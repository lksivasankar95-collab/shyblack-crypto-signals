package com.shyblack.cryptosignals.market;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Subscribes to Binance {@code !ticker@arr} (all-symbol 24h ticker array) and
 * writes accepted USDT pairs into an in-memory store. Listeners are notified
 * only for symbols whose quotes actually changed.
 */
public class BinanceArrayTickerStreamClient implements AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(BinanceArrayTickerStreamClient.class);
	private static final long MAX_BACKOFF_MS = 30_000;

	private final String label;
	private final URI uri;
	private final MarketTickerStore store;
	private final UsdtSymbolDirectory directory;
	private final ScheduledExecutorService scheduler;
	private final AtomicBoolean running = new AtomicBoolean(true);
	private final AtomicInteger attempt = new AtomicInteger(0);
	private volatile WebSocketClient client;

	public BinanceArrayTickerStreamClient(
			String label,
			String streamUrl,
			MarketTickerStore store,
			UsdtSymbolDirectory directory
	) {
		this.label = label;
		this.uri = URI.create(streamUrl);
		this.store = store;
		this.directory = directory;
		this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread thread = new Thread(r, label + "-ws-reconnect");
			thread.setDaemon(true);
			return thread;
		});
	}

	public void start() {
		connect();
	}

	private synchronized void connect() {
		if (!running.get()) {
			return;
		}
		closeQuietly();
		log.info("Connecting to Binance {} !ticker@arr stream {}", label, uri);
		WebSocketClient next = new WebSocketClient(uri) {
			@Override
			public void onOpen(ServerHandshake handshake) {
				attempt.set(0);
				log.info("Binance {} ticker array WebSocket connected", label);
			}

			@Override
			public void onMessage(String message) {
				try {
					List<MarketTicker> tickers = BinanceTickerParser.parseTickerArray(
							message,
							directory::contains,
							directory::name
					);
					store.upsertAll(tickers);
				} catch (Exception ex) {
					log.debug("Ignoring unparseable Binance {} payload: {}", label, ex.getMessage());
				}
			}

			@Override
			public void onClose(int code, String reason, boolean remote) {
				log.warn("Binance {} WebSocket closed code={} reason={}", label, code, reason);
				scheduleReconnect();
			}

			@Override
			public void onError(Exception ex) {
				log.warn("Binance {} WebSocket error: {}", label, ex.getMessage());
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
		log.info("Reconnecting to Binance {} in {} ms (attempt {})", label, delay, n);
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

	@Override
	public void close() {
		running.set(false);
		scheduler.shutdownNow();
		closeQuietly();
	}
}
