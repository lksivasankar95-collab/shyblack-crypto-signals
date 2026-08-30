package com.shyblack.cryptosignals.market;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.time.Instant;

public final class BinanceTickerParser {

	private BinanceTickerParser() {
	}

	public static MarketTicker parseCombined(String message, String name) {
		JsonObject root = JsonParser.parseString(message).getAsJsonObject();
		JsonObject data = root.has("data") ? root.getAsJsonObject("data") : root;
		return parseTicker(data, name);
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

	private static BigDecimal decimal(JsonObject data, String field) {
		if (!data.has(field) || data.get(field).isJsonNull()) {
			return BigDecimal.ZERO;
		}
		return new BigDecimal(data.get(field).getAsString());
	}

	public static JsonArray parseArray(String json) {
		return JsonParser.parseString(json).getAsJsonArray();
	}
}
