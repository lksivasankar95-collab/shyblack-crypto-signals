package com.shyblack.cryptosignals.exchange.futures.binance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shyblack.cryptosignals.config.FuturesTradingProperties;
import com.shyblack.cryptosignals.config.SettingsProperties;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.entity.enums.FuturesPositionMode;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.exchange.ExchangeAdapterException;
import com.shyblack.cryptosignals.exchange.futures.FuturesAccountSnapshot;
import com.shyblack.cryptosignals.exchange.futures.FuturesExchangePosition;
import com.shyblack.cryptosignals.security.ExchangeCredentialEncryptor;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Deterministic tests of the real Binance USDT-M futures adapter's read path, covering the
 * authoritative position-risk response and the account totals.
 *
 * <p>A local {@link HttpServer} on an ephemeral loopback port stands in for Binance so real signing,
 * parsing and error mapping execute without contacting any production endpoint.
 */
class BinanceFuturesLiveAdapterReadTest {

	private HttpServer server;
	private final AtomicReference<String> lastApiKeyHeader = new AtomicReference<>();

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
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, bytes.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		});
	}

	/** Position-mode probe must always answer so the account read does not fall back. */
	private void respondPositionMode() {
		respond("/fapi/v1/positionSide/dual", 200, "{\"dualSidePosition\":false}");
	}

	private BinanceFuturesLiveAdapter adapter() {
		FuturesTradingProperties props = new FuturesTradingProperties(
				FuturesTradingProperties.Mode.EXCHANGE, baseUrl(), null, 5000L, 3, null, null,
				null, 2, null, null, null, false);
		SettingsProperties settings = new SettingsProperties(
				0, 0, 0, "phase3-test-only-encryption-seed", null, null, null);
		return new BinanceFuturesLiveAdapter(props, new ExchangeCredentialEncryptor(settings));
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

	// ------------------------------------------------------------- positions

	@Test
	void readsALongPositionWithEveryReportedField() {
		respond("/fapi/v2/positionRisk", 200, """
				[{"symbol":"BTCUSDT","positionAmt":"0.500","entryPrice":"60000.0","markPrice":"61000.5",
				  "unRealizedProfit":"500.25","liquidationPrice":"45000.0","leverage":"3",
				  "marginType":"isolated","isolatedMargin":"10000.0","notional":"30500.25"}]""");

		List<FuturesExchangePosition> positions = adapter().getPositions(credential());

		assertThat(positions).hasSize(1);
		FuturesExchangePosition p = positions.get(0);
		assertThat(p.symbol()).isEqualTo("BTCUSDT");
		assertThat(p.positionSide()).isEqualTo(PositionSide.LONG);
		assertThat(p.positionAmount()).isEqualByComparingTo("0.5");
		assertThat(p.quantity()).isEqualByComparingTo("0.5");
		assertThat(p.entryPrice()).isEqualByComparingTo("60000");
		assertThat(p.markPrice()).isEqualByComparingTo("61000.5");
		assertThat(p.liquidationPrice()).isEqualByComparingTo("45000");
		assertThat(p.leverage()).isEqualTo(3);
		assertThat(p.marginMode()).isEqualTo(FuturesMarginMode.ISOLATED);
		assertThat(p.isolatedMargin()).isEqualByComparingTo("10000");
		assertThat(p.notional()).isEqualByComparingTo("30500.25");
		assertThat(p.unrealizedProfit()).isEqualByComparingTo("500.25");
		assertThat(p.isOpen()).isTrue();
	}

	@Test
	void aNegativeAmountIsAShortPosition() {
		respond("/fapi/v2/positionRisk", 200,
				"[{\"symbol\":\"ETHUSDT\",\"positionAmt\":\"-2\",\"entryPrice\":\"3000\",\"markPrice\":\"2900\"}]");

		FuturesExchangePosition p = adapter().getPositions(credential()).get(0);

		assertThat(p.positionSide()).isEqualTo(PositionSide.SHORT);
		assertThat(p.positionAmount()).isEqualByComparingTo("-2");
		assertThat(p.quantity()).as("quantity is the absolute size").isEqualByComparingTo("2");
		assertThat(p.isOpen()).isTrue();
	}

	@Test
	void readsMultiplePositionsOfBothDirections() {
		respond("/fapi/v2/positionRisk", 200, """
				[{"symbol":"BTCUSDT","positionAmt":"0.5","entryPrice":"60000"},
				 {"symbol":"ETHUSDT","positionAmt":"-2","entryPrice":"3000"},
				 {"symbol":"SOLUSDT","positionAmt":"10","entryPrice":"150"}]""");

		List<FuturesExchangePosition> positions = adapter().getPositions(credential());

		assertThat(positions).hasSize(3);
		assertThat(positions).extracting(FuturesExchangePosition::symbol)
				.containsExactly("BTCUSDT", "ETHUSDT", "SOLUSDT");
		assertThat(positions).extracting(FuturesExchangePosition::positionSide)
				.containsExactly(PositionSide.LONG, PositionSide.SHORT, PositionSide.LONG);
	}

	@Test
	void aFlatPositionIsOmittedBecauseClosedIsARealState() {
		respond("/fapi/v2/positionRisk", 200, """
				[{"symbol":"BTCUSDT","positionAmt":"0.000","entryPrice":"0","markPrice":"61000"},
				 {"symbol":"ETHUSDT","positionAmt":"-0.00","entryPrice":"0","markPrice":"2900"}]""");

		List<FuturesExchangePosition> positions = adapter().getPositions(credential());

		assertThat(positions)
				.as("zero-amount positions are closed on the exchange, not open positions")
				.isEmpty();
	}

	@Test
	void anEmptyPositionListIsARealNoOpenPositionsResult() {
		respond("/fapi/v2/positionRisk", 200, "[]");

		assertThat(adapter().getPositions(credential())).isEmpty();
	}

	@Test
	void aZeroLiquidationPriceBecomesNullBecauseBinanceMeansNotApplicable() {
		respond("/fapi/v2/positionRisk", 200, """
				[{"symbol":"BTCUSDT","positionAmt":"1","entryPrice":"60000","liquidationPrice":"0"}]""");

		FuturesExchangePosition p = adapter().getPositions(credential()).get(0);

		assertThat(p.liquidationPrice())
				.as("0 means not applicable, not liquidated at zero")
				.isNull();
	}

	@Test
	void omittedOptionalFieldsStayNullInsteadOfBecomingZero() {
		respond("/fapi/v2/positionRisk", 200,
				"[{\"symbol\":\"BTCUSDT\",\"positionAmt\":\"1\",\"entryPrice\":\"60000\"}]");

		FuturesExchangePosition p = adapter().getPositions(credential()).get(0);

		assertThat(p.markPrice()).isNull();
		assertThat(p.liquidationPrice()).isNull();
		assertThat(p.leverage()).isNull();
		assertThat(p.marginMode()).isNull();
		assertThat(p.isolatedMargin()).isNull();
		assertThat(p.notional()).isNull();
		assertThat(p.unrealizedProfit()).isNull();
	}

	@Test
	void crossedMarginModeIsReadBackFromTheExchange() {
		respond("/fapi/v2/positionRisk", 200,
				"[{\"symbol\":\"BTCUSDT\",\"positionAmt\":\"1\",\"marginType\":\"cross\"}]");

		assertThat(adapter().getPositions(credential()).get(0).marginMode())
				.isEqualTo(FuturesMarginMode.CROSS);
	}

	@Test
	void anUnrecognisedMarginTypeIsUnknownRatherThanAssumedIsolated() {
		respond("/fapi/v2/positionRisk", 200,
				"[{\"symbol\":\"BTCUSDT\",\"positionAmt\":\"1\",\"marginType\":\"something-else\"}]");

		assertThat(adapter().getPositions(credential()).get(0).marginMode()).isNull();
	}

	@Test
	void aMalformedNonNumericFieldIsRejected() {
		respond("/fapi/v2/positionRisk", 200,
				"[{\"symbol\":\"BTCUSDT\",\"positionAmt\":\"abc\"}]");

		assertThatThrownBy(() -> adapter().getPositions(credential()))
				.isInstanceOf(ExchangeAdapterException.class)
				.hasMessageContaining("positionAmt");
	}

	@Test
	void positionRequestIsSignedAndCarriesTheApiKeyHeader() {
		respond("/fapi/v2/positionRisk", 200, "[]");

		adapter().getPositions(credential());

		assertThat(lastApiKeyHeader.get()).isEqualTo("test-api-key");
	}

	@Test
	void positionsRejectsAnAuthenticationFailure() {
		respond("/fapi/v2/positionRisk", 401, "{\"code\":-2015,\"msg\":\"Invalid API-key\"}");

		assertThatThrownBy(() -> adapter().getPositions(credential()))
				.isInstanceOf(ExchangeAdapterException.class)
				.satisfies(ex -> {
					ExchangeAdapterException e = (ExchangeAdapterException) ex;
					assertThat(e.exchangeCode()).isEqualTo(-2015);
					assertThat(e.retryable()).isFalse();
				});
	}

	@Test
	void positionsRejectsAServerError() {
		respond("/fapi/v2/positionRisk", 500, "{\"code\":-1000,\"msg\":\"Unknown error\"}");

		assertThatThrownBy(() -> adapter().getPositions(credential()))
				.isInstanceOf(ExchangeAdapterException.class)
				.satisfies(ex -> {
					ExchangeAdapterException e = (ExchangeAdapterException) ex;
					assertThat(e.httpStatus()).isEqualTo(500);
					assertThat(e.retryable()).isTrue();
				});
	}

	// --------------------------------------------------------------- account

	@Test
	void readsAccountTotalsStrictly() {
		respond("/fapi/v2/account", 200, """
				{"totalWalletBalance":"1000.5","availableBalance":"650.25","totalMarginBalance":"1050.75",
				 "totalInitialMargin":"400.0","totalMaintMargin":"12.5","totalUnrealizedProfit":"50.25",
				 "canTrade":true}""");
		respondPositionMode();

		FuturesAccountSnapshot snapshot = adapter().getAccount(credential());

		assertThat(snapshot.walletBalance()).isEqualByComparingTo("1000.5");
		assertThat(snapshot.availableBalance()).isEqualByComparingTo("650.25");
		assertThat(snapshot.marginBalance()).isEqualByComparingTo("1050.75");
		assertThat(snapshot.usedMargin()).isEqualByComparingTo("400");
		assertThat(snapshot.maintenanceMargin()).isEqualByComparingTo("12.5");
		assertThat(snapshot.unrealizedPnl()).isEqualByComparingTo("50.25");
		assertThat(snapshot.canTrade()).isTrue();
		assertThat(snapshot.positionMode()).isEqualTo(FuturesPositionMode.ONE_WAY);
		assertThat(snapshot.marginMode())
				.as("an account aggregate has no single per-symbol margin mode")
				.isNull();
	}

	@Test
	void aMissingAccountTotalIsRejectedInsteadOfBecomingZero() {
		respond("/fapi/v2/account", 200, """
				{"availableBalance":"650.25","totalMarginBalance":"1050.75","totalInitialMargin":"400.0",
				 "totalMaintMargin":"12.5","totalUnrealizedProfit":"50.25","canTrade":true}""");
		respondPositionMode();

		assertThatThrownBy(() -> adapter().getAccount(credential()))
				.isInstanceOf(ExchangeAdapterException.class)
				.hasMessageContaining("totalWalletBalance");
	}

	@Test
	void hedgePositionModeIsReadBackFromTheExchange() {
		respond("/fapi/v2/account", 200, """
				{"totalWalletBalance":"1","availableBalance":"1","totalMarginBalance":"1",
				 "totalInitialMargin":"0","totalMaintMargin":"0","totalUnrealizedProfit":"0","canTrade":true}""");
		respond("/fapi/v1/positionSide/dual", 200, "{\"dualSidePosition\":true}");

		assertThat(adapter().getAccount(credential()).positionMode())
				.isEqualTo(FuturesPositionMode.HEDGE);
	}

	@Test
	void anExplicitZeroAccountValueIsPreserved() {
		respond("/fapi/v2/account", 200, """
				{"totalWalletBalance":"0","availableBalance":"0","totalMarginBalance":"0",
				 "totalInitialMargin":"0","totalMaintMargin":"0","totalUnrealizedProfit":"0","canTrade":true}""");
		respondPositionMode();

		FuturesAccountSnapshot snapshot = adapter().getAccount(credential());

		assertThat(snapshot.walletBalance()).isEqualByComparingTo("0");
		assertThat(snapshot.unrealizedPnl()).isEqualByComparingTo("0");
	}
}