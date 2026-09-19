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
import com.shyblack.cryptosignals.exchange.ExchangeAccountSnapshot;
import com.shyblack.cryptosignals.exchange.ExchangeAdapterException;
import com.shyblack.cryptosignals.exchange.ExchangeOrderResult;
import com.shyblack.cryptosignals.exchange.ExchangeTradingAdapter;
import com.shyblack.cryptosignals.exchange.PlaceOrderRequest;
import com.shyblack.cryptosignals.exchange.SymbolRules;
import com.shyblack.cryptosignals.security.ExchangeCredentialEncryptor;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.HttpClientErrorException;
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

	private final LiveTradingProperties props;
	private final ExchangeCredentialEncryptor encryptor;
	private final RestClient rest = RestClient.builder().build();

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
		String body = signedGet(credential, "/api/v3/account", new LinkedHashMap<>());
		JsonObject json = JsonParser.parseString(body).getAsJsonObject();
		boolean canTrade = json.has("canTrade") && json.get("canTrade").getAsBoolean();
		BigDecimal totalFree = BigDecimal.ZERO;
		BigDecimal totalLocked = BigDecimal.ZERO;
		JsonArray balances = json.getAsJsonArray("balances");
		for (JsonElement el : balances) {
			JsonObject b = el.getAsJsonObject();
			if ("USDT".equals(b.get("asset").getAsString())) {
				totalFree = new BigDecimal(b.get("free").getAsString());
				totalLocked = new BigDecimal(b.get("locked").getAsString());
			}
		}
		return new ExchangeAccountSnapshot(
				"USDT",
				totalFree,
				totalFree.add(totalLocked),
				canTrade,
				Instant.now());
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
		params.put("side", request.side().name());
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
			} catch (HttpClientErrorException http) {
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
		BigDecimal fee = BigDecimal.ZERO;
		String feeAsset = null;
		if (json.has("fills")) {
			for (JsonElement f : json.getAsJsonArray("fills")) {
				JsonObject fill = f.getAsJsonObject();
				fee = fee.add(new BigDecimal(fill.get("commission").getAsString()));
				feeAsset = fill.get("commissionAsset").getAsString();
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
