package com.shyblack.cryptosignals.exchange.binance;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A loopback HTTP server that stands in for the Binance REST API in adapter tests.
 *
 * <p><b>Why this exists.</b> Phase 8 reported that no adapter {@code placeOrder}
 * path had a test. Asserting only against a mock adapter would verify the mock, not
 * the request the exchange would actually receive. This server records the exact
 * method, path, headers and query string, and returns a scripted response — so the
 * adapter's URL, parameter set, HMAC signature and JSON parsing are all exercised
 * for real.
 *
 * <p><b>No network egress.</b> It binds to {@code 127.0.0.1} on an ephemeral port
 * and is only ever addressed by loopback. No request can reach Binance, and no
 * production credential is used: tests supply their own throwaway key material
 * through {@link #encrypt}.
 *
 * <p>Not a Spring bean. Each test creates one, uses it, and closes it.
 */
public final class FakeExchangeHttp implements AutoCloseable {

	/** One recorded inbound request, exactly as it arrived. */
	public record Recorded(
			String method,
			String path,
			Map<String, String> query,
			Map<String, String> headers,
			String rawQuery) {
	}

	/** Scripted response for a path. */
	private record Scripted(int status, String body, long delayMillis) {
	}

	private final HttpServer server;
	private final List<Recorded> requests = new CopyOnWriteArrayList<>();
	private final Map<String, Scripted> scripted = new LinkedHashMap<>();

	public FakeExchangeHttp() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", this::handle);
		server.setExecutor(null);
		server.start();
	}

	/** Base URL to hand the adapter, e.g. {@code http://127.0.0.1:54321}. */
	public String baseUrl() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	/**
	 * Scripts a 200 JSON response for a path.
	 *
	 * <p>Later scripts for the same path replace earlier ones, so a test can script a
	 * response, run it, then script the next call.
	 */
	public FakeExchangeHttp on(String path, String json) {
		return on(path, 200, json, 0L);
	}

	/** Scripts a response with an explicit status, for rejection and error paths. */
	public FakeExchangeHttp on(String path, int status, String json) {
		return on(path, status, json, 0L);
	}

	/**
	 * Scripts a response that stalls before replying.
	 *
	 * <p>Used to produce a genuine transport timeout, which is the ambiguous
	 * outcome that must never trigger an automatic resubmission.
	 */
	public FakeExchangeHttp onSlow(String path, long delayMillis) {
		return on(path, 200, "{}", delayMillis);
	}

	private FakeExchangeHttp on(String path, int status, String json, long delayMillis) {
		scripted.put(path, new Scripted(status, json, delayMillis));
		return this;
	}

	/** Everything the server received, in order. */
	public List<Recorded> requests() {
		return List.copyOf(requests);
	}

	public Recorded lastRequest() {
		if (requests.isEmpty()) {
			throw new AssertionError("the exchange server received no request");
		}
		return requests.get(requests.size() - 1);
	}

	public int requestCount() {
		return requests.size();
	}

	/** The single parameter value for a key, failing loudly when absent. */
	public String param(String key) {
		String value = lastRequest().query().get(key);
		if (value == null) {
			throw new AssertionError("expected query parameter '" + key + "' in " + lastRequest().rawQuery());
		}
		return value;
	}

	/** The single parameter value, or null when the key was absent. */
	public String paramOrNull(String key) {
		return lastRequest().query().get(key);
	}

	public String header(String name) {
		return lastRequest().headers().get(name.toLowerCase());
	}

	private void handle(HttpExchange exchange) throws IOException {
		try {
			String path = exchange.getRequestURI().getPath();
			Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());

			Map<String, String> headers = new LinkedHashMap<>();
			exchange.getRequestHeaders().forEach((k, v) -> {
				if (!v.isEmpty()) {
					headers.put(k.toLowerCase(), v.get(0));
				}
			});
			requests.add(new Recorded(
					exchange.getRequestMethod(), path, query, headers,
					exchange.getRequestURI().getRawQuery()));

			Scripted response = scripted.get(path);
			if (response == null) {
				respond(exchange, 404, "{\"code\":-1121,\"msg\":\"Invalid symbol.\"}");
				return;
			}
			if (response.delayMillis() > 0) {
				try {
					Thread.sleep(response.delayMillis());
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}
			respond(exchange, response.status(), response.body());
		} finally {
			exchange.close();
		}
	}

	private static void respond(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

	private static Map<String, String> parseQuery(String rawQuery) {
		Map<String, String> parsed = new LinkedHashMap<>();
		if (rawQuery == null || rawQuery.isBlank()) {
			return parsed;
		}
		for (String pair : rawQuery.split("&")) {
			if (pair.isEmpty()) {
				continue;
			}
			int eq = pair.indexOf('=');
			String key = eq < 0 ? pair : pair.substring(0, eq);
			String value = eq < 0 ? "" : pair.substring(eq + 1);
			parsed.put(decode(key), decode(value));
		}
		return parsed;
	}

	private static String decode(String value) {
		return URLDecoder.decode(value, StandardCharsets.UTF_8);
	}

	@Override
	public void close() {
		server.stop(0);
	}

	/** Convenience for asserting on a URI the adapter would build. */
	static String queryOf(URI uri) {
		return uri.getRawQuery();
	}

	/** All recorded request paths, for assertions about which endpoint was called. */
	public List<String> paths() {
		List<String> paths = new ArrayList<>();
		for (Recorded recorded : requests) {
			paths.add(recorded.path());
		}
		return paths;
	}
}