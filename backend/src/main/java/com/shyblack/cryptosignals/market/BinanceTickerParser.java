package com.shyblack.cryptosignals.market;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public final class BinanceTickerParser {

	private BinanceTickerParser() {
	}

	public static MarketTicker parseCombined(String message, String name) {
		JsonObject root = JsonParser.parseString(message).getAsJsonObject();
		JsonObject data = root.has("data") && root.get("data").isJsonObject()
				? root.getAsJsonObject("data")
				: root;
		return parseTicker(data, name);
	}

	public static List<MarketTicker> parseTickerArray(
			String message,
			Predicate<String> accept,
			Function<String, String> names
	) {
		JsonElement root = JsonParser.parseString(message);
		JsonArray rows = extractArray(root);
		if (rows == null) {
			if (root.isJsonObject()) {
				JsonObject data = root.getAsJsonObject().has("data")
						&& root.getAsJsonObject().get("data").isJsonObject()
						? root.getAsJsonObject().getAsJsonObject("data")
						: root.getAsJsonObject();
				if (data.has("s")) {
					String symbol = data.get("s").getAsString();
					if (accept.test(symbol)) {
						return List.of(parseTicker(data, names.apply(symbol)));
					}
				}
			}
			return List.of();
		}
		List<MarketTicker> tickers = new ArrayList<>();
		for (JsonElement element : rows) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject data = element.getAsJsonObject();
			if (!data.has("s")) {
				continue;
			}
			String symbol = data.get("s").getAsString();
			if (!accept.test(symbol)) {
				continue;
			}
			tickers.add(parseTicker(data, names.apply(symbol)));
		}
		return tickers;
	}

	public static MarketTicker parseTicker(JsonObject data, String name) {
		String symbol = data.get("s").getAsString();
		return new MarketTicker(
				symbol,
				name,
				decimal(data, "c"),
				decimal(data, "p"),
				decimal(data, "P"),
				decimal(data, "v"),
				decimal(data, "h"),
				decimal(data, "l"),
				Instant.now()
		);
	}

	public static MarketTicker parseRest24h(JsonObject data, String name) {
		String symbol = data.get("symbol").getAsString();
		return new MarketTicker(
				symbol,
				name,
				decimal(data, "lastPrice"),
				decimal(data, "priceChange"),
				decimal(data, "priceChangePercent"),
				decimal(data, "volume"),
				decimal(data, "highPrice"),
				decimal(data, "lowPrice"),
				Instant.now()
		);
	}

	private static BigDecimal decimal(JsonObject data, String field) {
		if (!data.has(field) || data.get(field).isJsonNull()) {
			return BigDecimal.ZERO;
		}
		JsonElement value = data.get(field);
		if (value.isJsonPrimitive()) {
			return new BigDecimal(value.getAsString());
		}
		return BigDecimal.ZERO;
	}

	public static JsonArray parseArray(String json) {
		return JsonParser.parseString(json).getAsJsonArray();
	}

	private static JsonArray extractArray(JsonElement root) {
		if (root.isJsonArray()) {
			return root.getAsJsonArray();
		}
		if (root.isJsonObject() && root.getAsJsonObject().has("data")
				&& root.getAsJsonObject().get("data").isJsonArray()) {
			return root.getAsJsonObject().getAsJsonArray("data");
		}
		return null;
	}
}
