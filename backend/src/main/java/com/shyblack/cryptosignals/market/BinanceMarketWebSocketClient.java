package com.shyblack.cryptosignals.market;

import com.shyblack.cryptosignals.config.MarketProperties;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;

@Component
@ConditionalOnProperty(name = "app.markets.websocket-enabled", havingValue = "true", matchIfMissing = true)
public class BinanceMarketWebSocketClient implements AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(BinanceMarketWebSocketClient.class);
	private static final long MAX_BACKOFF_MS = 30_000;

	private final MarketProperties properties;
	private final MarketTickerStore store;
	private final BinanceRestClient restClient;
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread thread = new Thread(r, "binance-ws-reconnect");
		thread.setDaemon(true);
		return thread;
	});
	private final AtomicBoolean running = new AtomicBoolean(true);
	private final AtomicInteger attempt = new AtomicInteger(0);
	private volatile WebSocketClient client;

	public BinanceMarketWebSocketClient(
			MarketProperties properties,
			MarketTickerStore store,
			BinanceRestClient restClient
	) {
		this.properties = properties;
		this.store = store;
		this.restClient = restClient;
		connect();
	}

	private synchronized void connect() {
		if (!running.get()) {
			return;
		}
		closeQuietly();
		URI uri = URI.create(properties.streamUrl());
		log.info("Connecting to Binance combined ticker stream {}", uri);
		WebSocketClient next = new WebSocketClient(uri) {
			@Override
			public void onOpen(ServerHandshake handshake) {
				attempt.set(0);
				log.info("Binance market WebSocket connected");
			}

			@Override
			public void onMessage(String message) {
				try {
					String symbolGuess = "";
					if (message.contains("\"s\":\"")) {
						int start = message.indexOf("\"s\":\"") + 5;
						int end = message.indexOf('"', start);
						if (end > start) {
							symbolGuess = message.substring(start, end);
						}
					}
					store.upsert(BinanceTickerParser.parseCombined(message, restClient.displayName(symbolGuess)));
				} catch (Exception ex) {
					log.debug("Ignoring unparseable Binance payload: {}", ex.getMessage());
				}
			}

			@Override
			public void onClose(int code, String reason, boolean remote) {
				log.warn("Binance market WebSocket closed code={} reason={}", code, reason);
				scheduleReconnect();
			}

			@Override
			public void onError(Exception ex) {
				log.warn("Binance market WebSocket error: {}", ex.getMessage());
			}
		};
		client = next;
		next.setConnectionLostTimeout(30);
		next.connect();
	}

	private void scheduleReconnect() {
		if (!running.get()) {
			return;
		}
		int n = attempt.incrementAndGet();
		long delay = Math.min(MAX_BACKOFF_MS, Duration.ofSeconds(1).multipliedBy(1L << Math.min(n, 5)).toMillis());
		log.info("Reconnecting to Binance in {} ms (attempt {})", delay, n);
		scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
	}

	private void closeQuietly() {
		WebSocketClient current = client;
		if (current != null) {
			try {
				current.close();
			} catch (Exception ignored) {
				// last known tickers stay in memory
			}
		}
	}

	@PreDestroy
	@Override
	public void close() {
		running.set(false);
		scheduler.shutdownNow();
		closeQuietly();
	}
}
