package com.shyblack.cryptosignals.exchange.futures.binance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.shyblack.cryptosignals.config.FuturesTradingProperties;
import com.shyblack.cryptosignals.config.SettingsProperties;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderType;
import com.shyblack.cryptosignals.entity.enums.FuturesPositionMode;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.exchange.ExchangeAdapterException;
import com.shyblack.cryptosignals.exchange.binance.FakeExchangeHttp;
import com.shyblack.cryptosignals.exchange.futures.FuturesOrderResult;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderStatus;
import com.shyblack.cryptosignals.exchange.futures.PlaceFuturesOrderRequest;
import com.shyblack.cryptosignals.exchange.binance.FakeExchangeHttp;
import com.shyblack.cryptosignals.security.ExchangeCredentialEncryptor;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Phase 9 — Binance USDT-M FUTURES adapter execution boundary.
 *
 * <p>Drives the real {@link BinanceFuturesLiveAdapter} against a loopback HTTP
 * server. URL construction, HMAC signing, the API key header, parameter selection
 * per order type and JSON parsing are all exercised for real. No request can reach
 * Binance and no production credential is used.
 */
class BinanceFuturesAdapterExecutionTest {

	private static final String TEST_SEED = "phase9-local-http-test-seed-never-used-in-production";
	private static final String TEST_API_KEY = "phase9-local-api-key";
	private static final String TEST_API_SECRET = "phase9-local-api-secret";

	private FakeExchangeHttp exchange;
	private BinanceFuturesLiveAdapter adapter;
	private ExchangeCredential credential;

	@BeforeEach
	void setUp() throws Exception {
		exchange = new FakeExchangeHttp();
		ExchangeCredentialEncryptor encryptor = new ExchangeCredentialEncryptor(
				new SettingsProperties(2.0, 20.0, 4, TEST_SEED, null, null, null));
		adapter = new BinanceFuturesLiveAdapter(props(exchange.baseUrl()), encryptor);

		credential = new ExchangeCredential();
		credential.setExchange(ExchangeName.BINANCE);
		credential.setApiKey(encryptor.encrypt(TEST_API_KEY));
		credential.setApiSecret(encryptor.encrypt(TEST_API_SECRET));
	}

	@AfterEach
	void tearDown() {
		exchange.close();
	}

	private static FuturesTradingProperties props(String baseUrl) {
		return new FuturesTradingProperties(
				FuturesTradingProperties.Mode.EXCHANGE, baseUrl, baseUrl, 5000, 3,
				FuturesMarginMode.ISOLATED, FuturesPositionMode.ONE_WAY,
				new BigDecimal("200.00"), 2, new BigDecimal("5.00"),
				new BigDecimal("0.30"), new BigDecimal("15.00"), false);
	}

	private PlaceFuturesOrderRequest marketLong(String symbol, String qty) {
		return new PlaceFuturesOrderRequest(symbol, PositionSide.LONG, PositionSide.LONG,
				FuturesOrderType.MARKET, false, new BigDecimal(qty), null, null,
				"SBF-testclientorderid1");
	}

	private static String filledFuturesJson(String status) {
		return """
				{"orderId":555,"symbol":"BTCUSDT","status":"%s","clientOrderId":"SBF-testclientorderid1",
				 "price":"0","avgPrice":"50000.00000","origQty":"1.000","executedQty":"1.000",
				 "cumQuote":"50000.00000","side":"BUY","positionSide":"BOTH","type":"MARKET",
				 "reduceOnly":false,"updateTime":1700000000123}
				""".formatted(status);
	}

	// ============================================ G/H. request construction + signing

	@Test
	void marketOrderPostsToTheFuturesOrderEndpoint() {
		exchange.on("/fapi/v1/order", filledFuturesJson("NEW"));

		adapter.placeOrder(credential, marketLong("BTCUSDT", "1"));

		assertThat(exchange.lastRequest().method()).isEqualTo("POST");
		assertThat(exchange.lastRequest().path()).isEqualTo("/fapi/v1/order");
	}

	@Test
	void everySignedRequestCarriesTimestampAndRecvWindow() {
		exchange.on("/fapi/v1/order", filledFuturesJson("NEW"));

		long before = System.currentTimeMillis();
		adapter.placeOrder(credential, marketLong("BTCUSDT", "1"));
		long after = System.currentTimeMillis();

		assertThat(Long.parseLong(exchange.param("timestamp"))).isBetween(before - 1000, after + 1000);
		assertThat(exchange.param("recvWindow")).isEqualTo("5000");
	}

	@Test
	void theApiKeyTravelsInTheHeaderAndTheSignatureIsVerifiedAgainstTheQuery() {
		exchange.on("/fapi/v1/order", filledFuturesJson("NEW"));

		adapter.placeOrder(credential, marketLong("BTCUSDT", "1"));

		assertThat(exchange.header("X-MBX-APIKEY")).isEqualTo(TEST_API_KEY);
		String signature = exchange.param("signature");
		assertThat(signature).matches("^[0-9a-f]{64}$");

		String raw = exchange.lastRequest().rawQuery();
		String signedPayload = raw.substring(0, raw.length() - ("&signature=" + signature).length());
		assertThat(signature).isEqualTo(
				BinanceFuturesSignatureUtil.hmacSha256Hex(TEST_API_SECRET, signedPayload));
	}

	@Test
	void futuresAndSpotSignaturesDifferBecauseTheSecretsDiffer() {
		// Two independent signing utilities must both produce valid lowercase hex;
		// the spot suite covers its own verification in full.
		exchange.on("/fapi/v1/order", filledFuturesJson("NEW"));
		adapter.placeOrder(credential, marketLong("BTCUSDT", "1"));

		assertThat(BinanceFuturesSignatureUtil.hmacSha256Hex("secret", "a=1"))
				.matches("^[0-9a-f]{64}$");
	}

	@Test
	void longMapsToBuyAndShortMapsToSell() {
		exchange.on("/fapi/v1/order", filledFuturesJson("NEW"));
		adapter.placeOrder(credential, marketLong("BTCUSDT", "1"));
		assertThat(exchange.param("side")).isEqualTo("BUY");

		exchange.on("/fapi/v1/order", filledFuturesJson("NEW"));
		adapter.placeOrder(credential, new PlaceFuturesOrderRequest("BTCUSDT",
				PositionSide.SHORT, PositionSide.SHORT, FuturesOrderType.MARKET, false,
				BigDecimal.ONE, null, null, "SBF-short01"));
		assertThat(exchange.param("side")).isEqualTo("SELL");
	}

	@Test
	void positionSideIsOmittedInOneWayMode() {
		exchange.on("/fapi/v1/order", filledFuturesJson("NEW"));

		adapter.placeOrder(credential, marketLong("BTCUSDT", "1"));

		// In ONE_WAY mode Binance defaults to BOTH. Sending positionSide would address
		// a specific leg and is invalid for the mode the account is configured for.
		assertThat(exchange.paramOrNull("positionSide")).isNull();
	}

	@Test
	void reduceOnlyIsSentOnlyWhenTrue() {
		exchange.on("/fapi/v1/order", filledFuturesJson("NEW"));
		adapter.placeOrder(credential, marketLong("BTCUSDT", "1"));
		assertThat(exchange.paramOrNull("reduceOnly")).isNull();

		exchange.on("/fapi/v1/order", filledFuturesJson("NEW"));
		adapter.placeOrder(credential, new PlaceFuturesOrderRequest("BTCUSDT",
				PositionSide.SHORT, PositionSide.SHORT, FuturesOrderType.MARKET, true,
				BigDecimal.ONE, null, null, "SBF-reduce01"));
		assertThat(exchange.param("reduceOnly")).isEqualTo("true");
	}

	@Test
	void marketOrderSendsNoPriceAndNoTimeInForce() {
		exchange.on("/fapi/v1/order", filledFuturesJson("NEW"));

		adapter.placeOrder(credential, marketLong("BTCUSDT", "1"));

		assertThat(exchange.paramOrNull("price")).isNull();
		assertThat(exchange.paramOrNull("timeInForce")).isNull();
	}

	@Test
	void limitOrderCarriesPriceAndGtcTimeInForce() {
		exchange.on("/fapi/v1/order", filledFuturesJson("NEW"));

		adapter.placeOrder(credential, new PlaceFuturesOrderRequest("BTCUSDT",
				PositionSide.LONG, PositionSide.LONG, FuturesOrderType.LIMIT, false,
				BigDecimal.ONE, new BigDecimal("49000"), null, "SBF-limit001"));

		assertThat(exchange.param("type")).isEqualTo("LIMIT");
		assertThat(exchange.param("price")).isEqualTo("49000");
		assertThat(exchange.param("timeInForce"))
				.as("a futures LIMIT order without timeInForce is rejected by Binance")
				.isEqualTo("GTC");
	}

	@Test
	void aLimitOrderWithoutAPriceIsRefusedBeforeAnyRequestIsSent() {
		ExchangeAdapterException thrown = assertThrows(ExchangeAdapterException.class,
				() -> adapter.placeOrder(credential, new PlaceFuturesOrderRequest("BTCUSDT",
						PositionSide.LONG, PositionSide.LONG, FuturesOrderType.LIMIT, false,
						BigDecimal.ONE, null, null, "SBF-nolimit01")));

		assertThat(thrown.getMessage()).contains("LIMIT");
		assertThat(thrown.retryable()).isFalse();
		assertThat(exchange.requestCount())
				.as("an invalid order must never reach the exchange")
				.isZero();
	}

	@Test
	void stopMarketOrderCarriesItsStopPriceAndNoTimeInForce() {
		exchange.on("/fapi/v1/order", filledFuturesJson("NEW"));

		adapter.placeOrder(credential, new PlaceFuturesOrderRequest("BTCUSDT",
				PositionSide.SHORT, PositionSide.SHORT, FuturesOrderType.STOP_MARKET, true,
				BigDecimal.ONE, null, new BigDecimal("48000"), "SBF-stop0001"));

		assertThat(exchange.param("type")).isEqualTo("STOP_MARKET");
		assertThat(exchange.param("stopPrice")).isEqualTo("48000");
		assertThat(exchange.param("reduceOnly")).isEqualTo("true");
		assertThat(exchange.paramOrNull("timeInForce"))
				.as("timeInForce is only valid on LIMIT")
				.isNull();
	}

	@Test
	void quantityIsSentInPlainNotationWithoutTrailingZeros() {
		exchange.on("/fapi/v1/order", filledFuturesJson("NEW"));

		adapter.placeOrder(credential, marketLong("ETHUSDT", "1.50000000"));

		assertThat(exchange.param("quantity")).isEqualTo("1.5");
	}

	@Test
	void theClientOrderIdIsForwardedVerbatim() {
		exchange.on("/fapi/v1/order", filledFuturesJson("NEW"));

		adapter.placeOrder(credential, marketLong("BTCUSDT", "1"));

		assertThat(exchange.param("newClientOrderId")).isEqualTo("SBF-testclientorderid1");
	}

	@Test
	void leverageAndMarginModeAreConfiguredOnSeparateEndpoints() {
		exchange.on("/fapi/v1/leverage", """
				{"leverage":3,"maxNotionalValue":"1000000","symbol":"BTCUSDT"}
				""");
		exchange.on("/fapi/v1/marginType", """
				{"code":200,"msg":"success"}
				""");

		adapter.setLeverage(credential, "BTCUSDT", 3);
		adapter.setMarginMode(credential, "BTCUSDT", FuturesMarginMode.ISOLATED);

		assertThat(exchange.paths()).containsExactly("/fapi/v1/leverage", "/fapi/v1/marginType");
// The last request was the margin-mode call.
		assertThat(exchange.lastRequest().query().get("marginType")).isEqualTo("ISOLATED");
		assertThat(exchange.lastRequest().query().get("leverage"))
				.as("leverage is configured on its own endpoint, never inside the order")
				.isNull();
	}

	// ==================================================== J. response mapping

	@Test
	void newStatusMapsToAcknowledgedNotFilled() {
		exchange.on("/fapi/v1/order", filledFuturesJson("NEW"));

		FuturesOrderResult result = adapter.placeOrder(credential, marketLong("BTCUSDT", "1"));

		assertThat(result.status())
				.as("HTTP 200 must never be treated as a fill")
				.isEqualTo(FuturesOrderStatus.ACKNOWLEDGED);
	}

	@Test
	void filledStatusMapsToFilledAndCarriesTheExchangeOrderId() {
		exchange.on("/fapi/v1/order", filledFuturesJson("FILLED"));

		FuturesOrderResult result = adapter.placeOrder(credential, marketLong("BTCUSDT", "1"));

		assertThat(result.status()).isEqualTo(FuturesOrderStatus.FILLED);
		assertThat(result.exchangeOrderId()).isEqualTo("555");
		assertThat(result.executedQuantity()).isEqualByComparingTo("1");
		assertThat(result.avgFillPrice()).isEqualByComparingTo("50000");
	}

	@Test
	void partiallyFilledStatusMapsToPartiallyFilled() {
		exchange.on("/fapi/v1/order", filledFuturesJson("PARTIALLY_FILLED"));

		assertThat(adapter.placeOrder(credential, marketLong("BTCUSDT", "1")).status())
				.isEqualTo(FuturesOrderStatus.PARTIALLY_FILLED);
	}

	@Test
	void canceledAndPendingCancelBothMapToCancelled() {
		for (String raw : List.of("CANCELED", "PENDING_CANCEL")) {
			exchange.on("/fapi/v1/order", filledFuturesJson(raw));
			assertThat(adapter.placeOrder(credential, marketLong("BTCUSDT", "1")).status())
					.as("status %s", raw)
					.isEqualTo(FuturesOrderStatus.CANCELLED);
		}
	}

	@Test
	void anUnrecognisedStatusBecomesUnknownRatherThanAFill() {
		exchange.on("/fapi/v1/order", filledFuturesJson("SOMETHING_ELSE"));

		assertThat(adapter.placeOrder(credential, marketLong("BTCUSDT", "1")).status())
				.isEqualTo(FuturesOrderStatus.UNKNOWN);
	}

	@Test
	void aResponseWithoutAStatusFieldBecomesUnknown() {
		exchange.on("/fapi/v1/order", """
				{"orderId":556,"symbol":"BTCUSDT","origQty":"1.000","executedQty":"0.000"}
				""");

		assertThat(adapter.placeOrder(credential, marketLong("BTCUSDT", "1")).status())
				.isEqualTo(FuturesOrderStatus.UNKNOWN);
	}

	// ============================================================== K. rejection

	@Test
	void aRejectedOrderSurfacesTheExchangeCode() {
		exchange.on("/fapi/v1/order", 400,
				"{\"code\":-2019,\"msg\":\"Margin is insufficient.\"}");

		ExchangeAdapterException thrown = assertThrows(ExchangeAdapterException.class,
				() -> adapter.placeOrder(credential, marketLong("BTCUSDT", "1")));

		assertThat(thrown.exchangeCode()).isEqualTo(-2019);
		assertThat(thrown.retryable())
				.as("a rejection is a definite answer and must not be retried")
				.isFalse();
	}

	@Test
	void anAdapterFailureMessageNeverCarriesTheCredential() {
		exchange.on("/fapi/v1/order", 400, "{\"code\":-1121,\"msg\":\"Invalid symbol.\"}");

		ExchangeAdapterException thrown = assertThrows(ExchangeAdapterException.class,
				() -> adapter.placeOrder(credential, marketLong("BTCUSDT", "1")));

		assertThat(String.valueOf(thrown.getMessage()))
				.doesNotContain(TEST_API_KEY)
				.doesNotContain(TEST_API_SECRET)
				.doesNotContain("signature");
	}

	// =================================================== L. UNKNOWN transport outcome

	@Test
	void aTransportFailureIsMarkedRetryableSoTheCallerReconciles() {
		exchange.on("/fapi/v1/order", 502, "{\"code\":-1001,\"msg\":\"Bad gateway.\"}");

		ExchangeAdapterException thrown = assertThrows(ExchangeAdapterException.class,
				() -> adapter.placeOrder(credential, marketLong("BTCUSDT", "1")));

		assertThat(thrown.retryable())
				.as("retryable here means 'reconcile', never 'resend'")
				.isTrue();
		assertThat(exchange.requestCount())
				.as("the adapter must never retry an order submission on its own")
				.isEqualTo(1);
	}

	@Test
	void aReadTimeoutProducesAnAmbiguousOutcomeRatherThanAFabricatedFill() {
		exchange.onSlow("/fapi/v1/order", TimeUnit.SECONDS.toMillis(30));

		ExchangeAdapterException thrown = assertThrows(ExchangeAdapterException.class,
				() -> adapter.placeOrder(credential, marketLong("BTCUSDT", "1")));

		assertThat(thrown).isNotNull();
		assertThat(exchange.requestCount())
				.as("the attempt was genuinely made, so reconciliation can query it")
				.isEqualTo(1);
	}

	@Test
	void aMalformedJsonBodyDoesNotProduceAFill() {
		exchange.on("/fapi/v1/order", "not json at all");

		assertThrows(Exception.class,
				() -> adapter.placeOrder(credential, marketLong("BTCUSDT", "1")));
	}

	// ============================================== Y/Z. fee handling (defect 2)

	@Test
	void anAbsentFeeStaysUnknownRatherThanBecomingZero() {
		exchange.on("/fapi/v1/order", filledFuturesJson("FILLED"));

		FuturesOrderResult result = adapter.placeOrder(credential, marketLong("BTCUSDT", "1"));

		// RESULT carries no fills[], so real commission is unavailable at submission.
		// A fabricated zero would be indistinguishable from "Binance charged nothing".
		assertThat(result.fee())
				.as("an absent futures fee must not become a stored zero")
				.isNull();
		assertThat(result.feeAsset()).isNull();
	}

	@Test
	void aFilledFuturesOrderDoesNotImplyAZeroFee() {
		exchange.on("/fapi/v1/order", filledFuturesJson("FILLED"));

		FuturesOrderResult result = adapter.placeOrder(credential, marketLong("BTCUSDT", "1"));

		assertThat(result.status()).isEqualTo(FuturesOrderStatus.FILLED);
		assertThat(result.fee())
				.as("a fill is known; its commission is not, and must stay unknown")
				.isNull();
	}
}