package com.shyblack.cryptosignals.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BinanceTickerParserTest {

	@Test
	void parsesCombinedStreamPayload() {
		String json = """
				{"stream":"btcusdt@ticker","data":{"e":"24hrTicker","s":"BTCUSDT","p":"100.5","P":"1.23","c":"65000.1","h":"66000","l":"64000","v":"12.5"}}
				""";
		MarketTicker ticker = BinanceTickerParser.parseCombined(json, "Bitcoin");
		assertEquals("BTCUSDT", ticker.symbol());
		assertEquals("Bitcoin", ticker.name());
		assertEquals(new BigDecimal("65000.1"), ticker.price());
		assertEquals(new BigDecimal("1.23"), ticker.changePercent24h());
		assertEquals(new BigDecimal("100.5"), ticker.change24h());
	}

	@Test
	void parsesTickerArrayAndSkipsNonAcceptedSymbols() {
		String json = """
				[
				  {"e":"24hrTicker","s":"BTCUSDT","p":"100.5","P":"1.23","c":"65000.1","h":"66000","l":"64000","v":"12.5"},
				  {"e":"24hrTicker","s":"ETHBTC","p":"0.1","P":"0.2","c":"0.05","h":"0.06","l":"0.04","v":"1"}
				]
				""";
		List<MarketTicker> tickers = BinanceTickerParser.parseTickerArray(
				json,
				symbol -> symbol.endsWith("USDT"),
				symbol -> symbol.substring(0, symbol.length() - 4)
		);
		assertEquals(1, tickers.size());
		assertEquals("BTCUSDT", tickers.get(0).symbol());
		assertEquals("BTC", tickers.get(0).name());
	}

	@Test
	void parsesRest24hTicker() {
		String json = """
				{"symbol":"ETHUSDT","priceChange":"-10","priceChangePercent":"-1.2","lastPrice":"3400","highPrice":"3500","lowPrice":"3300","volume":"99"}
				""";
		MarketTicker ticker = BinanceTickerParser.parseRest24h(
				com.google.gson.JsonParser.parseString(json).getAsJsonObject(),
				"Ethereum"
		);
		assertEquals("ETHUSDT", ticker.symbol());
		assertEquals(new BigDecimal("3400"), ticker.price());
		assertEquals(new BigDecimal("-1.2"), ticker.changePercent24h());
		assertTrue(ticker.volume24h().compareTo(new BigDecimal("99")) == 0);
	}
}
