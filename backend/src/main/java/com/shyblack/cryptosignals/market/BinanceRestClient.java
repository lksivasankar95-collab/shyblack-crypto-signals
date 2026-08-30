package com.shyblack.cryptosignals.market;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.shyblack.cryptosignals.config.MarketProperties;
import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.exception.MarketUpstreamException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class BinanceRestClient {

	private static final Logger log = LoggerFactory.getLogger(BinanceRestClient.class);

	private static final Map<String, String> FALLBACK_NAMES = Map.of(
			"BTCUSDT", "Bitcoin",
			"ETHUSDT", "Ethereum",
			"BNBUSDT", "BNB",
			"SOLUSDT", "Solana",
			"XRPUSDT", "XRP",
			"ADAUSDT", "Cardano",
			"DOGEUSDT", "Dogecoin"
	);

	private final RestClient rest;
	private final ConcurrentHashMap<String, String> names = new ConcurrentHashMap<>(FALLBACK_NAMES);
	private volatile boolean exchangeInfoLoaded;

	public BinanceRestClient(MarketProperties properties) {
		this.rest = RestClient.builder()
				.baseUrl(properties.restBaseUrl())
				.build();
	}

	public String displayName(String symbol) {
		ensureExchangeInfo();
		return names.getOrDefault(MarketTickerStore.normalize(symbol), baseAsset(symbol));
	}

	public List<KlineResponse> klines(String symbol, String interval, int limit) {
		try {
			String body = rest.get()
					.uri("/api/v3/klines?symbol={symbol}&interval={interval}&limit={limit}",
							MarketTickerStore.normalize(symbol), interval, limit)
					.accept(MediaType.APPLICATION_JSON)
					.retrieve()
					.body(String.class);
			if (body == null || body.isBlank()) {
				throw new MarketUpstreamException("Empty kline response from Binance");
			}
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
		} catch (RestClientException ex) {
			throw new MarketUpstreamException("Unable to load candlesticks from Binance", ex);
		}
	}

	private void ensureExchangeInfo() {
		if (exchangeInfoLoaded) {
			return;
		}
		synchronized (this) {
			if (exchangeInfoLoaded) {
				return;
			}
			try {
				String body = rest.get()
						.uri("/api/v3/exchangeInfo")
						.accept(MediaType.APPLICATION_JSON)
						.retrieve()
						.body(String.class);
				if (body != null) {
					JsonObject root = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
					JsonArray symbols = root.getAsJsonArray("symbols");
					if (symbols != null) {
						for (JsonElement element : symbols) {
							JsonObject item = element.getAsJsonObject();
							String symbol = item.get("symbol").getAsString();
							String base = item.get("baseAsset").getAsString();
							names.putIfAbsent(symbol, FALLBACK_NAMES.getOrDefault(symbol, base));
						}
					}
				}
				exchangeInfoLoaded = true;
				log.info("Cached Binance exchangeInfo ({} symbols)", names.size());
			} catch (Exception ex) {
				log.warn("Binance exchangeInfo unavailable; using fallback names: {}", ex.getMessage());
				exchangeInfoLoaded = true;
			}
		}
	}

	private static String baseAsset(String symbol) {
		String normalized = MarketTickerStore.normalize(symbol);
		if (normalized.endsWith("USDT") && normalized.length() > 4) {
			return normalized.substring(0, normalized.length() - 4);
		}
		return normalized;
	}
}
