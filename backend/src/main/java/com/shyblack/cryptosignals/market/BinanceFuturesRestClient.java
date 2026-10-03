package com.shyblack.cryptosignals.market;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shyblack.cryptosignals.config.MarketProperties;
import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.exception.MarketUpstreamException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
				.requestFactory(com.shyblack.cryptosignals.config.HttpClientFactory.withDefaultTimeouts())
				.build();
		this.quoteAsset = properties.quoteAssetOrUsdt();
	}

	/**
	 * Tradable USDT-M futures instruments, with the identity Binance reports for each.
	 *
	 * <p>Contract metadata is preserved rather than flattened away. The trading universe is
	 * intentionally still limited to perpetuals, so existing strategy behaviour is unchanged, but
	 * the returned instruments carry {@code contractType} and settlement date so a caller that does
	 * render a dated contract can label it correctly instead of presenting it as an unqualified
	 * symbol identical to the spot pair.
	 */
	public List<MarketInstrument> loadTradableUsdtMFuturesInstruments() {
		try {
			String body = rest.get()
					.uri("/fapi/v1/exchangeInfo")
					.accept(MediaType.APPLICATION_JSON)
					.retrieve()
					.body(String.class);
			List<MarketInstrument> instruments = new ArrayList<>();
			if (body == null || body.isBlank()) {
				return instruments;
			}
			JsonObject root = JsonParser.parseString(body).getAsJsonObject();
			JsonArray symbols = root.getAsJsonArray("symbols");
			if (symbols == null) {
				return instruments;
			}
			for (JsonElement element : symbols) {
				JsonObject item = element.getAsJsonObject();
				if (!"TRADING".equals(item.get("status").getAsString())) {
					continue;
				}
				if (!quoteAsset.equalsIgnoreCase(item.get("quoteAsset").getAsString())) {
					continue;
				}
				String contractType = optString(item, "contractType");
				if (contractType != null && !"PERPETUAL".equalsIgnoreCase(contractType)) {
					continue;
				}
				instruments.add(new MarketInstrument(
						TradingMode.FUTURES,
						item.get("symbol").getAsString(),
						item.get("baseAsset").getAsString(),
						item.get("quoteAsset").getAsString(),
						contractType,
						optDeliveryDate(item, "deliveryDate", contractType)));
			}
			log.info("Loaded {} USDT-M Futures {} instruments from exchangeInfo", instruments.size(), quoteAsset);
			return instruments;
		} catch (RestClientException ex) {
			throw new MarketUpstreamException("Unable to load Futures exchangeInfo from Binance", ex);
		}
	}

	private static String optString(JsonObject item, String field) {
		return item.has(field) && !item.get(field).isJsonNull()
				? item.get(field).getAsString()
				: null;
	}

	/**
	 * Settlement date of a dated contract, or null.
	 *
	 * <p>Two Binance conventions make the raw field unusable as-is. USDⓈ-M perpetuals carry
	 * {@code deliveryDate = 0}, and dated USDⓈ-M contracts carry a far-future sentinel
	 * ({@code 4133404800000}, year 2100) rather than an omitted field. Rendering either produces a
	 * meaningless expiry on a contract that has none — a perpetual labelled "2100-12-25".
	 *
	 * <p>Contract type is authoritative: a perpetual has no expiry by definition, and only a dated
	 * contract's settlement date is real.
	 */
	private static LocalDate optDeliveryDate(JsonObject item, String field, String contractType) {
		if (contractType != null && "PERPETUAL".equalsIgnoreCase(contractType)) {
			return null;
		}
		if (!item.has(field) || item.get(field).isJsonNull()) {
			return null;
		}
		long epochMillis = item.get(field).getAsLong();
		if (epochMillis <= 0) {
			return null;
		}
		LocalDate date = Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate();
		// Binance's "no settlement" sentinel for USDⓈ-M dated contracts.
		return date.getYear() >= 2100 ? null : date;
	}

	public Map<String, String> loadTradableUsdtMFuturesNames() {
		Map<String, String> names = new LinkedHashMap<>();
		for (MarketInstrument instrument : loadTradableUsdtMFuturesInstruments()) {
			names.put(instrument.exchangeSymbol(), instrument.baseAsset());
		}
		return names;
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
