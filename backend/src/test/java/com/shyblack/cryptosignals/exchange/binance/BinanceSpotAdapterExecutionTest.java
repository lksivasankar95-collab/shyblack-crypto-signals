package com.shyblack.cryptosignals.exchange.binance;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.config.LiveTradingProperties;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.LiveOrderStatus;
import com.shyblack.cryptosignals.entity.enums.LiveOrderType;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.exchange.ExchangeAdapterException;
import com.shyblack.cryptosignals.exchange.ExchangeOrderResult;
import com.shyblack.cryptosignals.exchange.PlaceOrderRequest;
import com.shyblack.cryptosignals.security.AesGcmEncryptor;
import com.shyblack.cryptosignals.config.SettingsProperties;
import com.shyblack.cryptosignals.security.ExchangeCredentialEncryptor;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Phase 9 — Binance SPOT adapter execution boundary.
 *
 * <p>Every test drives a real {@link BinanceLiveTradingAdapter} against a loopback
 * HTTP server ({@link FakeExchangeHttp}). Nothing is stubbed between the adapter and
 * the socket: the URL is built, the query string is encoded, the HMAC is computed,
 * the header is set, and the JSON response is parsed. No request can reach Binance
 * and no production credential is involved.
 *
 * <p>The assertions that matter are on the <b>generated request</b> — method, path,
 * parameters, signature and API key header — not only on the parsed result. A test
 * that only checked the returned status would pass while the adapter sent a
 * malformed order to a real exchange.
 */
class BinanceSpotAdapterExecutionTest {

	/** Throwaway test-only key material. Never a production credential. */
	private static final String TEST_SEED = "phase9-local-http-test-seed-never-used-in-production";
	private static final String TEST_API_KEY = "phase9-local-api-key";
	private static final String TEST_API_SECRET = "phase9-local-api-secret";

	private FakeExchangeHttp exchange;
	private BinanceLiveTradingAdapter adapter;
	private ExchangeCredential credential;

	@BeforeEach
	void setUp() throws Exception {
		exchange = new FakeExchangeHttp();
		// The real encryptor, so the round trip encryption->decryption is exercised.
		ExchangeCredentialEncryptor encryptor = new ExchangeCredentialEncryptor(
				new SettingsProperties(2.0, 20.0, 4, TEST_SEED, null, null, null));
		adapter = new BinanceLiveTradingAdapter(props(exchange.baseUrl()), encryptor);

		credential = new ExchangeCredential();
		credential.setUser(null);
		credential.setExchange(ExchangeName.BINANCE);
		credential.setApiKey(encryptor.encrypt(TEST_API_KEY));
		credential.setApiSecret(encryptor.encrypt(TEST_API_SECRET));
	}

	@AfterEach
	void tearDown() {
		exchange.close();
	}

	private static LiveTradingProperties props(String baseUrl) {
		return new LiveTradingProperties(
				LiveTradingProperties.Mode.EXCHANGE, baseUrl, baseUrl, baseUrl,
				5000, new BigDecimal("200.00"), 3, new BigDecimal("5.00"), false);
	}

	private PlaceOrderRequest marketBuy(String symbol, String qty) {
		return new PlaceOrderRequest(symbol, PositionSide.LONG, LiveOrderType.MARKET,
				new BigDecimal(qty), null, null, "SB-testclientorderid01");
	}

	private static String filledSpotJson() {
		return """
				{"symbol":"BTCUSDT","orderId":123456,"clientOrderId":"SB-testclientorderid01",
				 "transactTime":1700000000123,"price":"0.00000000","origQty":"1.00000000",
				 "executedQty":"1.00000000","cummulativeQuoteQty":"50000.00000000","status":"FILLED",
				 "timeInForce":"GTC","type":"MARKET","side":"BUY",
				 "fills":[{"price":"50000.00000000","qty":"1.00000000","commission":"0.00100000",
				           "commissionAsset":"USDT","tradeId":999}]}
				""";
	}

	// ============================================ A/B. request construction + signing

	@Test
	void marketOrderPostsToTheOrderEndpointWithTheExpectedMethod() {
		exchange.on("/api/v3/order", filledSpotJson());

		adapter.placeOrder(credential, marketBuy("BTCUSDT", "1"));

		assertThat(exchange.lastRequest().method()).isEqualTo("POST");
		assertThat(exchange.lastRequest().path()).isEqualTo("/api/v3/order");
	}

	@Test
	void everySignedRequestCarriesTheTimestampAndRecvWindow() {
		exchange.on("/api/v3/order", filledSpotJson());

		long before = System.currentTimeMillis();
		adapter.placeOrder(credential, marketBuy("BTCUSDT", "1"));
		long after = System.currentTimeMillis();

		long timestamp = Long.parseLong(exchange.param("timestamp"));
		assertThat(timestamp)
				.as("the exchange rejects a stale or absent timestamp")
				.isBetween(before - 1000, after + 1000);
		assertThat(exchange.param("recvWindow")).isEqualTo("5000");
	}

	@Test
	void theApiKeyTravelsInTheHeaderAndTheSignatureInTheQuery() {
		exchange.on("/api/v3/order", filledSpotJson());

		adapter.placeOrder(credential, marketBuy("BTCUSDT", "1"));

		assertThat(exchange.header("X-MBX-APIKEY")).isEqualTo(TEST_API_KEY);
		assertThat(exchange.paramOrNull("signature"))
				.as("the signature must be present and lowercase hex")
				.isNotNull()
				.matches("^[0-9a-f]{64}$");
	}

	@Test
	void theSignatureCoversEverySignedParameterExceptItself() {
		exchange.on("/api/v3/order", filledSpotJson());

		adapter.placeOrder(credential, marketBuy("BTCUSDT", "1"));

		String raw = exchange.lastRequest().rawQuery();
		String signature = exchange.param("signature");
		// Reconstruct exactly what was signed and re-verify, proving the signature
		// covers the real payload rather than an arbitrary string.
		String signedPayload = raw.substring(0, raw.length() - ("&signature=" + signature).length());
		String expected = BinanceSignatureUtil.hmacSha256Hex(TEST_API_SECRET, signedPayload);
		assertThat(signature)
				.as("signature must be HMAC-SHA256 of the signed query string")
				.isEqualTo(expected);
	}

	@Test
	void aDifferentSecretProducesADifferentSignature() {
		exchange.on("/api/v3/order", filledSpotJson());
		adapter.placeOrder(credential, marketBuy("BTCUSDT", "1"));
		String withSecretA = exchange.param("signature");

		AesGcmEncryptor other = new AesGcmEncryptor(TEST_SEED);
		ExchangeCredential otherCredential = new ExchangeCredential();
		otherCredential.setExchange(ExchangeName.BINANCE);
		otherCredential.setApiKey(other.encrypt(TEST_API_KEY));
		otherCredential.setApiSecret(other.encrypt("a-completely-different-secret"));

		exchange.on("/api/v3/order", filledSpotJson());
		adapter.placeOrder(otherCredential, marketBuy("BTCUSDT", "1"));

		assertThat(exchange.param("signature")).isNotEqualTo(withSecretA);
	}

	@Test
	void marketOrderOmitsPriceAndTimeInForce() {
		exchange.on("/api/v3/order", filledSpotJson());

		adapter.placeOrder(credential, marketBuy("BTCUSDT", "1"));

		// A MARKET order must not carry a limit price; Binance rejects the pair.
		assertThat(exchange.paramOrNull("price")).isNull();
		assertThat(exchange.paramOrNull("timeInForce")).isNull();
	}

	@Test
	void limitOrderCarriesPriceAndGtcTimeInForce() {
		exchange.on("/api/v3/order", """
				{"symbol":"BTCUSDT","orderId":1,"clientOrderId":"SB-limit01","status":"NEW",
				 "origQty":"1.00000000","executedQty":"0.00000000","price":"49000.00000000",
				 "cummulativeQuoteQty":"0.00000000"}
				""");

		adapter.placeOrder(credential, new PlaceOrderRequest("BTCUSDT", PositionSide.LONG,
				LiveOrderType.LIMIT, new BigDecimal("1"), new BigDecimal("49000"), null, "SB-limit01"));

		assertThat(exchange.param("price")).isEqualTo("49000");
		assertThat(exchange.param("timeInForce")).isEqualTo("GTC");
		assertThat(exchange.param("type")).isEqualTo("LIMIT");
	}

	@Test
	void quantityIsSentInPlainNotationWithoutTrailingZeros() {
		exchange.on("/api/v3/order", filledSpotJson());

		adapter.placeOrder(credential, marketBuy("ETHUSDT", "1.50000000"));

		// stripTrailingZeros().toPlainString(): "1.5", never "1.50000000" or "1.5E+0".
		assertThat(exchange.param("quantity")).isEqualTo("1.5");
		assertThat(exchange.param("quantity")).doesNotContain("E");
	}

	@Test
	void theRequestAsksForAFullResponseSoFillsAreAvailable() {
		exchange.on("/api/v3/order", filledSpotJson());

		adapter.placeOrder(credential, marketBuy("BTCUSDT", "1"));

		assertThat(exchange.param("newOrderRespType"))
				.as("FULL is what makes real commission available in fills[]")
				.isEqualTo("FULL");
	}

	@Test
	void theClientOrderIdIsForwardedVerbatim() {
		exchange.on("/api/v3/order", filledSpotJson());

		adapter.placeOrder(credential, marketBuy("BTCUSDT", "1"));

		assertThat(exchange.param("newClientOrderId")).isEqualTo("SB-testclientorderid01");
	}

	@Test
	void stopLossLimitOrderCarriesItsStopPrice() {
		exchange.on("/api/v3/order", """
				{"symbol":"BTCUSDT","orderId":2,"clientOrderId":"SB-stop01","status":"NEW",
				 "origQty":"1.00000000","executedQty":"0.00000000","price":"49000.00000000",
				 "cummulativeQuoteQty":"0.00000000"}
				""");

		adapter.placeOrder(credential, new PlaceOrderRequest("BTCUSDT", PositionSide.LONG,
				LiveOrderType.STOP_LOSS_LIMIT, new BigDecimal("1"), new BigDecimal("49000"),
				new BigDecimal("49000"), "SB-stop01"));

		assertThat(exchange.param("type")).isEqualTo("STOP_LOSS_LIMIT");
		assertThat(exchange.param("stopPrice")).isEqualTo("49000");
		assertThat(exchange.param("timeInForce"))
				.as("Binance requires GTC on a STOP_LOSS_LIMIT")
				.isEqualTo("GTC");
	}

	// ==================================================== side semantics (defect 1)

	@Test
	void spotSideIsSentAsBuyOrSellNotLongOrShort() {
		exchange.on("/api/v3/order", filledSpotJson());

		adapter.placeOrder(credential, marketBuy("BTCUSDT", "1"));

		String side = exchange.param("side");
		assertThat(side)
				.as("Binance spot accepts only BUY or SELL; the application's "
						+ "PositionSide.LONG must be translated")
				.isIn("BUY", "SELL");
		assertThat(side).isEqualTo("BUY");
	}

	@Test
	void aShortSignalMapsToSell() {
		exchange.on("/api/v3/order", """
				{"symbol":"BTCUSDT","orderId":3,"clientOrderId":"SB-sell01","status":"FILLED",
				 "origQty":"1.00000000","executedQty":"1.00000000","price":"0.00000000",
				 "cummulativeQuoteQty":"50000.00000000",
				 "fills":[{"price":"50000","qty":"1","commission":"0.001","commissionAsset":"USDT"}]}
				""");

		adapter.placeOrder(credential, new PlaceOrderRequest("BTCUSDT", PositionSide.SHORT,
				LiveOrderType.MARKET, BigDecimal.ONE, null, null, "SB-sell01"));

		assertThat(exchange.param("side")).isEqualTo("SELL");
	}

	// ============================================ C. symbol normalization (defect 4)

	@Test
	void aSymbolIsSentVerbatimWithNoNormalization() {
		exchange.on("/api/v3/order", filledSpotJson());

		adapter.placeOrder(credential, marketBuy("btcusdt", "1"));

		// Documents the real contract: the write path performs no normalization, while
		// the read paths do. Callers currently supply an exchangeInfo-derived uppercase
		// symbol, so this is an accident of the call chain rather than a guarantee.
		assertThat(exchange.param("symbol")).isEqualTo("btcusdt");
	}

	@Test
	void aSymbolWithSurroundingWhitespaceIsNotSilentlyTrimmed() {
		exchange.on("/api/v3/order", filledSpotJson());

		adapter.placeOrder(credential, marketBuy(" BTCUSDT ", "1"));

		assertThat(exchange.param("symbol"))
				.as("no hidden normalization exists on the write path")
				.isEqualTo(" BTCUSDT ");
	}

	// ======================================================= E. response mapping

	@Test
	void http200WithFilledStatusMapsToFilled() {
		exchange.on("/api/v3/order", filledSpotJson());

		ExchangeOrderResult result = adapter.placeOrder(credential, marketBuy("BTCUSDT", "1"));

		assertThat(result.status()).isEqualTo(LiveOrderStatus.FILLED);
		assertThat(result.exchangeOrderId()).isEqualTo("123456");
		assertThat(result.clientOrderId()).isEqualTo("SB-testclientorderid01");
		assertThat(result.executedQuantity()).isEqualByComparingTo("1");
		assertThat(result.cumulativeQuoteQty()).isEqualByComparingTo("50000");
	}

	@Test
	void http200WithNewStatusIsAcknowledgedNeverFilled() {
		exchange.on("/api/v3/order", """
				{"symbol":"BTCUSDT","orderId":4,"clientOrderId":"SB-testclientorderid01",
				 "status":"NEW","origQty":"1.00000000","executedQty":"0.00000000",
				 "price":"49000.00000000","cummulativeQuoteQty":"0.00000000"}
				""");

		ExchangeOrderResult result = adapter.placeOrder(credential, marketBuy("BTCUSDT", "1"));

		assertThat(result.status())
				.as("HTTP 200 must never be treated as a fill")
				.isEqualTo(LiveOrderStatus.ACKNOWLEDGED);
		assertThat(result.executedQuantity()).isEqualByComparingTo("0");
	}

	@Test
	void http200WithPartialStatusMapsToPartiallyFilled() {
		exchange.on("/api/v3/order", """
				{"symbol":"BTCUSDT","orderId":5,"clientOrderId":"SB-testclientorderid01",
				 "status":"PARTIALLY_FILLED","origQty":"2.00000000","executedQty":"1.00000000",
				 "price":"0.00000000","cummulativeQuoteQty":"50000.00000000",
				 "fills":[{"price":"50000","qty":"1","commission":"0.001","commissionAsset":"USDT"}]}
				""");

		assertThat(adapter.placeOrder(credential, marketBuy("BTCUSDT", "2")).status())
				.isEqualTo(LiveOrderStatus.PARTIALLY_FILLED);
	}

	@Test
	void canceledAndPendingCancelBothMapToCancelled() {
		for (String raw : List.of("CANCELED", "PENDING_CANCEL")) {
			exchange.on("/api/v3/order", """
					{"symbol":"BTCUSDT","orderId":6,"clientOrderId":"SB-testclientorderid01",
					 "status":"%s","origQty":"1.00000000","executedQty":"0.00000000",
					 "price":"0.00000000","cummulativeQuoteQty":"0.00000000"}
					""".formatted(raw));

			assertThat(adapter.placeOrder(credential, marketBuy("BTCUSDT", "1")).status())
					.as("status %s", raw)
					.isEqualTo(LiveOrderStatus.CANCELLED);
		}
	}

	@Test
	void anUnrecognisedStatusBecomesUnknownRatherThanAFill() {
		exchange.on("/api/v3/order", """
				{"symbol":"BTCUSDT","orderId":7,"clientOrderId":"SB-testclientorderid01",
				 "status":"SOMETHING_NEW","origQty":"1.00000000","executedQty":"1.00000000",
				 "price":"0.00000000","cummulativeQuoteQty":"50000.00000000"}
				""");

		assertThat(adapter.placeOrder(credential, marketBuy("BTCUSDT", "1")).status())
				.as("an unknown exchange status must not be mapped to FILLED")
				.isEqualTo(LiveOrderStatus.UNKNOWN);
	}

	@Test
	void aResponseWithoutAStatusFieldBecomesUnknown() {
		exchange.on("/api/v3/order", """
				{"symbol":"BTCUSDT","orderId":8,"clientOrderId":"SB-testclientorderid01",
				 "origQty":"1.00000000","executedQty":"1.00000000"}
				""");

		assertThat(adapter.placeOrder(credential, marketBuy("BTCUSDT", "1")).status())
				.isEqualTo(LiveOrderStatus.UNKNOWN);
	}

	@Test
	void theOrderIdIsCarriedThroughForReconciliation() {
		exchange.on("/api/v3/order", filledSpotJson());

		// The exchange order id is what lets a later reconciliation query correlate a
		// local order with the exchange's own record.
		assertThat(adapter.placeOrder(credential, marketBuy("BTCUSDT", "1")).exchangeOrderId())
				.isEqualTo("123456");
	}

	@Test
	void averageFillPriceIsDerivedFromCumulativeQuoteAndExecuted() {
		exchange.on("/api/v3/order", filledSpotJson());

		assertThat(adapter.placeOrder(credential, marketBuy("BTCUSDT", "1")).avgFillPrice())
				.isEqualByComparingTo("50000");
	}

	@Test
	void anUnfilledOrderHasNoAverageFillPrice() {
		exchange.on("/api/v3/order", """
				{"symbol":"BTCUSDT","orderId":9,"clientOrderId":"SB-testclientorderid01",
				 "status":"NEW","origQty":"1.00000000","executedQty":"0.00000000",
				 "price":"49000.00000000","cummulativeQuoteQty":"0.00000000"}
				""");

		assertThat(adapter.placeOrder(credential, marketBuy("BTCUSDT", "1")).avgFillPrice())
				.as("no fill means no average price, not a fabricated zero")
				.isNull();
	}

	// ============================================================== F. rejection

	@Test
	void aRejectedOrderIsSurfacedAsAnAdapterException() {
		exchange.on("/api/v3/order", 400,
				"{\"code\":-2010,\"msg\":\"Account has insufficient balance.\"}");

		ExchangeAdapterException thrown = org.junit.jupiter.api.Assertions.assertThrows(
				ExchangeAdapterException.class,
				() -> adapter.placeOrder(credential, marketBuy("BTCUSDT", "1")));

		assertThat(thrown.exchangeCode())
				.as("the exchange code drives the retry decision, so it must survive")
				.isEqualTo(-2010);
		assertThat(thrown.httpStatus()).isEqualTo(400);
		assertThat(thrown.retryable())
				.as("a rejection is a definite answer; retrying would be refused again")
				.isFalse();
	}

	@Test
	void aRateLimitIsMarkedRetryable() {
		exchange.on("/api/v3/order", 429, "{\"code\":-1003,\"msg\":\"Too many requests.\"}");

		ExchangeAdapterException thrown = org.junit.jupiter.api.Assertions.assertThrows(
				ExchangeAdapterException.class,
				() -> adapter.placeOrder(credential, marketBuy("BTCUSDT", "1")));

		assertThat(thrown.retryable()).isTrue();
		assertThat(thrown.httpStatus()).isEqualTo(429);
	}

	@Test
	void aServerErrorIsMarkedRetryable() {
		exchange.on("/api/v3/order", 503, "{\"code\":-1001,\"msg\":\"Internal error.\"}");

		assertThat(org.junit.jupiter.api.Assertions.assertThrows(
				ExchangeAdapterException.class,
				() -> adapter.placeOrder(credential, marketBuy("BTCUSDT", "1"))).retryable())
				.isTrue();
	}

	@Test
	void anAdapterFailureMessageNeverCarriesTheCredential() {
		exchange.on("/api/v3/order", 400, "{\"code\":-1121,\"msg\":\"Invalid symbol.\"}");

		ExchangeAdapterException thrown = org.junit.jupiter.api.Assertions.assertThrows(
				ExchangeAdapterException.class,
				() -> adapter.placeOrder(credential, marketBuy("BTCUSDT", "1")));

		assertThat(String.valueOf(thrown.getMessage()))
				.doesNotContain(TEST_API_KEY)
				.doesNotContain(TEST_API_SECRET)
				.doesNotContain("signature");
	}

	// =================================================== L. unknown transport outcome

@Test
	void aSlowExchangeProducesAnAmbiguousFailureRatherThanAFabricatedFill() {
		// The request may or may not have reached the exchange before the read
		// timeout expires. This is precisely the outcome that must never become a
		// fill and must never trigger an automatic resubmission.
		exchange.onSlow("/api/v3/order", TimeUnit.SECONDS.toMillis(30));

		ExchangeAdapterException thrown = org.junit.jupiter.api.Assertions.assertThrows(
				ExchangeAdapterException.class,
				() -> adapter.placeOrder(credential, marketBuy("BTCUSDT", "1")));

		assertThat(thrown)
				.as("a timeout leaves the exchange outcome unestablished")
				.isNotNull();
		// The request was genuinely attempted, so reconciliation can query by
		// clientOrderId; the adapter did not swallow the attempt.
		assertThat(exchange.requestCount()).isEqualTo(1);
	}

	@Test
	void anAmbiguousTransportFailureIsMarkedRetryableSoReconciliationRuns() {
		// A 5xx means the exchange may have received the order, so the outcome is
		// unknown. The caller sees UNKNOWN and reconciles rather than resubmitting.
		exchange.on("/api/v3/order", 500, "{\"code\":-1001,\"msg\":\"Internal error.\"}");

		ExchangeAdapterException thrown = org.junit.jupiter.api.Assertions.assertThrows(
				ExchangeAdapterException.class,
				() -> adapter.placeOrder(credential, marketBuy("BTCUSDT", "1")));

		assertThat(thrown.retryable())
				.as("retryable here means 'reconcile', never 'resend'")
				.isTrue();
		assertThat(exchange.requestCount())
				.as("the adapter must not retry on its own")
				.isEqualTo(1);
	}

	@Test
	void anEmptyResponseBodyNeverBecomesAFill() {
		exchange.on("/api/v3/order", 0, "");

		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> adapter.placeOrder(credential, marketBuy("BTCUSDT", "1")))
				.isInstanceOf(Exception.class);
	}

	@Test
	void aMalformedJsonBodyDoesNotProduceAFill() {
		exchange.on("/api/v3/order", "not json at all");

		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> adapter.placeOrder(credential, marketBuy("BTCUSDT", "1")))
				.isInstanceOf(Exception.class);
	}

	// ============================================= Y/Z. fee handling (defect 2)

	@Test
	void realCommissionFromFillsIsSummedIntoTheFee() {
		exchange.on("/api/v3/order", filledSpotJson());

		ExchangeOrderResult result = adapter.placeOrder(credential, marketBuy("BTCUSDT", "1"));

		assertThat(result.fee())
				.as("FULL response carries real commission; it must not be discarded")
				.isEqualByComparingTo("0.001");
		assertThat(result.feeAsset()).isEqualTo("USDT");
	}

	@Test
	void multipleFillsHaveTheirCommissionSummed() {
		exchange.on("/api/v3/order", """
				{"symbol":"BTCUSDT","orderId":10,"clientOrderId":"SB-testclientorderid01",
				 "status":"FILLED","origQty":"2.00000000","executedQty":"2.00000000",
				 "price":"0.00000000","cummulativeQuoteQty":"100000.00000000",
				 "fills":[{"price":"50000","qty":"1","commission":"0.001","commissionAsset":"USDT"},
				          {"price":"50000","qty":"1","commission":"0.002","commissionAsset":"USDT"}]}
				""");

		assertThat(adapter.placeOrder(credential, marketBuy("BTCUSDT", "2")).fee())
				.isEqualByComparingTo("0.003");
	}

	@Test
	void anAbsentFillsArrayLeavesTheFeeUnknownRatherThanZero() {
		exchange.on("/api/v3/order", """
				{"symbol":"BTCUSDT","orderId":11,"clientOrderId":"SB-testclientorderid01",
				 "status":"NEW","origQty":"1.00000000","executedQty":"0.00000000",
				 "price":"49000.00000000","cummulativeQuoteQty":"0.00000000"}
				""");

		ExchangeOrderResult result = adapter.placeOrder(credential, marketBuy("BTCUSDT", "1"));

		// A zero fee is indistinguishable from "Binance charged nothing", which is
		// never true. Unknown must stay unknown until authoritative fill data arrives.
		assertThat(result.fee())
				.as("an absent fee must not be fabricated as zero")
				.isNull();
		assertThat(result.feeAsset()).isNull();
	}

	// ================================================= D/I. exchange filter boundary

	@Test
	void quantityRoundingNeverIncreasesRiskPosition() {
		com.shyblack.cryptosignals.exchange.SymbolRules rules = new com.shyblack.cryptosignals.exchange.SymbolRules(
				"BTCUSDT", new BigDecimal("0.00001"), new BigDecimal("1000"),
				new BigDecimal("0.001"), new BigDecimal("0.01"), new BigDecimal("1000000"),
				new BigDecimal("0.01"), new BigDecimal("10"));

		// Rounds DOWN: 0.0015 -> 0.001, never 0.002.
		assertThat(rules.normalizeQuantity(new BigDecimal("0.0015")))
				.isEqualByComparingTo("0.001");
		assertThat(rules.normalizeQuantity(new BigDecimal("0.0019")))
				.as("rounding up would buy more than the risk calculation allowed")
				.isEqualByComparingTo("0.001");
		assertThat(rules.normalizeQuantity(new BigDecimal("1.0009")))
				.isEqualByComparingTo("1.000");
	}

	@Test
	void aQuantityBelowTheExchangeMinimumIsDetectable() {
		com.shyblack.cryptosignals.exchange.SymbolRules rules = new com.shyblack.cryptosignals.exchange.SymbolRules(
				"BTCUSDT", new BigDecimal("0.00001"), new BigDecimal("1000"),
				new BigDecimal("0.001"), new BigDecimal("0.01"), new BigDecimal("1000000"),
				new BigDecimal("0.01"), new BigDecimal("10"));

		assertThat(rules.meetsMinQty(new BigDecimal("0.00001"))).isTrue();
		assertThat(rules.meetsMinQty(new BigDecimal("0.000001")))
				.as("below LOT_SIZE minQty the exchange would reject the order")
				.isFalse();
		assertThat(rules.meetsMinQty(null)).isFalse();
	}

	@Test
	void aNotionalBelowTheExchangeMinimumIsDetectable() {
		com.shyblack.cryptosignals.exchange.SymbolRules rules = new com.shyblack.cryptosignals.exchange.SymbolRules(
				"BTCUSDT", new BigDecimal("0.00001"), new BigDecimal("1000"),
				new BigDecimal("0.001"), new BigDecimal("0.01"), new BigDecimal("1000000"),
				new BigDecimal("0.01"), new BigDecimal("10"));

		assertThat(rules.meetsMinNotional(new BigDecimal("0.001"), new BigDecimal("20000")))
				.isTrue();
		assertThat(rules.meetsMinNotional(new BigDecimal("0.0001"), new BigDecimal("20000")))
				.as("notional 2.00 is below MIN_NOTIONAL 10")
				.isFalse();
	}

	@Test
	void priceRoundingFollowsTheExchangeTickSizeDeterministically() {
		com.shyblack.cryptosignals.exchange.SymbolRules rules = new com.shyblack.cryptosignals.exchange.SymbolRules(
				"BTCUSDT", new BigDecimal("0.00001"), new BigDecimal("1000"),
				new BigDecimal("0.001"), new BigDecimal("0.01"), new BigDecimal("1000000"),
				new BigDecimal("0.01"), new BigDecimal("10"));

		// Same input always yields the same price, whatever the path that produced it.
		assertThat(rules.normalizePrice(new BigDecimal("49000.004"))).isEqualByComparingTo("49000.00");
		assertThat(rules.normalizePrice(new BigDecimal("49000.004")))
				.isEqualTo(rules.normalizePrice(new BigDecimal("49000.004")));
		assertThat(rules.normalizePrice(new BigDecimal("49000.016"))).isEqualByComparingTo("49000.02");
	}

	@Test
	void aNonPositiveQuantityNormalizesToZeroRatherThanAPositiveSize() {
		com.shyblack.cryptosignals.exchange.SymbolRules rules = new com.shyblack.cryptosignals.exchange.SymbolRules(
				"BTCUSDT", new BigDecimal("0.00001"), new BigDecimal("1000"),
				new BigDecimal("0.001"), new BigDecimal("0.01"), new BigDecimal("1000000"),
				new BigDecimal("0.01"), new BigDecimal("10"));

		assertThat(rules.normalizeQuantity(BigDecimal.ZERO)).isEqualByComparingTo("0");
		assertThat(rules.normalizeQuantity(new BigDecimal("-1"))).isEqualByComparingTo("0");
		assertThat(rules.normalizeQuantity(null)).isEqualByComparingTo("0");
	}
}