package com.shyblack.cryptosignals.exchange.futures.binance;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shyblack.cryptosignals.config.FuturesTradingProperties;
import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderStatus;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderType;
import com.shyblack.cryptosignals.entity.enums.FuturesPositionMode;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.exchange.ExchangeAdapterException;
import com.shyblack.cryptosignals.exchange.SymbolRules;
import com.shyblack.cryptosignals.exchange.futures.FuturesAccountSnapshot;
import com.shyblack.cryptosignals.exchange.futures.FuturesExchangeAdapter;
import com.shyblack.cryptosignals.exchange.futures.FuturesExchangePosition;
import com.shyblack.cryptosignals.exchange.futures.FuturesOrderResult;
import com.shyblack.cryptosignals.exchange.futures.PlaceFuturesOrderRequest;
import com.shyblack.cryptosignals.security.ExchangeCredentialEncryptor;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

/**
 * Real Binance USDT-M FUTURES signed adapter. Testnet URL by default.
 *
 * Credentials are decrypted only for the HMAC computation and never logged.
 * All calls set {@code timestamp} + {@code recvWindow} and produce an
 * {@code X-MBX-APIKEY} header.
 */
@RequiredArgsConstructor
public class BinanceFuturesLiveAdapter implements FuturesExchangeAdapter {

	private static final Logger log = LoggerFactory.getLogger(BinanceFuturesLiveAdapter.class);

	private final FuturesTradingProperties props;
	private final ExchangeCredentialEncryptor encryptor;
	private final RestClient rest = RestClient.builder()
			.requestFactory(com.shyblack.cryptosignals.config.HttpClientFactory.withDefaultTimeouts())
			.build();
	private final Map<String, SymbolRules> rulesCache = new ConcurrentHashMap<>();

	@Override public ExchangeName exchange() { return ExchangeName.BINANCE; }

	@Override
	public FuturesAccountSnapshot validateCredentials(ExchangeCredential credential) {
		return getAccount(credential);
	}

	@Override
	public FuturesAccountSnapshot getAccount(ExchangeCredential credential) {
		String body = signedGet(credential, "/fapi/v2/account", new LinkedHashMap<>());
		JsonObject json = JsonParser.parseString(body).getAsJsonObject();
		BigDecimal wallet = requiredNum(json, "totalWalletBalance", "/fapi/v2/account");
		BigDecimal available = requiredNum(json, "availableBalance", "/fapi/v2/account");
		BigDecimal marginBalance = requiredNum(json, "totalMarginBalance", "/fapi/v2/account");
		BigDecimal usedMargin = requiredNum(json, "totalInitialMargin", "/fapi/v2/account");
		BigDecimal maint = requiredNum(json, "totalMaintMargin", "/fapi/v2/account");
		BigDecimal unreal = requiredNum(json, "totalUnrealizedProfit", "/fapi/v2/account");
		boolean canTrade = json.has("canTrade") && json.get("canTrade").getAsBoolean();

		// Position mode is a separate endpoint on Binance; probe it once.
		FuturesPositionMode positionMode = fetchPositionMode(credential);
		// Per-symbol margin mode now comes from the authoritative position list when a position is
		// open; an account-level aggregate has no single margin mode, so it stays null rather than
		// being hardcoded to ISOLATED.
		FuturesMarginMode accountMarginMode = null;
		return new FuturesAccountSnapshot(
				"USDT", wallet, available, marginBalance, usedMargin, maint, unreal,
				positionMode, accountMarginMode, canTrade, Instant.now());
	}

	@Override
	public List<FuturesExchangePosition> getPositions(ExchangeCredential credential) {
		String body = signedGet(credential, "/fapi/v2/positionRisk", new LinkedHashMap<>());
		JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
		List<FuturesExchangePosition> positions = new ArrayList<>(arr.size());
		for (JsonElement el : arr) {
			JsonObject p = el.getAsJsonObject();
			String symbol = p.get("symbol").getAsString().toUpperCase();
			BigDecimal amount = optionalNum(p, "positionAmt");
			// A flat position is a real closed state on the exchange, not an open one.
			if (amount == null || amount.signum() == 0) {
				continue;
			}
			positions.add(new FuturesExchangePosition(
					symbol,
					amount.signum() > 0 ? PositionSide.LONG : PositionSide.SHORT,
					amount,
					optionalNum(p, "entryPrice"),
					optionalNum(p, "markPrice"),
					zeroToNull(optionalNum(p, "liquidationPrice")),
					optionalInt(p, "leverage"),
					marginMode(p.get("marginType") == null ? null : p.get("marginType").getAsString()),
					optionalNum(p, "isolatedMargin"),
					optionalNum(p, "notional"),
					optionalNum(p, "unRealizedProfit"),
					Instant.now()));
		}
		return List.copyOf(positions);
	}

	/**
	 * Binance reports "0" for a liquidation price that is not applicable, which would otherwise be
	 * indistinguishable from a genuine zero. Normalise that one sentinel to null.
	 */
	private static BigDecimal zeroToNull(BigDecimal value) {
		return value == null || value.signum() == 0 ? null : value;
	}

	private static FuturesMarginMode marginMode(String raw) {
		if (raw == null) {
			return null;
		}
		return switch (raw.toUpperCase()) {
			case "ISOLATED", "ISOLATED_MARGIN" -> FuturesMarginMode.ISOLATED;
			case "CROSSED", "CROSS", "CROSSED_MARGIN" -> FuturesMarginMode.CROSS;
			default -> null;
		};
	}

	private FuturesPositionMode fetchPositionMode(ExchangeCredential credential) {
		try {
			String body = signedGet(credential, "/fapi/v1/positionSide/dual", new LinkedHashMap<>());
			JsonObject json = JsonParser.parseString(body).getAsJsonObject();
			boolean dual = json.has("dualSidePosition") && json.get("dualSidePosition").getAsBoolean();
			return dual ? FuturesPositionMode.HEDGE : FuturesPositionMode.ONE_WAY;
		} catch (Exception ex) {
			log.warn("[FutAdapter] position mode probe failed — defaulting ONE_WAY: {}", ex.getMessage());
			return FuturesPositionMode.ONE_WAY;
		}
	}

	@Override
	public void setLeverage(ExchangeCredential credential, String symbol, int leverage) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("symbol", symbol);
		params.put("leverage", Integer.toString(leverage));
		signedRequest(credential, HttpMethod.POST, "/fapi/v1/leverage", params);
	}

	@Override
	public void setMarginMode(ExchangeCredential credential, String symbol, FuturesMarginMode marginMode) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("symbol", symbol);
		params.put("marginType", marginMode.name());
		try {
			signedRequest(credential, HttpMethod.POST, "/fapi/v1/marginType", params);
		} catch (ExchangeAdapterException ex) {
			// -4046 = "No need to change margin type" — already set. Not an error.
			if (ex.exchangeCode() != null && ex.exchangeCode() == -4046) return;
			throw ex;
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
			String url = props.restBaseUrl() + "/fapi/v1/exchangeInfo";
			String body = rest.get().uri(url).retrieve().body(String.class);
			JsonObject json = JsonParser.parseString(body).getAsJsonObject();
			for (JsonElement el : json.getAsJsonArray("symbols")) {
				JsonObject s = el.getAsJsonObject();
				if (!symbol.equals(s.get("symbol").getAsString())) continue;
				rulesCache.put(symbol, parseFilters(symbol, s.getAsJsonArray("filters")));
				return;
			}
		} catch (Exception ex) {
			throw new ExchangeAdapterException("Failed to fetch Futures exchangeInfo for " + symbol,
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
				case "LOT_SIZE", "MARKET_LOT_SIZE" -> {
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
					JsonElement mn = f.has("notional") ? f.get("notional") : f.get("minNotional");
					if (mn != null && !mn.isJsonNull()) minNotional = new BigDecimal(mn.getAsString());
				}
				default -> { /* ignore */ }
			}
		}
		return new SymbolRules(symbol, minQty, maxQty, stepSize, minPrice, maxPrice, tickSize, minNotional);
	}

	@Override
	public FuturesOrderResult placeOrder(ExchangeCredential credential, PlaceFuturesOrderRequest req) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("symbol", req.symbol());
		params.put("side", req.side() == PositionSide.LONG ? "BUY" : "SELL");
		params.put("type", binanceType(req.type()));
		params.put("newClientOrderId", req.clientOrderId());
		params.put("quantity", req.quantity().stripTrailingZeros().toPlainString());
		if (req.reduceOnly()) params.put("reduceOnly", "true");
		if (req.stopPrice() != null) params.put("stopPrice", req.stopPrice().stripTrailingZeros().toPlainString());
		// ONE_WAY mode: don't send positionSide param (Binance defaults to BOTH).
		params.put("newOrderRespType", "RESULT");

		String body = signedRequest(credential, HttpMethod.POST, "/fapi/v1/order", params);
		return parse(req, body);
	}

	@Override
	public FuturesOrderResult getOrder(ExchangeCredential credential, String symbol, String clientOrderId) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("symbol", symbol);
		params.put("origClientOrderId", clientOrderId);
		String body;
		try {
			body = signedGet(credential, "/fapi/v1/order", params);
		} catch (ExchangeAdapterException notFound) {
			if (notFound.exchangeCode() != null && notFound.exchangeCode() == -2013) return null;
			throw notFound;
		}
		return parse(new PlaceFuturesOrderRequest(
				symbol, null, null, null, false, BigDecimal.ZERO, null, null, clientOrderId), body);
	}

	@Override
	public FuturesOrderResult cancelOrder(ExchangeCredential credential, String symbol, String clientOrderId) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("symbol", symbol);
		params.put("origClientOrderId", clientOrderId);
		String body = signedRequest(credential, HttpMethod.DELETE, "/fapi/v1/order", params);
		return parse(new PlaceFuturesOrderRequest(
				symbol, null, null, null, false, BigDecimal.ZERO, null, null, clientOrderId), body);
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
			String signature = BinanceFuturesSignatureUtil.hmacSha256Hex(secret, queryString);
			String url = props.restBaseUrl() + path + "?" + queryString + "&signature=" + signature;
			HttpHeaders headers = new HttpHeaders();
			headers.set("X-MBX-APIKEY", apiKey);
			try {
				return rest.method(method).uri(url).headers(h -> h.addAll(headers))
						.retrieve().body(String.class);
			} catch (HttpStatusCodeException http) {
				Integer code = parseCode(http.getResponseBodyAsString());
				log.warn("[FutAdapter] Binance {} {} HTTP {} code={}",
						method, path, http.getStatusCode().value(), code);
				throw new ExchangeAdapterException(
						"Binance Futures error " + http.getStatusCode(),
						http,
						http.getStatusCode().is5xxServerError() || http.getStatusCode().value() == 429,
						http.getStatusCode().value(),
						code);
			}
		} finally {
			apiKey = null; secret = null;
		}
	}

	private static BigDecimal num(JsonObject json, String key) {
		return json.has(key) && !json.get(key).isJsonNull()
				? new BigDecimal(json.get(key).getAsString())
				: BigDecimal.ZERO;
	}

	/**
	 * Strict read for account totals: an absent or unusable field means the response is malformed.
	 * Falling back to zero here would report a real-looking balance that Binance never sent.
	 */
	private static BigDecimal requiredNum(JsonObject json, String key, String path) {
		BigDecimal value = optionalNum(json, key);
		if (value == null) {
			throw new ExchangeAdapterException(
					"Malformed " + path + " response: missing numeric field " + key, null, false, 200, null);
		}
		return value;
	}

	/** Null-preserving numeric read; an unusable value is malformed data, not zero. */
	private static BigDecimal optionalNum(JsonObject json, String key) {
		JsonElement el = json.get(key);
		if (el == null || el.isJsonNull()) {
			return null;
		}
		try {
			return new BigDecimal(el.getAsString());
		} catch (NumberFormatException nfe) {
			throw new ExchangeAdapterException(
					"Malformed exchange response: field " + key + " is not numeric",
					nfe, false, 200, null);
		}
	}

	private static Integer optionalInt(JsonObject json, String key) {
		JsonElement el = json.get(key);
		if (el == null || el.isJsonNull()) {
			return null;
		}
		try {
			return Integer.valueOf(el.getAsString());
		} catch (NumberFormatException nfe) {
			return null;
		}
	}

	private static Integer parseCode(String body) {
		if (body == null || body.isBlank()) return null;
		try {
			JsonObject json = JsonParser.parseString(body).getAsJsonObject();
			return json.has("code") ? json.get("code").getAsInt() : null;
		} catch (Exception ignore) { return null; }
	}

	private static String urlEncode(Map<String, String> params) {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> e : params.entrySet()) {
			if (sb.length() > 0) sb.append('&');
			sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)).append('=');
			sb.append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
		}
		return sb.toString();
	}

	private static String binanceType(FuturesOrderType type) {
		return switch (type) {
			case MARKET -> "MARKET";
			case LIMIT -> "LIMIT";
			case STOP_MARKET -> "STOP_MARKET";
			case TAKE_PROFIT_MARKET -> "TAKE_PROFIT_MARKET";
		};
	}

	private static FuturesOrderResult parse(PlaceFuturesOrderRequest req, String body) {
		JsonObject json = JsonParser.parseString(body).getAsJsonObject();
		String status = json.has("status") ? json.get("status").getAsString() : "UNKNOWN";
		BigDecimal executed = num(json, "executedQty");
		BigDecimal cumQuote = num(json, "cumQuote");
		BigDecimal avg = json.has("avgPrice") ? num(json, "avgPrice")
				: (executed.signum() > 0
						? cumQuote.divide(executed, 8, java.math.RoundingMode.HALF_UP) : null);
		BigDecimal fee = BigDecimal.ZERO;
		String feeAsset = null;
		// Binance Futures RESULT doesn't include fills[]; commission comes via user-data
		// stream (not implemented). For now, fees are set later by reconciliation.
		String exchOrderId = json.has("orderId") ? json.get("orderId").getAsString() : null;
		return new FuturesOrderResult(
				exchOrderId, req.clientOrderId(), req.symbol(),
				req.side(), req.positionSide(), mapStatus(status), req.reduceOnly(),
				req.quantity(), executed, cumQuote, avg, fee, feeAsset,
				Instant.now(), status);
	}

	private static FuturesOrderStatus mapStatus(String raw) {
		return switch (raw) {
			case "NEW" -> FuturesOrderStatus.ACKNOWLEDGED;
			case "PARTIALLY_FILLED" -> FuturesOrderStatus.PARTIALLY_FILLED;
			case "FILLED" -> FuturesOrderStatus.FILLED;
			case "CANCELED", "PENDING_CANCEL" -> FuturesOrderStatus.CANCELLED;
			case "REJECTED" -> FuturesOrderStatus.REJECTED;
			case "EXPIRED" -> FuturesOrderStatus.EXPIRED;
			default -> FuturesOrderStatus.UNKNOWN;
		};
	}
}
