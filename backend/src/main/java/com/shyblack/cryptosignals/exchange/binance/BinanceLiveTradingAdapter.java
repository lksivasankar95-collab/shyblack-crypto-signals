package com.shyblack.cryptosignals.exchange.binance;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shyblack.cryptosignals.config.LiveTradingProperties;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.LiveOrderStatus;
import com.shyblack.cryptosignals.entity.enums.LiveOrderType;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.exchange.ExchangeAccountSnapshot;
import com.shyblack.cryptosignals.exchange.ExchangeAdapterException;
import com.shyblack.cryptosignals.exchange.ExchangeAssetBalance;
import com.shyblack.cryptosignals.exchange.ExchangeBalances;
import com.shyblack.cryptosignals.exchange.ExchangeOrderResult;
import com.shyblack.cryptosignals.exchange.ExchangeOrderSnapshot;
import com.shyblack.cryptosignals.exchange.ExchangeTradeSnapshot;
import com.shyblack.cryptosignals.exchange.ExchangeTradingAdapter;
import com.shyblack.cryptosignals.exchange.PlaceOrderRequest;
import com.shyblack.cryptosignals.exchange.SymbolRules;
import com.shyblack.cryptosignals.security.ExchangeCredentialEncryptor;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Real Binance spot signed adapter. Uses HMAC-SHA256 request signing.
 *
 * Base URL defaults to {@code testnet.binance.vision} — production traffic
 * requires an explicit operator override. Credentials are decrypted only
 * inside this class; the plaintext secret never leaves an HMAC computation
 * scope and is never logged.
 *
 * Only active when {@code app.live-trading.mode=EXCHANGE}. Registration is
 * handled by {@code LiveTradingConfig} rather than a class-level
 * {@code @ConditionalOnProperty} so unit tests can still instantiate the
 * class explicitly if they need to.
 */
@RequiredArgsConstructor
public class BinanceLiveTradingAdapter implements ExchangeTradingAdapter {

	private static final Logger log = LoggerFactory.getLogger(BinanceLiveTradingAdapter.class);

	/** Quote asset the trading engine summarises. Read-only summaries are per-asset. */
	private static final String QUOTE_ASSET = "USDT";

	private final LiveTradingProperties props;
	private final ExchangeCredentialEncryptor encryptor;
	private final RestClient rest = RestClient.builder()
			.requestFactory(com.shyblack.cryptosignals.config.HttpClientFactory.withDefaultTimeouts())
			.build();

	/** exchangeInfo cache — refreshed lazily on first miss. */
	private final Map<String, SymbolRules> rulesCache = new ConcurrentHashMap<>();

	@Override
	public ExchangeName exchange() {
		return ExchangeName.BINANCE;
	}

	@Override
	public ExchangeAccountSnapshot validateCredentials(ExchangeCredential credential) {
		return getAccountBalance(credential);
	}

	@Override
	public ExchangeAccountSnapshot getAccountBalance(ExchangeCredential credential) {
		ExchangeBalances balances = getBalances(credential);
		// Narrow to the configured quote asset. When the exchange did not report it at all the
		// balances stay null: an absent asset is missing data, not a zero balance.
		BigDecimal free = balances.free(QUOTE_ASSET);
		BigDecimal total = balances.total(QUOTE_ASSET);
		return new ExchangeAccountSnapshot(
				QUOTE_ASSET,
				free,
				total,
				balances.canTrade(),
				balances.fetchedAt());
	}

	@Override
	public ExchangeBalances getBalances(ExchangeCredential credential) {
		String body = signedGet(credential, "/api/v3/account", new LinkedHashMap<>());
		JsonObject json = JsonParser.parseString(body).getAsJsonObject();
		boolean canTrade = json.has("canTrade") && json.get("canTrade").getAsBoolean();
		Map<String, ExchangeAssetBalance> parsed = new LinkedHashMap<>();
		JsonArray balances = json.getAsJsonArray("balances");
		if (balances == null) {
			throw new ExchangeAdapterException(
					"Malformed /api/v3/account response: missing balances array", null, false, 200, null);
		}
		for (JsonElement el : balances) {
			JsonObject b = el.getAsJsonObject();
			String asset = b.get("asset").getAsString().toUpperCase();
			parsed.put(asset, new ExchangeAssetBalance(
					asset,
					decimal(b, "free", asset),
					decimal(b, "locked", asset)));
		}
		return new ExchangeBalances(parsed, canTrade, Instant.now());
	}

	/** Strict numeric read: an unusable value is malformed data, not a silent zero. */
	private static BigDecimal decimal(JsonObject json, String key, String asset) {
		JsonElement el = json.get(key);
		if (el == null || el.isJsonNull()) {
			throw new ExchangeAdapterException(
					"Malformed balance for asset " + asset + ": missing " + key, null, false, 200, null);
		}
		try {
			return new BigDecimal(el.getAsString());
		} catch (NumberFormatException nfe) {
			throw new ExchangeAdapterException(
					"Malformed balance for asset " + asset + ": " + key + " is not numeric",
					nfe, false, 200, null);
		}
	}

	@Override
	public SymbolRules getSymbolRules(String symbol) {
		SymbolRules cached = rulesCache.get(symbol.toUpperCase());
		if (cached != null) return cached;
		refreshSymbol(symbol.toUpperCase());
		return rulesCache.get(symbol.toUpperCase());
	}

	private void refreshSymbol(String symbol) {
		try {
			String url = props.spotRestBaseUrl() + "/api/v3/exchangeInfo?symbol=" + symbol;
			String body = rest.get().uri(url).retrieve().body(String.class);
			JsonObject json = JsonParser.parseString(body).getAsJsonObject();
			JsonArray symbols = json.getAsJsonArray("symbols");
			for (JsonElement el : symbols) {
				JsonObject s = el.getAsJsonObject();
				if (!symbol.equals(s.get("symbol").getAsString())) continue;
				SymbolRules parsed = parseFilters(symbol, s.getAsJsonArray("filters"));
				rulesCache.put(symbol, parsed);
				return;
			}
		} catch (Exception ex) {
			throw new ExchangeAdapterException("Failed to fetch exchangeInfo for " + symbol,
					ex, true, null, null);
		}
	}

	private static SymbolRules parseFilters(String symbol, JsonArray filters) {
		BigDecimal minQty = null, maxQty = null, stepSize = null;
		BigDecimal minPrice = null, maxPrice = null, tickSize = null;
		BigDecimal minNotional = null;
		for (JsonElement el : filters) {
			JsonObject f = el.getAsJsonObject();
			switch (f.get("filterType").getAsString()) {
				case "LOT_SIZE" -> {
					minQty = new BigDecimal(f.get("minQty").getAsString());
					maxQty = new BigDecimal(f.get("maxQty").getAsString());
					stepSize = new BigDecimal(f.get("stepSize").getAsString());
				}
				case "PRICE_FILTER" -> {
					minPrice = new BigDecimal(f.get("minPrice").getAsString());
					maxPrice = new BigDecimal(f.get("maxPrice").getAsString());
					tickSize = new BigDecimal(f.get("tickSize").getAsString());
				}
				case "MIN_NOTIONAL", "NOTIONAL" -> {
					JsonElement mn = f.has("minNotional") ? f.get("minNotional") : f.get("notional");
					if (mn != null && !mn.isJsonNull()) minNotional = new BigDecimal(mn.getAsString());
				}
				default -> { /* ignore */ }
			}
		}
		return new SymbolRules(symbol, minQty, maxQty, stepSize, minPrice, maxPrice, tickSize, minNotional);
	}

	@Override
	public ExchangeOrderResult placeOrder(ExchangeCredential credential, PlaceOrderRequest request) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("symbol", request.symbol());
		params.put("side", spotSide(request.side()));
		params.put("type", binanceType(request.type()));
		params.put("newClientOrderId", request.clientOrderId());
		params.put("quantity", request.quantity().stripTrailingZeros().toPlainString());
		if (request.price() != null && request.type() != LiveOrderType.MARKET) {
			params.put("price", request.price().stripTrailingZeros().toPlainString());
			params.put("timeInForce", "GTC");
		}
		if (request.stopPrice() != null) {
			params.put("stopPrice", request.stopPrice().stripTrailingZeros().toPlainString());
		}
		params.put("newOrderRespType", "FULL");

		String body = signedRequest(credential, HttpMethod.POST, "/api/v3/order", params);
		return parseOrderResponse(request, body);
	}

	@Override
	public ExchangeOrderResult getOrder(ExchangeCredential credential, String symbol, String clientOrderId) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("symbol", symbol);
		params.put("origClientOrderId", clientOrderId);
		String body;
		try {
			body = signedGet(credential, "/api/v3/order", params);
		} catch (ExchangeAdapterException notFound) {
			// -2013 = Order does not exist. Treat as null so reconciliation can act.
			if (notFound.exchangeCode() != null && notFound.exchangeCode() == -2013) return null;
			throw notFound;
		}
		return parseOrderResponse(new PlaceOrderRequest(
				symbol, null, null, BigDecimal.ZERO, null, null, clientOrderId), body);
	}

	@Override
	public ExchangeOrderResult cancelOrder(ExchangeCredential credential, String symbol, String clientOrderId) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("symbol", symbol);
		params.put("origClientOrderId", clientOrderId);
		String body = signedRequest(credential, HttpMethod.DELETE, "/api/v3/order", params);
		return parseOrderResponse(new PlaceOrderRequest(
				symbol, null, null, BigDecimal.ZERO, null, null, clientOrderId), body);
	}

	@Override
	public List<ExchangeOrderSnapshot> getOpenOrders(ExchangeCredential credential, String symbol) {
		Map<String, String> params = new LinkedHashMap<>();
		if (symbol != null && !symbol.isBlank()) {
			params.put("symbol", symbol.toUpperCase());
		}
		return parseOrders(signedGet(credential, "/api/v3/openOrders", params));
	}

	@Override
	public List<ExchangeOrderSnapshot> getAllOrders(
			ExchangeCredential credential, String symbol, Instant from, Instant to, int limit) {
		requireSymbol(symbol, "all-orders");
		Map<String, String> params = new LinkedHashMap<>();
		params.put("symbol", symbol.toUpperCase());
		putIfPresent(params, "startTime", from);
		putIfPresent(params, "endTime", to);
		params.put("limit", Integer.toString(limit));
		return parseOrders(signedGet(credential, "/api/v3/allOrders", params));
	}

	@Override
	public List<ExchangeTradeSnapshot> getTrades(
			ExchangeCredential credential, String symbol, Instant from, Instant to, int limit) {
		requireSymbol(symbol, "trade-history");
		Map<String, String> params = new LinkedHashMap<>();
		params.put("symbol", symbol.toUpperCase());
		putIfPresent(params, "startTime", from);
		putIfPresent(params, "endTime", to);
		params.put("limit", Integer.toString(limit));
		return parseTrades(signedGet(credential, "/api/v3/myTrades", params));
	}

	/**
	 * The exchange requires a symbol for these endpoints, so a missing one is rejected explicitly
	 * rather than being silently widened into a cross-symbol query.
	 */
	private static void requireSymbol(String symbol, String what) {
		if (symbol == null || symbol.isBlank()) {
			throw new ExchangeAdapterException(
					"A symbol is required for the exchange " + what + " endpoint",
					null, false, null, null);
		}
	}

	private static void putIfPresent(Map<String, String> params, String key, Instant value) {
		if (value != null) {
			params.put(key, Long.toString(value.toEpochMilli()));
		}
	}

	private static List<ExchangeOrderSnapshot> parseOrders(String body) {
		JsonArray array = JsonParser.parseString(body).getAsJsonArray();
		List<ExchangeOrderSnapshot> orders = new ArrayList<>(array.size());
		for (JsonElement element : array) {
			JsonObject o = element.getAsJsonObject();
			orders.add(new ExchangeOrderSnapshot(
					str(o, "symbol"),
					longOrNull(o, "orderId"),
					str(o, "clientOrderId"),
					str(o, "side"),
					str(o, "type"),
					str(o, "status"),
					dec(o, "price"),
					dec(o, "origQty"),
					dec(o, "executedQty"),
					dec(o, "cummulativeQuoteQty"),
					millis(o, "time"),
					millis(o, "updateTime")));
		}
		return List.copyOf(orders);
	}

	private static List<ExchangeTradeSnapshot> parseTrades(String body) {
		JsonArray array = JsonParser.parseString(body).getAsJsonArray();
		List<ExchangeTradeSnapshot> trades = new ArrayList<>(array.size());
		for (JsonElement element : array) {
			JsonObject t = element.getAsJsonObject();
			trades.add(new ExchangeTradeSnapshot(
					str(t, "symbol"),
					longOrNull(t, "tradeId"),
					longOrNull(t, "orderId"),
					str(t, "side"),
					dec(t, "price"),
					dec(t, "qty"),
					// Spot myTrades omits the quote quantity; it stays null rather than derived.
					dec(t, "quoteQty"),
					dec(t, "commission"),
					str(t, "commissionAsset"),
					t.has("isMaker") && !t.get("isMaker").isJsonNull() ? t.get("isMaker").getAsBoolean() : null,
					millis(t, "time")));
		}
		return List.copyOf(trades);
	}

	private static String str(JsonObject json, String key) {
		JsonElement el = json.get(key);
		return el == null || el.isJsonNull() ? null : el.getAsString();
	}

	private static Long longOrNull(JsonObject json, String key) {
		JsonElement el = json.get(key);
		if (el == null || el.isJsonNull()) {
			return null;
		}
		try {
			return el.getAsLong();
		} catch (Exception ex) {
			return null;
		}
	}

	private static BigDecimal dec(JsonObject json, String key) {
		JsonElement el = json.get(key);
		if (el == null || el.isJsonNull()) {
			return null;
		}
		try {
			return new BigDecimal(el.getAsString());
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private static Instant millis(JsonObject json, String key) {
		Long value = longOrNull(json, key);
		return value == null ? null : Instant.ofEpochMilli(value);
	}

	// ------------------------------------------------------------------

	private String signedGet(ExchangeCredential credential, String path, Map<String, String> params) {
		return signedRequest(credential, HttpMethod.GET, path, params);
	}

	private String signedRequest(ExchangeCredential credential, HttpMethod method,
			String path, Map<String, String> params) {
		String apiKey = encryptor.decrypt(credential.getApiKey());
		String secret = encryptor.decrypt(credential.getApiSecret());
		try {
			params.put("timestamp", Long.toString(System.currentTimeMillis()));
			params.put("recvWindow", Long.toString(props.recvWindowMs()));
			String queryString = urlEncode(params);
			String signature = BinanceSignatureUtil.hmacSha256Hex(secret, queryString);

			String url = props.spotRestBaseUrl() + path + "?" + queryString + "&signature=" + signature;
			HttpHeaders headers = new HttpHeaders();
			headers.set("X-MBX-APIKEY", apiKey);

			try {
				return rest.method(method)
						.uri(url)
						.headers(h -> h.addAll(headers))
						.retrieve()
						.body(String.class);
			} catch (ResourceAccessException transport) {
				// A read timeout or connection reset leaves the exchange outcome
				// unestablished: the order may or may not exist. It is wrapped as a
				// retryable adapter failure so the execution service records UNKNOWN and
				// reconciles, rather than the raw transport exception escaping and the
				// order being left SUBMITTING with no recorded outcome.
				log.warn("[LiveAdapter] Binance {} {} transport failure: {}",
						method, path, transport.getClass().getSimpleName());
				throw new ExchangeAdapterException(
						"Exchange transport failure; the order outcome is unknown",
						transport, true, null, null);
			} catch (HttpStatusCodeException http) {
				Integer code = parseCode(http.getResponseBodyAsString());
				log.warn("[LiveAdapter] Binance {} {} -> HTTP {} code={} bodyLen={}",
						method, path, http.getStatusCode().value(), code,
						http.getResponseBodyAsString() == null ? 0 : http.getResponseBodyAsString().length());
				throw new ExchangeAdapterException(
						"Binance error " + http.getStatusCode(),
						http,
						http.getStatusCode().is5xxServerError() || http.getStatusCode().value() == 429,
						http.getStatusCode().value(),
						code);
			}
		} finally {
			// Best-effort: overwrite decrypted secret so it's no longer strongly held.
			apiKey = null;
			secret = null;
		}
	}

	private static Integer parseCode(String body) {
		if (body == null || body.isBlank()) return null;
		try {
			JsonObject json = JsonParser.parseString(body).getAsJsonObject();
			return json.has("code") ? json.get("code").getAsInt() : null;
		} catch (Exception ignore) {
			return null;
		}
	}

	private static String urlEncode(Map<String, String> params) {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> e : params.entrySet()) {
			if (sb.length() > 0) sb.append('&');
			sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8));
			sb.append('=');
			sb.append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
		}
		return sb.toString();
	}

	private static String binanceType(LiveOrderType type) {
		return switch (type) {
			case MARKET -> "MARKET";
			case LIMIT -> "LIMIT";
			case STOP_LOSS_LIMIT -> "STOP_LOSS_LIMIT";
			case TAKE_PROFIT_LIMIT -> "TAKE_PROFIT_LIMIT";
		};
	}

	/**
	 * Translates the application's {@link PositionSide} into Binance's spot vocabulary.
	 *
	 * <p>Binance spot accepts only {@code BUY} and {@code SELL}; it has no notion of a
	 * position side, because spot cannot be shorted. The internal enum is
	 * {@code {LONG, SHORT}}, so sending {@code side().name()} directly would produce
	 * {@code side=LONG} and be rejected with {@code -1102 Invalid side}.
	 *
	 * <p>Spot is long-only: the live engine refuses a SHORT signal before it reaches
	 * here, so SHORT maps to SELL for completeness and to keep a manual close or a
	 * future caller correct rather than silently producing an invalid order.
	 */
	private static String spotSide(PositionSide side) {
		if (side == null) {
			throw new ExchangeAdapterException(
					"An order side is required; the exchange accepts only BUY or SELL",
					null, false, null, null);
		}
		return side == PositionSide.LONG ? "BUY" : "SELL";
	}

	private static ExchangeOrderResult parseOrderResponse(PlaceOrderRequest ref, String body) {
		JsonObject json = JsonParser.parseString(body).getAsJsonObject();
		String status = json.has("status") ? json.get("status").getAsString() : "UNKNOWN";
		BigDecimal executed = json.has("executedQty")
				? new BigDecimal(json.get("executedQty").getAsString()) : BigDecimal.ZERO;
		BigDecimal cumQuote = json.has("cummulativeQuoteQty")
				? new BigDecimal(json.get("cummulativeQuoteQty").getAsString()) : BigDecimal.ZERO;
		BigDecimal avgFill = executed.signum() > 0
				? cumQuote.divide(executed, 8, java.math.RoundingMode.HALF_UP)
				: null;
		// Fee stays null unless the exchange actually reported commission. A zero fee
		// is indistinguishable from "Binance charged nothing", which is never true:
		// an absent fills[] means UNKNOWN, and null keeps that distinction until
		// authoritative fill data arrives.
		BigDecimal fee = null;
		String feeAsset = null;
		if (json.has("fills")) {
			BigDecimal summed = BigDecimal.ZERO;
			boolean sawCommission = false;
			for (JsonElement f : json.getAsJsonArray("fills")) {
				JsonObject fill = f.getAsJsonObject();
				if (fill.has("commission") && !fill.get("commission").isJsonNull()) {
					summed = summed.add(new BigDecimal(fill.get("commission").getAsString()));
					sawCommission = true;
				}
				if (fill.has("commissionAsset") && !fill.get("commissionAsset").isJsonNull()) {
					feeAsset = fill.get("commissionAsset").getAsString();
				}
			}
			if (sawCommission) {
				fee = summed;
			}
		}
		String exchOrderId = json.has("orderId") ? json.get("orderId").getAsString()
				: json.has("clientOrderId") ? json.get("clientOrderId").getAsString() : null;
		return new ExchangeOrderResult(
				exchOrderId,
				ref.clientOrderId(),
				ref.symbol(),
				mapStatus(status),
				ref.quantity(),
				executed,
				cumQuote,
				avgFill,
				fee,
				feeAsset,
				Instant.now(),
				status);
	}

	private static LiveOrderStatus mapStatus(String raw) {
		return switch (raw) {
			case "NEW" -> LiveOrderStatus.ACKNOWLEDGED;
			case "PARTIALLY_FILLED" -> LiveOrderStatus.PARTIALLY_FILLED;
			case "FILLED" -> LiveOrderStatus.FILLED;
			case "CANCELED", "PENDING_CANCEL" -> LiveOrderStatus.CANCELLED;
			case "REJECTED" -> LiveOrderStatus.REJECTED;
			case "EXPIRED" -> LiveOrderStatus.EXPIRED;
			default -> LiveOrderStatus.UNKNOWN;
		};
	}

	// Suppress unused-import warning for HttpEntity — kept as a hint for future
	// callers that may need to send a body for advanced order types.
	@SuppressWarnings("unused")
	private HttpEntity<Void> reserved() { return null; }
}
