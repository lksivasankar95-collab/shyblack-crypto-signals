package com.shyblack.cryptosignals.market;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shyblack.cryptosignals.config.MarketProperties;
import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.exception.MarketUpstreamException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class BinanceFuturesRestClient {

	private static final Logger log = LoggerFactory.getLogger(BinanceFuturesRestClient.class);

	private final RestClient rest;
	private final String quoteAsset;

	public BinanceFuturesRestClient(MarketProperties properties) {
		this.rest = RestClient.builder()
				.baseUrl(properties.futuresRestBaseUrl())
				.build();
		this.quoteAsset = properties.quoteAssetOrUsdt();
	}

	public Map<String, String> loadTradableUsdtMFuturesNames() {
		try {
			String body = rest.get()
					.uri("/fapi/v1/exchangeInfo")
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
				if (item.has("contractType") && !"PERPETUAL".equalsIgnoreCase(item.get("contractType").getAsString())) {
					continue;
				}
				String symbol = item.get("symbol").getAsString();
				String base = item.get("baseAsset").getAsString();
				names.put(symbol, base);
			}
			log.info("Loaded {} USDT-M Futures {} pairs from exchangeInfo", names.size(), quoteAsset);
			return names;
		} catch (RestClientException ex) {
			throw new MarketUpstreamException("Unable to load Futures exchangeInfo from Binance", ex);
		}
	}

	public List<MarketTicker> loadFutures24hTickers(UsdtSymbolDirectory directory) {
		try {
			String body = rest.get()
					.uri("/fapi/v1/ticker/24hr")
					.accept(MediaType.APPLICATION_JSON)
					.retrieve()
					.body(String.class);
			return BinanceRestClient.parseRestTickers(body, directory);
		} catch (RestClientException ex) {
			throw new MarketUpstreamException("Unable to load Futures 24h tickers from Binance", ex);
		}
	}

	public List<KlineResponse> klines(String symbol, String interval, int limit) {
		try {
			String body = rest.get()
					.uri("/fapi/v1/klines?symbol={symbol}&interval={interval}&limit={limit}",
							MarketTickerStore.normalize(symbol), interval, limit)
					.accept(MediaType.APPLICATION_JSON)
					.retrieve()
					.body(String.class);
			if (body == null || body.isBlank()) {
				throw new MarketUpstreamException("Empty Futures kline response from Binance");
			}
			return BinanceRestClient.toKlines(body);
		} catch (RestClientException ex) {
			throw new MarketUpstreamException("Unable to load Futures candlesticks from Binance", ex);
		}
	}
}
