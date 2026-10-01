package com.shyblack.cryptosignals.exchange.binance;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shyblack.cryptosignals.config.FuturesTradingProperties;
import com.shyblack.cryptosignals.config.HttpClientFactory;
import com.shyblack.cryptosignals.config.LiveTradingProperties;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.security.ExchangeCredentialEncryptor;
import com.shyblack.cryptosignals.service.portfolio.UserStreamConnector;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

/**
 * Binance user-data WebSocket transport for one user's SPOT and FUTURES scopes.
 *
 * <p>Spot and Futures are genuinely different and are handled separately: different REST endpoints
 * to mint a listen key ({@code /api/v3/userDataStream} vs {@code /fapi/v1/listenKey}), different
 * event shapes, and different ordering keys. They are not interchangeable.
 *
 * <p>Listen-key endpoints use Binance's API-KEY security type: only the {@code X-MBX-APIKEY} header
 * is required, <em>no</em> HMAC signature is involved. Signing them would be wrong, so it is not
 * done here.
 *
 * <p>The user-data socket itself is authenticated by the listen key in its path, so no API key
 * header is sent on the WebSocket handshake.
 *
 * <p>Secrets are never logged: the listen key is treated as sensitive, so neither it nor the full
 * stream URI is ever included in a log line.
 */
@RequiredArgsConstructor
public class BinanceUserStreamConnector implements UserStreamConnector {

	private static final Logger log = LoggerFactory.getLogger(BinanceUserStreamConnector.class);

	/** Binance requires a keep-alive at least every 60 minutes; 30 leaves margin for jitter. */
	private static final Duration KEEPALIVE_INTERVAL = Duration.ofMinutes(30);

	/** Bounded exponential backoff, matching the existing ticker stream client convention. */
	private static final long MAX_BACKOFF_MS = 60_000;

	private final LiveTradingProperties liveProps;
	private final FuturesTradingProperties futuresProps;
	private final ExchangeCredentialEncryptor encryptor;
	private final RestClient rest = RestClient.builder()
			.requestFactory(HttpClientFactory.withDefaultTimeouts())
			.build();

	private final Map<String, Session> sessions = new ConcurrentHashMap<>();

	@Override
	public Handle open(Request request) {
		String key = sessionKey(request);
		// Idempotent per scope: a repeated start reuses the live session instead of duplicating it.
		Session existing = sessions.get(key);
		if (existing != null && existing.isRunning()) {
			log.debug("User-data stream already active for scope {}", key);
			return existing;
		}
		Session session = new Session(key, request);
		sessions.put(key, session);
		session.start();
		return session;
	}

	/** True when a stream is currently registered and running for the scope. */
	public boolean isRunning(UUID userId, AccountCategory category) {
		Session session = sessions.get(key(userId, category));
		return session != null && session.isRunning();
	}

	public void close(UUID userId, AccountCategory category) {
		Session session = sessions.remove(key(userId, category));
		if (session != null) {
			session.close();
		}
	}

	public void closeAll() {
		sessions.values().forEach(Session::close);
		sessions.clear();
	}

	public int activeSessionCount() {
		return sessions.size();
	}

	static String sessionKey(Request request) {
		return key(request.userId(), request.accountCategory());
	}

	private static String key(java.util.UUID userId, AccountCategory category) {
		return userId + ":" + category.name();
	}

	// ---------------------------------------------------------------- session

	private final class Session implements Handle {

		private final String label;
		private final Request request;
		private final AccountCategory category;
		private final AtomicBoolean running = new AtomicBoolean(true);
		private final AtomicInteger attempt = new AtomicInteger(0);
		private final ScheduledExecutorService scheduler;
		private volatile WebSocketClient client;
		private volatile String listenKey;

		Session(String label, Request request) {
			this.label = label;
			this.request = request;
			this.category = request.accountCategory();
			this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
				Thread thread = new Thread(r, "user-stream-" + label);
				thread.setDaemon(true);
				return thread;
			});
		}

		boolean isRunning() {
			return running.get();
		}

		void start() {
			scheduler.schedule(this::connect, 0, TimeUnit.MILLISECONDS);
		}

		private synchronized void connect() {
			if (!running.get()) {
				return;
			}
			closeSocket();
			try {
				// A fresh listen key on every (re)connect: a key tied to a dead connection is not
				// guaranteed to be reusable.
				listenKey = obtainListenKey();
			} catch (Exception ex) {
				log.warn("User-data stream {} could not obtain a listen key: {}", label, ex.getMessage());
				scheduleReconnect();
				return;
			}

			URI uri = URI.create(streamBaseUrl() + "/" + listenKey);
			WebSocketClient next = new WebSocketClient(uri) {
				@Override
				public void onOpen(ServerHandshake handshake) {
					attempt.set(0);
					// Scope only: the URI (which embeds the listen key) is never logged.
					log.info("Binance user-data stream connected scope={}", label);
				}

				@Override
				public void onMessage(String message) {
					// Delivered verbatim; parsing and validation belong to the processor.
					try {
						request.payloadSink().accept(message);
					} catch (RuntimeException ex) {
						// A single bad event must never tear down a healthy stream.
						log.warn("User-data event rejected scope={} reason={}", label, ex.getMessage());
					}
				}

				@Override
				public void onClose(int code, String reason, boolean remote) {
					if (!running.get()) {
						return;
					}
					log.warn("Binance user-data stream disconnected scope={} code={}", label, code);
					notifyDropped();
					scheduleReconnect();
				}

				@Override
				public void onError(Exception ex) {
					log.warn("Binance user-data stream error scope={} reason={}", label, ex.getMessage());
				}
			};
			client = next;
			next.setConnectionLostTimeout(30);
			next.connect();
			scheduler.schedule(this::keepalive, KEEPALIVE_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
		}

		/**
		 * Signals a drop exactly once per disconnect so the manager reconciles over REST. Events
		 * emitted while the socket was down cannot be assumed irrelevant.
		 */
		private final AtomicBoolean dropSignalled = new AtomicBoolean(false);

		private void notifyDropped() {
			if (dropSignalled.compareAndSet(false, true)) {
				try {
					request.onDisconnected().run();
				} catch (RuntimeException ex) {
					log.warn("User-data drop handler failed scope={} reason={}", label, ex.getMessage());
				}
			}
		}

		private void scheduleReconnect() {
			if (!running.get()) {
				return;
			}
			dropSignalled.set(false);
			int n = attempt.incrementAndGet();
			long delay = Math.min(MAX_BACKOFF_MS,
					Duration.ofSeconds(1).multipliedBy(1L << Math.min(n, 5)).toMillis());
			log.info("Reconnecting Binance user-data stream scope={} in {} ms (attempt {})",
					label, delay, n);
			scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
		}

		private void keepalive() {
			if (!running.get()) {
				return;
			}
			try {
				listenKeyRequest(org.springframework.http.HttpMethod.PUT, listenKey);
			} catch (Exception ex) {
				// An expired key is recovered by the next reconnect, which mints a fresh one.
				log.warn("User-data keep-alive failed scope={} reason={}", label, ex.getMessage());
			}
			scheduler.schedule(this::keepalive, KEEPALIVE_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
		}

		private String obtainListenKey() {
			String body = listenKeyRequest(org.springframework.http.HttpMethod.POST, null);
			JsonObject json = JsonParser.parseString(body).getAsJsonObject();
			String key = json.has("listenKey") ? json.get("listenKey").getAsString() : null;
			if (key == null || key.isBlank()) {
				throw new IllegalStateException("Exchange returned no listen key");
			}
			return key;
		}

		private String listenKeyRequest(org.springframework.http.HttpMethod method, String existingKey) {
			String path = category == AccountCategory.FUTURES ? "/fapi/v1/listenKey" : "/api/v3/userDataStream";
			String base = category == AccountCategory.FUTURES
					? futuresProps.restBaseUrl()
					: liveProps.spotRestBaseUrl();
			String url = base + path
					+ (existingKey == null ? "" : "?listenKey=" + java.net.URLEncoder
							.encode(existingKey, java.nio.charset.StandardCharsets.UTF_8));

			HttpHeaders headers = new HttpHeaders();
			headers.set("X-MBX-APIKEY", encryptor.decrypt(request.credential().getApiKey()));
			try {
				return rest.method(method).uri(url).headers(h -> h.addAll(headers))
						.retrieve().body(String.class);
			} catch (HttpStatusCodeException http) {
				throw new IllegalStateException(
						"Exchange rejected the listen-key request with HTTP " + http.getStatusCode(), http);
			}
		}

		private String streamBaseUrl() {
			return category == AccountCategory.FUTURES
					? futuresProps.streamBaseUrl()
					: liveProps.spotStreamBaseUrl();
		}

		private void closeSocket() {
			WebSocketClient current = client;
			if (current != null) {
				try {
					current.close();
				} catch (Exception ignored) {
					// nothing to do; the socket is being replaced
				}
			}
		}

		@Override
		public boolean isOpen() {
			WebSocketClient current = client;
			return running.get() && current != null && current.isOpen();
		}

		@Override
		public void close() {
			running.set(false);
			scheduler.shutdownNow();
			closeSocket();
		}
	}
}