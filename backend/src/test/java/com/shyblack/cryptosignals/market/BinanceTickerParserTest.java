package com.shyblack.cryptosignals.market;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
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
}
