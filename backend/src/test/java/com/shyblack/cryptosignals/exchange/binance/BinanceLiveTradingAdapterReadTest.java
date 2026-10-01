package com.shyblack.cryptosignals.exchange.binance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shyblack.cryptosignals.config.LiveTradingProperties;
import com.shyblack.cryptosignals.config.SettingsProperties;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.exchange.ExchangeAccountSnapshot;
import com.shyblack.cryptosignals.exchange.ExchangeAdapterException;
import com.shyblack.cryptosignals.exchange.ExchangeBalances;
import com.shyblack.cryptosignals.security.ExchangeCredentialEncryptor;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Deterministic tests of the real Binance spot adapter's read path.
 *
 * <p>A local {@link HttpServer} on an ephemeral loopback port stands in for Binance, so the genuine
 * HMAC signing, URL building, JSON parsing and error mapping all execute while nothing leaves the
 * machine. No real credential is used and no production Binance endpoint is contacted.
 */
class BinanceLiveTradingAdapterReadTest {

	private HttpServer server;
	private final AtomicReference<String> lastApiKeyHeader = new AtomicReference<>();
	private final AtomicReference<String> lastQuery = new AtomicReference<>();

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.start();
	}

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	private String baseUrl() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	private void respond(String path, int status, String body) {
		server.createContext(path, exchange -> {
			lastApiKeyHeader.set(exchange.getRequestHeaders().getFirst("X-MBX-APIKEY"));
			lastQuery.set(exchange.getRequestURI().getQuery());
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, bytes.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		});
	}

	private BinanceLiveTradingAdapter adapter() {
		LiveTradingProperties props = new LiveTradingProperties(
				LiveTradingProperties.Mode.EXCHANGE, baseUrl(), null, null,
				5000L, new BigDecimal("200"), 3, new BigDecimal("5"), false);
		SettingsProperties settings = new SettingsProperties(
				0, 0, 0, "phase3-test-only-encryption-seed", null, null, null);
		return new BinanceLiveTradingAdapter(props, new ExchangeCredentialEncryptor(settings));
	}

	private ExchangeCredential credential() {
		ExchangeCredentialEncryptor encryptor = new ExchangeCredentialEncryptor(
				new SettingsProperties(0, 0, 0, "phase3-test-only-encryption-seed", null, null, null));
		ExchangeCredential credential = new ExchangeCredential();
		credential.setExchange(ExchangeName.BINANCE);
		credential.setApiKey(encryptor.encrypt("test-api-key"));
		credential.setApiSecret(encryptor.encrypt("test-api-secret"));
		return credential;
	}

	// ------------------------------------------------- complete balance set

	@Test
	void readsEveryAssetWithFreeAndLockedAmounts() {
		respond("/api/v3/account", 200, """
				{"canTrade":true,"balances":[
				  {"asset":"USDT","free":"100.50000000","locked":"25.25000000"},
				  {"asset":"BTC","free":"0.75000000","locked":"0.00000000"},
				  {"asset":"ETH","free":"0.00000000","locked":"3.00000000"}
				]}""");

		ExchangeBalances balances = adapter().getBalances(credential());

		assertThat(balances.assets()).hasSize(3);
		assertThat(balances.canTrade()).isTrue();
		assertThat(balances.free("USDT")).isEqualByComparingTo("100.5");
		assertThat(balances.locked("USDT")).isEqualByComparingTo("25.25");
		assertThat(balances.total("USDT")).isEqualByComparingTo("125.75");
		assertThat(balances.free("BTC")).isEqualByComparingTo("0.75");
		assertThat(balances.locked("BTC")).isEqualByComparingTo("0");
		assertThat(balances.free("ETH")).isEqualByComparingTo("0");
		assertThat(balances.locked("ETH")).isEqualByComparingTo("3");
	}

	@Test
	void assetLookupIsCaseInsensitiveBecauseBinanceReportsUpperCase() {
		respond("/api/v3/account", 200,
				"{\"canTrade\":true,\"balances\":[{\"asset\":\"USDT\",\"free\":\"1\",\"locked\":\"0\"}]}");

		ExchangeBalances balances = adapter().getBalances(credential());

		assertThat(balances.free("usdt")).isEqualByComparingTo("1");
	}

	@Test
	void anExplicitZeroFromTheExchangeIsPreservedAsZero() {
		respond("/api/v3/account", 200,
				"{\"canTrade\":true,\"balances\":[{\"asset\":\"USDT\",\"free\":\"0.00000000\",\"locked\":\"0.00000000\"}]}");

		ExchangeBalances balances = adapter().getBalances(credential());

		assertThat(balances.find("USDT")).isPresent();
		assertThat(balances.free("USDT")).isEqualByComparingTo("0");
		assertThat(balances.total("USDT")).isEqualByComparingTo("0");
	}

	@Test
	void anAssetTheExchangeNeverReportedIsNullRatherThanZero() {
		respond("/api/v3/account", 200,
				"{\"canTrade\":true,\"balances\":[{\"asset\":\"BTC\",\"free\":\"1\",\"locked\":\"0\"}]}");

		ExchangeBalances balances = adapter().getBalances(credential());

		assertThat(balances.find("USDT"))
				.as("missing balance is not the same as a zero balance")
				.isEmpty();
		assertThat(balances.free("USDT")).isNull();
		assertThat(balances.locked("USDT")).isNull();
		assertThat(balances.total("USDT")).isNull();
	}

	@Test
	void narrowAccountSnapshotReportsNullWhenTheQuoteAssetIsAbsent() {
		respond("/api/v3/account", 200,
				"{\"canTrade\":true,\"balances\":[{\"asset\":\"BTC\",\"free\":\"1\",\"locked\":\"0\"}]}");

		ExchangeAccountSnapshot snapshot = adapter().getAccountBalance(credential());

		assertThat(snapshot.quoteCurrency()).isEqualTo("USDT");
		assertThat(snapshot.availableBalance())
				.as("previously this silently reported ZERO for an absent quote asset")
				.isNull();
		assertThat(snapshot.totalBalance()).isNull();
	}

	@Test
	void narrowAccountSnapshotStillReportsRealQuoteBalances() {
		respond("/api/v3/account", 200,
				"{\"canTrade\":true,\"balances\":[{\"asset\":\"USDT\",\"free\":\"40\",\"locked\":\"2\"}]}");

		ExchangeAccountSnapshot snapshot = adapter().getAccountBalance(credential());

		assertThat(snapshot.availableBalance()).isEqualByComparingTo("40");
		assertThat(snapshot.totalBalance()).isEqualByComparingTo("42");
	}

	// --------------------------------------------------------------- signing

	@Test
	void requestIsSignedAndCarriesTheApiKeyHeader() {
		respond("/api/v3/account", 200,
				"{\"canTrade\":true,\"balances\":[{\"asset\":\"USDT\",\"free\":\"1\",\"locked\":\"0\"}]}");

		adapter().getBalances(credential());

		assertThat(lastApiKeyHeader.get()).isEqualTo("test-api-key");
		assertThat(lastQuery.get())
				.contains("signature=")
				.contains("timestamp=")
				.contains("recvWindow=5000");
		assertThat(lastQuery.get())
				.as("the plaintext secret must never appear in the request")
				.doesNotContain("test-api-secret");
	}

	@Test
	void credentialValidationPerformsAReadOnlyAccountCall() {
		respond("/api/v3/account", 200,
				"{\"canTrade\":true,\"balances\":[{\"asset\":\"USDT\",\"free\":\"5\",\"locked\":\"0\"}]}");

		ExchangeAccountSnapshot snapshot = adapter().validateCredentials(credential());

		assertThat(snapshot.availableBalance()).isEqualByComparingTo("5");
	}

	// ------------------------------------------------------- failure mapping

	@Test
	void invalidApiKeySurfacesTheBinanceErrorCode() {
		respond("/api/v3/account", 401, "{\"code\":-2015,\"msg\":\"Invalid API-key, IP, or permissions\"}");

		assertThatThrownBy(() -> adapter().getBalances(credential()))
				.isInstanceOf(ExchangeAdapterException.class)
				.satisfies(ex -> {
					ExchangeAdapterException e = (ExchangeAdapterException) ex;
					assertThat(e.exchangeCode()).isEqualTo(-2015);
					assertThat(e.httpStatus()).isEqualTo(401);
					assertThat(e.retryable()).isFalse();
				});
	}

	@Test
	void invalidSignatureSurfacesTheBinanceErrorCode() {
		respond("/api/v3/account", 401, "{\"code\":-1022,\"msg\":\"Signature for this request is not valid\"}");

		assertThatThrownBy(() -> adapter().getBalances(credential()))
				.isInstanceOf(ExchangeAdapterException.class)
				.satisfies(ex -> assertThat(((ExchangeAdapterException) ex).exchangeCode()).isEqualTo(-1022));
	}

	@Test
	void rateLimitIsReportedAsRetryable() {
		respond("/api/v3/account", 429, "{\"code\":-1003,\"msg\":\"Too much request weight used\"}");

		assertThatThrownBy(() -> adapter().getBalances(credential()))
				.isInstanceOf(ExchangeAdapterException.class)
				.satisfies(ex -> {
					ExchangeAdapterException e = (ExchangeAdapterException) ex;
					assertThat(e.httpStatus()).isEqualTo(429);
					assertThat(e.exchangeCode()).isEqualTo(-1003);
					assertThat(e.retryable()).isTrue();
				});
	}

	@Test
	void ipBanIsNotTreatedAsRetryable() {
		respond("/api/v3/account", 418, "{\"code\":-1003,\"msg\":\"IP banned\"}");

		assertThatThrownBy(() -> adapter().getBalances(credential()))
				.isInstanceOf(ExchangeAdapterException.class)
				.satisfies(ex -> {
					ExchangeAdapterException e = (ExchangeAdapterException) ex;
					assertThat(e.httpStatus()).isEqualTo(418);
					assertThat(e.retryable())
							.as("an IP ban needs operator action, not an immediate retry")
							.isFalse();
				});
	}

	@Test
	void serverErrorIsReportedAsRetryable() {
		respond("/api/v3/account", 503, "{\"code\":-1001,\"msg\":\"Internal error\"}");

		assertThatThrownBy(() -> adapter().getBalances(credential()))
				.isInstanceOf(ExchangeAdapterException.class)
				.satisfies(ex -> {
					ExchangeAdapterException e = (ExchangeAdapterException) ex;
					assertThat(e.httpStatus()).isEqualTo(503);
					assertThat(e.retryable()).isTrue();
				});
	}

	@Test
	void malformedBodyIsRejectedInsteadOfCoercedToZero() {
		respond("/api/v3/account", 200, "{\"canTrade\":true}");

		assertThatThrownBy(() -> adapter().getBalances(credential()))
				.isInstanceOf(ExchangeAdapterException.class)
				.hasMessageContaining("balances");
	}

	@Test
	void nonNumericBalanceIsRejectedInsteadOfCoercedToZero() {
		respond("/api/v3/account", 200,
				"{\"canTrade\":true,\"balances\":[{\"asset\":\"USDT\",\"free\":\"abc\",\"locked\":\"0\"}]}");

		assertThatThrownBy(() -> adapter().getBalances(credential()))
				.isInstanceOf(ExchangeAdapterException.class)
				.hasMessageContaining("USDT");
	}

	@Test
	void failureDiagnosticsNeverContainTheSecret() {
		respond("/api/v3/account", 401, "{\"code\":-2015,\"msg\":\"Invalid API-key, IP, or permissions\"}");

		assertThatThrownBy(() -> adapter().getBalances(credential()))
				.isInstanceOf(ExchangeAdapterException.class)
				.satisfies(ex -> {
					assertThat(ex.getMessage()).doesNotContain("test-api-secret");
					assertThat(ex.getMessage()).doesNotContain("test-api-key");
				});
	}

	@Test
	void emptyBalanceSetIsPreservedRatherThanTreatedAsZero() {
		respond("/api/v3/account", 200, "{\"canTrade\":true,\"balances\":[]}");

		ExchangeBalances balances = adapter().getBalances(credential());

		assertThat(balances.assets()).isEmpty();
		assertThat(balances.free("USDT")).isNull();
	}

	@Test
	void totalsOnlyAddWhenBothComponentsArePresent() {
		var partial = new com.shyblack.cryptosignals.exchange.ExchangeAssetBalance("USDT", null, new BigDecimal("5"));
		var complete = new com.shyblack.cryptosignals.exchange.ExchangeAssetBalance("BTC", new BigDecimal("1"), new BigDecimal("2"));

		assertThat(partial.total()).as("an unknown component yields no total").isNull();
		assertThat(complete.total()).isEqualByComparingTo("3");
	}

	@Test
	void assetBalanceRequiresAnAssetName() {
		assertThatThrownBy(() -> new com.shyblack.cryptosignals.exchange.ExchangeAssetBalance(
				"  ", new BigDecimal("1"), BigDecimal.ZERO))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void balancesConstructorToleratesANullMap() {
		ExchangeBalances balances = new ExchangeBalances(null, true, java.time.Instant.now());
		assertThat(balances.assets()).isEmpty();
		assertThat(balances.find("USDT")).isEmpty();
		assertThat(balances.find(null)).isEmpty();
	}
}