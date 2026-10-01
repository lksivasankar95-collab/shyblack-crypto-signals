package com.shyblack.cryptosignals.market;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shyblack.cryptosignals.config.HttpClientFactory;
import com.shyblack.cryptosignals.config.MarketProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Read-only USDT-M futures derivatives data (spec §12–§15, §47). All calls are
 * public and every failure degrades to {@link Optional#empty()} — NFM must
 * never assume missing OI/funding/liquidation equals zero.
 */
@Component
public class BinanceFuturesDerivativesClient {

	private static final Logger log = LoggerFactory.getLogger(BinanceFuturesDerivativesClient.class);

	private final RestClient rest;

	public BinanceFuturesDerivativesClient(MarketProperties properties) {
		this.rest = RestClient.builder()
				.baseUrl(properties.futuresRestBaseUrl())
				.requestFactory(HttpClientFactory.withDefaultTimeouts())
				.build();
	}

	public record PremiumIndex(BigDecimal markPrice, BigDecimal indexPrice, BigDecimal lastFundingRate,
			Long nextFundingTime) {}

	public record OpenInterestPoint(long timestamp, BigDecimal openInterest, BigDecimal openInterestValue) {}

	public record LiquidationAggregate(BigDecimal longVolume, BigDecimal shortVolume) {}

	public Optional<PremiumIndex> premiumIndex(String symbol) {
		Optional<String> body = get("/fapi/v1/premiumIndex?symbol={symbol}", symbol);
		if (body.isEmpty()) {
			return Optional.empty();
		}
		try {
			JsonObject root = JsonParser.parseString(body.get()).getAsJsonObject();
			return Optional.of(new PremiumIndex(
					decimal(root, "markPrice"),
					decimal(root, "indexPrice"),
					decimal(root, "lastFundingRate"),
					root.has("nextFundingTime") ? root.get("nextFundingTime").getAsLong() : null));
		} catch (Exception ex) {
			log.debug("[NFM] premiumIndex parse failed symbol={} err={}", symbol, ex.getMessage());
			return Optional.empty();
		}
	}

	public Optional<BigDecimal> openInterest(String symbol) {
		Optional<String> body = get("/fapi/v1/openInterest?symbol={symbol}", symbol);
		if (body.isEmpty()) {
			return Optional.empty();
		}
		try {
			JsonObject root = JsonParser.parseString(body.get()).getAsJsonObject();
			return Optional.ofNullable(decimal(root, "openInterest"));
		} catch (Exception ex) {
			return Optional.empty();
		}
	}

	public List<OpenInterestPoint> openInterestHistory(String symbol, String period, int limit) {
		Optional<String> body = get("/futures/data/openInterestHist?symbol={symbol}&period={period}&limit={limit}",
				symbol, period, String.valueOf(limit));
		if (body.isEmpty()) {
			return List.of();
		}
		List<OpenInterestPoint> points = new ArrayList<>();
		try {
			JsonArray array = JsonParser.parseString(body.get()).getAsJsonArray();
			for (JsonElement element : array) {
				JsonObject item = element.getAsJsonObject();
				points.add(new OpenInterestPoint(
						item.has("timestamp") ? item.get("timestamp").getAsLong() : 0L,
						decimal(item, "sumOpenInterest"),
						decimal(item, "sumOpenInterestValue")));
			}
		} catch (Exception ex) {
			log.debug("[NFM] openInterestHist parse failed symbol={} err={}", symbol, ex.getMessage());
		}
		return points;
	}

	/** Aggregated forced-order volume by side. Public and frequently unavailable — degrade gracefully. */
	public Optional<LiquidationAggregate> recentLiquidations(String symbol, int limit) {
		Optional<String> body = get("/fapi/v1/allForceOrders?symbol={symbol}&limit={limit}",
				symbol, String.valueOf(limit));
		if (body.isEmpty()) {
			return Optional.empty();
		}
		try {
			JsonArray array = JsonParser.parseString(body.get()).getAsJsonArray();
			BigDecimal longVol = BigDecimal.ZERO;
			BigDecimal shortVol = BigDecimal.ZERO;
			for (JsonElement element : array) {
				JsonObject item = element.getAsJsonObject();
				BigDecimal qty = decimal(item, "origQty");
				BigDecimal price = decimal(item, "price");
				if (qty == null || price == null) {
					continue;
				}
				BigDecimal notional = qty.multiply(price);
				String side = item.has("side") ? item.get("side").getAsString() : "";
				// A SELL forced order closes a LONG (long liquidation); BUY closes a SHORT.
				if ("SELL".equalsIgnoreCase(side)) {
					longVol = longVol.add(notional);
				} else if ("BUY".equalsIgnoreCase(side)) {
					shortVol = shortVol.add(notional);
				}
			}
			return Optional.of(new LiquidationAggregate(longVol, shortVol));
		} catch (Exception ex) {
			log.debug("[NFM] liquidation parse failed symbol={} err={}", symbol, ex.getMessage());
			return Optional.empty();
		}
	}

	private Optional<String> get(String uriTemplate, Object... uriVars) {
		try {
			String body = rest.get()
					.uri(uriTemplate, uriVars)
					.accept(MediaType.APPLICATION_JSON)
					.retrieve()
					.body(String.class);
			if (body == null || body.isBlank()) {
				return Optional.empty();
			}
			return Optional.of(body);
		} catch (RestClientException ex) {
			log.debug("[NFM] derivatives call failed uri={} err={}", uriTemplate, ex.getMessage());
			return Optional.empty();
		}
	}

	private static BigDecimal decimal(JsonObject obj, String field) {
		if (obj == null || !obj.has(field) || obj.get(field).isJsonNull()) {
			return null;
		}
		try {
			return new BigDecimal(obj.get(field).getAsString());
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	static BigDecimal pct(BigDecimal from, BigDecimal to) {
		if (from == null || to == null || from.signum() == 0) {
			return null;
		}
		return to.subtract(from)
				.divide(from, 8, RoundingMode.HALF_UP)
				.multiply(BigDecimal.valueOf(100))
				.setScale(6, RoundingMode.HALF_UP);
	}
}
