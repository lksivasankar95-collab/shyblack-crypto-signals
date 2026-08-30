package com.shyblack.cryptosignals;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.market.BinanceRestClient;
import com.shyblack.cryptosignals.market.MarketTicker;
import com.shyblack.cryptosignals.market.MarketTickerStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MarketTickerStore store;

	@MockitoBean
	private BinanceRestClient binanceRestClient;

	@BeforeEach
	void seed() {
		store.upsert(ticker("BTCUSDT", "Bitcoin", "65000", "2.5"));
		store.upsert(ticker("ETHUSDT", "Ethereum", "3400", "-1.2"));
		store.upsert(ticker("SOLUSDT", "Solana", "150", "8.1"));
	}

	@Test
	void snapshotGainersLosersAndSymbol() throws Exception {
		mockMvc.perform(get("/api/markets"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].symbol").exists())
				.andExpect(jsonPath("$[?(@.symbol=='BTCUSDT')].name").value("Bitcoin"));

		mockMvc.perform(get("/api/markets/gainers"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].symbol").value("SOLUSDT"));

		mockMvc.perform(get("/api/markets/losers"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].symbol").value("ETHUSDT"));

		mockMvc.perform(get("/api/markets/BTCUSDT"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.price").value(65000));

		mockMvc.perform(get("/api/markets/UNKNOWN"))
				.andExpect(status().isNotFound());
	}

	@Test
	void klinesDelegateToBinanceRest() throws Exception {
		when(binanceRestClient.klines(anyString(), anyString(), anyInt()))
				.thenReturn(List.of(new KlineResponse(
						1L,
						new BigDecimal("1"),
						new BigDecimal("2"),
						new BigDecimal("0.5"),
						new BigDecimal("1.5"),
						new BigDecimal("100"),
						2L
				)));

		mockMvc.perform(get("/api/markets/BTCUSDT/klines").param("interval", "1h").param("limit", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].close").value(1.5));
	}

	private static MarketTicker ticker(String symbol, String name, String price, String changePct) {
		return new MarketTicker(
				symbol,
				name,
				new BigDecimal(price),
				BigDecimal.ONE,
				new BigDecimal(changePct),
				new BigDecimal("1000"),
				new BigDecimal(price).add(BigDecimal.TEN),
				new BigDecimal(price).subtract(BigDecimal.TEN),
				Instant.parse("2026-08-30T12:00:00Z")
		);
	}
}
