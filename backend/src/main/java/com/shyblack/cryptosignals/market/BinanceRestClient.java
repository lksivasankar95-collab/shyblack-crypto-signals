package com.shyblack.cryptosignals.market;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shyblack.cryptosignals.config.MarketProperties;
import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.exception.MarketUpstreamException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class BinanceRestClient {

	private static final Logger log = LoggerFactory.getLogger(BinanceRestClient.class);

	private final RestClient rest;
	private final String quoteAsset;

	public BinanceRestClient(MarketProperties properties) {
		this.rest = RestClient.builder()
				.baseUrl(properties.restBaseUrl())
				.build();
		this.quoteAsset = properties.quoteAssetOrUsdt();
	}

	public Map<String, String> loadTradableUsdtSpotNames() {
		try {
			String body = rest.get()
					.uri("/api/v3/exchangeInfo")
					.accept(MediaType.APPLICATION_JSON)
					.retrieve()
					.body(String.class);
			Map<String, String> names = new LinkedHashMap<>();
			if (body == null || body.isBlank()) {
				return names;
			}
			JsonObject root = JsonParser.parseString(body).getAsJsonObject();
			JsonArray symbols = root.getAsJsonArray("symbols");
			if (symbols == null) {
				return names;
			}
			for (JsonElement element : symbols) {
				JsonObject item = element.getAsJsonObject();
				if (!"TRADING".equals(item.get("status").getAsString())) {
					continue;
				}
				if (!quoteAsset.equalsIgnoreCase(item.get("quoteAsset").getAsString())) {
					continue;
				}
				if (item.has("isSpotTradingAllowed") && !item.get("isSpotTradingAllowed").getAsBoolean()) {
					continue;
				}
				String symbol = item.get("symbol").getAsString();
				String base = item.get("baseAsset").getAsString();
				names.put(symbol, base);
			}
			log.info("Loaded {} Spot {} pairs from exchangeInfo", names.size(), quoteAsset);
			return names;
		} catch (RestClientException ex) {
			throw new MarketUpstreamException("Unable to load Spot exchangeInfo from Binance", ex);
		}
	}

	public List<MarketTicker> loadSpot24hTickers(UsdtSymbolDirectory directory) {
		try {
			String body = rest.get()
					.uri("/api/v3/ticker/24hr")
					.accept(MediaType.APPLICATION_JSON)
					.retrieve()
					.body(String.class);
			return parseRestTickers(body, directory);
		} catch (RestClientException ex) {
			throw new MarketUpstreamException("Unable to load Spot 24h tickers from Binance", ex);
		}
	}

	/** Binance's hard maximum candles per klines request. */
	public static final int MAX_KLINES_PER_REQUEST = 1000;

	public List<KlineResponse> klines(String symbol, String interval, int limit) {
		return klines(symbol, interval, null, null, limit);
	}

	/**
	 * Klines for {@code symbol}/{@code interval}, optionally bounded by an
	 * inclusive {@code [startTime, endTime]} epoch-millis window. The
	 * {@code limit} is clamped to {@link #MAX_KLINES_PER_REQUEST}. This is the
	 * primitive the historical paginator uses; the 3-arg overload above is
	 * unchanged for backward compatibility.
	 */
	public List<KlineResponse> klines(String symbol, String interval, Long startTime, Long endTime,
			int limit) {
		int capped = Math.max(1, Math.min(limit, MAX_KLINES_PER_REQUEST));
		try {
			String body = rest.get()
					.uri(uriBuilder -> uriBuilder
							.path("/api/v3/klines")
							.queryParam("symbol", MarketTickerStore.normalize(symbol))
							.queryParam("interval", interval)
							.queryParamIfPresent("startTime", Optional.ofNullable(startTime))
							.queryParamIfPresent("endTime", Optional.ofNullable(endTime))
							.queryParam("limit", capped)
							.build())
					.accept(MediaType.APPLICATION_JSON)
					.retrieve()
					.body(String.class);
			if (body == null || body.isBlank()) {
				throw new MarketUpstreamException("Empty kline response from Binance");
			}
			return toKlines(body);
		} catch (RestClientException ex) {
			throw new MarketUpstreamException("Unable to load candlesticks from Binance", ex);
		}
	}

	static List<MarketTicker> parseRestTickers(String body, UsdtSymbolDirectory directory) {
		if (body == null || body.isBlank()) {
			return List.of();
		}
		JsonArray rows = BinanceTickerParser.parseArray(body);
		List<MarketTicker> tickers = new ArrayList<>();
		for (JsonElement element : rows) {
			JsonObject item = element.getAsJsonObject();
			String symbol = item.get("symbol").getAsString();
			if (!directory.contains(symbol)) {
				continue;
			}
			tickers.add(BinanceTickerParser.parseRest24h(item, directory.name(symbol)));
		}
		return tickers;
	}

	static List<KlineResponse> toKlines(String body) {
		JsonArray rows = BinanceTickerParser.parseArray(body);
		List<KlineResponse> candles = new ArrayList<>();
		for (JsonElement row : rows) {
			JsonArray item = row.getAsJsonArray();
			candles.add(new KlineResponse(
					item.get(0).getAsLong(),
					new BigDecimal(item.get(1).getAsString()),
					new BigDecimal(item.get(2).getAsString()),
					new BigDecimal(item.get(3).getAsString()),
					new BigDecimal(item.get(4).getAsString()),
					new BigDecimal(item.get(5).getAsString()),
					item.get(6).getAsLong()
			));
		}
		return candles;
	}
}
