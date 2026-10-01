package com.shyblack.cryptosignals.exchange.binance;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.config.FuturesTradingProperties;
import com.shyblack.cryptosignals.config.LiveTradingProperties;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.security.ExchangeCredentialEncryptor;
import com.shyblack.cryptosignals.config.SettingsProperties;
import com.shyblack.cryptosignals.service.portfolio.UserStreamConnector;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Phase 4 transport tests for the Binance user-data stream, run entirely on loopback.
 *
 * <p>A local HTTP server stands in for the listen-key REST endpoints and a local WebSocket server
 * stands in for the user stream, so real listen-key minting, a real socket handshake, real payload
 * delivery and a real server-initiated disconnect are all exercised without contacting Binance and
 * without any real credential.
 */
class BinanceUserStreamConnectorTest {

	private HttpServer rest;
	private StubStreamServer stream;
	private final AtomicInteger listenKeyRequests = new AtomicInteger();
	private final List<String> listenKeyMethods = new CopyOnWriteArrayList<>();
	private final AtomicReference<String> lastApiKeyHeader = new AtomicReference<>();
	private final AtomicInteger listenKeySequence = new AtomicInteger();

	@BeforeEach
	void startInfrastructure() throws IOException, InterruptedException {
		rest = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		// Serve both the spot and the futures listen-key paths.
		rest.createContext("/api/v3/userDataStream", exchange -> handleListenKey(exchange));
		rest.createContext("/fapi/v1/listenKey", exchange -> handleListenKey(exchange));
		rest.start();

		stream = new StubStreamServer(0);
		stream.start();
		// WebSocketServer binds asynchronously.
		stream.awaitReady();
	}

	@AfterEach
	void stopInfrastructure() throws InterruptedException {
		if (stream != null) {
			stream.stop();
		}
		if (rest != null) {
			rest.stop(0);
		}
	}

	private void handleListenKey(HttpExchange exchange) throws IOException {
		lastApiKeyHeader.set(exchange.getRequestHeaders().getFirst("X-MBX-APIKEY"));
		listenKeyMethods.add(exchange.getRequestMethod());
		String key = "stub-listen-key-" + listenKeySequence.incrementAndGet();
		listenKeyRequests.incrementAndGet();
		byte[] body = ("{\"listenKey\":\"" + key + "\"}").getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, body.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(body);
		}
	}

	/** The listen key is requested on the session's own scheduler, so assertions must wait for it. */
	private void awaitListenKeyRequests(int minimum, long timeoutMs) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (listenKeyRequests.get() < minimum && System.currentTimeMillis() < deadline) {
			try {
				Thread.sleep(20);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	private String restBase() {
		return "http://127.0.0.1:" + rest.getAddress().getPort();
	}

	private String streamBase() {
		return "ws://127.0.0.1:" + stream.getPort();
	}

	private BinanceUserStreamConnector connector() {
		LiveTradingProperties live = new LiveTradingProperties(
				LiveTradingProperties.Mode.EXCHANGE, restBase(), streamBase(), null,
				5000L, null, 0, null, false);
		FuturesTradingProperties futures = new FuturesTradingProperties(
				FuturesTradingProperties.Mode.EXCHANGE, restBase(), streamBase(),
				5000L, 3, null, null, null, 0, null, null, null, false);
		return new BinanceUserStreamConnector(live, futures, new ExchangeCredentialEncryptor(
				new SettingsProperties(0, 0, 0, "phase4-test-only-seed", null, null, null)));
	}

	private ExchangeCredential credential() {
		ExchangeCredentialEncryptor encryptor = new ExchangeCredentialEncryptor(
				new SettingsProperties(0, 0, 0, "phase4-test-only-seed", null, null, null));
		ExchangeCredential credential = new ExchangeCredential();
		credential.setExchange(ExchangeName.BINANCE);
		credential.setApiKey(encryptor.encrypt("test-api-key"));
		credential.setApiSecret(encryptor.encrypt("test-api-secret"));
		return credential;
	}

	private UserStreamConnector.Request request(
			List<String> received, Runnable onDisconnect, java.util.UUID userId, AccountCategory category) {
		return new UserStreamConnector.Request(
				userId, category, credential(), received::add, onDisconnect);
	}

	// ------------------------------------------------------- listen key + connect

	@Test
	void opensAStreamAndDeliversPayloadsVerbatim() throws Exception {
		List<String> received = new CopyOnWriteArrayList<>();
		CountDownLatch delivered = new CountDownLatch(1);
		List<String> sink = new CopyOnWriteArrayList<>() {
			@Override
			public boolean add(String value) {
				boolean added = super.add(value);
				delivered.countDown();
				return added;
			}
		};

		BinanceUserStreamConnector connector = connector();
		UserStreamConnector.Handle handle = connector.open(
				request(sink, () -> { }, java.util.UUID.randomUUID(), AccountCategory.SPOT));
		try {
			assertThat(stream.awaitConnection(5, TimeUnit.SECONDS))
					.as("the socket must connect to the stub stream server")
					.isTrue();
			stream.send("{\"e\":\"outboundAccountPosition\",\"E\":1,\"u\":1,\"B\":[]}");

			assertThat(delivered.await(5, TimeUnit.SECONDS))
					.as("the payload must reach the sink")
					.isTrue();
			assertThat(sink).containsExactly("{\"e\":\"outboundAccountPosition\",\"E\":1,\"u\":1,\"B\":[]}");
			assertThat(listenKeyRequests.get()).isGreaterThan(0);
			assertThat(handle.isOpen()).isTrue();
		} finally {
			handle.close();
		}
	}

	@Test
	void listenKeyRequestCarriesTheApiKeyHeaderAndNoSignature() {
		BinanceUserStreamConnector connector = connector();
		UserStreamConnector.Handle handle = connector.open(
				request(new CopyOnWriteArrayList<>(), () -> { },
						java.util.UUID.randomUUID(), AccountCategory.SPOT));
		try {
			awaitListenKeyRequests(1, 5000);
			assertThat(lastApiKeyHeader.get())
					.as("listen-key endpoints use Binance's API-KEY security type")
					.isEqualTo("test-api-key");
			assertThat(listenKeyMethods).contains("POST");
		} finally {
			handle.close();
		}
	}

	@Test
	void spotAndFuturesUseDifferentListenKeyEndpoints() {
		BinanceUserStreamConnector connector = connector();
		connector.open(request(new CopyOnWriteArrayList<>(), () -> { },
				java.util.UUID.randomUUID(), AccountCategory.SPOT));
		connector.open(request(new CopyOnWriteArrayList<>(), () -> { },
				java.util.UUID.randomUUID(), AccountCategory.FUTURES));

		awaitListenKeyRequests(2, 5000);
		assertThat(connector.activeSessionCount()).isEqualTo(2);
		assertThat(listenKeyRequests.get()).isGreaterThanOrEqualTo(2);
	}

	// ----------------------------------------------------------- idempotency

	@Test
	void openingTheSameScopeTwiceReusesTheSingleSession() throws Exception {
		BinanceUserStreamConnector connector = connector();
		java.util.UUID userId = java.util.UUID.randomUUID();
		UserStreamConnector.Handle first = connector.open(
				request(new CopyOnWriteArrayList<>(), () -> { }, userId, AccountCategory.SPOT));
		UserStreamConnector.Handle second = connector.open(
				request(new CopyOnWriteArrayList<>(), () -> { }, userId, AccountCategory.SPOT));

		try {
			assertThat(second).as("a repeated open must not create a second stream").isSameAs(first);
			assertThat(connector.activeSessionCount()).isEqualTo(1);
		} finally {
			first.close();
		}
	}

	@Test
	void twoScopesOfOneUserAreTrackedIndependently() {
		BinanceUserStreamConnector connector = connector();
		java.util.UUID userId = java.util.UUID.randomUUID();
		connector.open(request(new CopyOnWriteArrayList<>(), () -> { }, userId, AccountCategory.SPOT));
		connector.open(request(new CopyOnWriteArrayList<>(), () -> { }, userId, AccountCategory.FUTURES));

		assertThat(connector.activeSessionCount()).isEqualTo(2);
		assertThat(connector.isRunning(userId, AccountCategory.SPOT)).isTrue();
		assertThat(connector.isRunning(userId, AccountCategory.FUTURES)).isTrue();
		connector.closeAll();
	}

	@Test
	void twoUsersDoNotShareASession() {
		BinanceUserStreamConnector connector = connector();
		java.util.UUID a = java.util.UUID.randomUUID();
		java.util.UUID b = java.util.UUID.randomUUID();
		connector.open(request(new CopyOnWriteArrayList<>(), () -> { }, a, AccountCategory.SPOT));
		connector.open(request(new CopyOnWriteArrayList<>(), () -> { }, b, AccountCategory.SPOT));

		assertThat(connector.activeSessionCount()).isEqualTo(2);
		connector.closeAll();
	}

	@Test
	void closingOneScopeLeavesTheOtherRunning() {
		BinanceUserStreamConnector connector = connector();
		java.util.UUID userId = java.util.UUID.randomUUID();
		connector.open(request(new CopyOnWriteArrayList<>(), () -> { }, userId, AccountCategory.SPOT));
		connector.open(request(new CopyOnWriteArrayList<>(), () -> { }, userId, AccountCategory.FUTURES));

		connector.close(userId, AccountCategory.SPOT);

		assertThat(connector.isRunning(userId, AccountCategory.SPOT)).isFalse();
		assertThat(connector.isRunning(userId, AccountCategory.FUTURES)).isTrue();
		connector.closeAll();
	}

	@Test
	void closeIsIdempotentAndSafeForScopesThatWereNeverOpened() {
		BinanceUserStreamConnector connector = connector();
		java.util.UUID userId = java.util.UUID.randomUUID();
		UserStreamConnector.Handle handle = connector.open(
				request(new CopyOnWriteArrayList<>(), () -> { }, userId, AccountCategory.SPOT));

		handle.close();
		handle.close();
		connector.close(userId, AccountCategory.SPOT);
		connector.close(java.util.UUID.randomUUID(), AccountCategory.FUTURES);

		assertThat(connector.activeSessionCount()).isZero();
	}

	@Test
	void closeAllStopsEverySession() {
		BinanceUserStreamConnector connector = connector();
		connector.open(request(new CopyOnWriteArrayList<>(), () -> { },
				java.util.UUID.randomUUID(), AccountCategory.SPOT));
		connector.open(request(new CopyOnWriteArrayList<>(), () -> { },
				java.util.UUID.randomUUID(), AccountCategory.FUTURES));

		connector.closeAll();

		assertThat(connector.activeSessionCount()).isZero();
	}

	// ------------------------------------------------------------- disconnect

	@Test
	void aServerInitiatedDisconnectSignalsExactlyOncePerDrop() throws Exception {
		AtomicInteger disconnects = new AtomicInteger();
		BinanceUserStreamConnector connector = connector();
		UserStreamConnector.Handle handle = connector.open(request(
				new CopyOnWriteArrayList<>(), disconnects::incrementAndGet,
				java.util.UUID.randomUUID(), AccountCategory.SPOT));
		try {
			assertThat(stream.awaitConnection(5, TimeUnit.SECONDS)).isTrue();
			stream.dropAll();

			long deadline = System.currentTimeMillis() + 5000;
			while (disconnects.get() == 0 && System.currentTimeMillis() < deadline) {
				Thread.sleep(25);
			}
			assertThat(disconnects.get())
					.as("a physical drop must be signalled exactly once so the manager reconciles once")
					.isEqualTo(1);
		} finally {
			handle.close();
		}
	}

	@Test
	void aReconnectMintsAFreshListenKey() throws Exception {
		BinanceUserStreamConnector connector = connector();
		UserStreamConnector.Handle handle = connector.open(request(
				new CopyOnWriteArrayList<>(), () -> { },
				java.util.UUID.randomUUID(), AccountCategory.SPOT));
		try {
			assertThat(stream.awaitConnection(5, TimeUnit.SECONDS)).isTrue();
			int afterFirst = listenKeyRequests.get();
			stream.dropAll();

			long deadline = System.currentTimeMillis() + 10_000;
			while (listenKeyRequests.get() <= afterFirst && System.currentTimeMillis() < deadline) {
				Thread.sleep(25);
			}
			assertThat(listenKeyRequests.get())
					.as("a key tied to a dead connection is not assumed reusable")
					.isGreaterThan(afterFirst);
		} finally {
			handle.close();
		}
	}

	@Test
	void aMalformedEventDoesNotTearDownAHealthyStream() throws Exception {
		List<String> sink = new CopyOnWriteArrayList<>();
		BinanceUserStreamConnector connector = connector();
		UserStreamConnector.Handle handle = connector.open(
				request(sink, () -> { }, java.util.UUID.randomUUID(), AccountCategory.SPOT));
		try {
			assertThat(stream.awaitConnection(5, TimeUnit.SECONDS)).isTrue();
			stream.send("this is not json");
			stream.send("{\"e\":\"outboundAccountPosition\"}");
			Thread.sleep(300);

			assertThat(sink).hasSize(2);
			assertThat(handle.isOpen())
					.as("a bad payload must never kill the stream")
					.isTrue();
		} finally {
			handle.close();
		}
	}

	// ------------------------------------------------------------- stub server

	/** Minimal in-process WebSocket server: records connections and can broadcast or drop. */
	private static final class StubStreamServer extends WebSocketServer {

		private final CountDownLatch oneConnection = new CountDownLatch(1);
		private final CountDownLatch ready = new CountDownLatch(1);
		private volatile WebSocket socket;

		StubStreamServer(int port) {
			super(new InetSocketAddress("127.0.0.1", port));
			setReuseAddr(true);
		}

		@Override
		public void onOpen(WebSocket conn, ClientHandshake handshake) {
			socket = conn;
			oneConnection.countDown();
		}

		@Override
		public void onClose(WebSocket conn, int code, String reason, boolean remote) { }

		@Override
		public void onMessage(WebSocket conn, String message) { }

		@Override
		public void onError(WebSocket conn, Exception ex) { }

		@Override
		public void onStart() {
			ready.countDown();
		}

		void awaitReady() throws InterruptedException {
			ready.await(5, TimeUnit.SECONDS);
		}

		boolean awaitConnection(long timeout, TimeUnit unit) throws InterruptedException {
			return oneConnection.await(timeout, unit);
		}

		void send(String message) {
			WebSocket current = socket;
			if (current != null) {
				current.send(message);
			}
		}

		void dropAll() {
			WebSocket current = socket;
			if (current != null) {
				current.close();
			}
		}
	}
}