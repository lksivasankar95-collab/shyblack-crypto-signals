package com.shyblack.cryptosignals;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.market.BinanceFuturesRestClient;
import com.shyblack.cryptosignals.market.BinanceRestClient;
import com.shyblack.cryptosignals.market.MarketBook;
import com.shyblack.cryptosignals.market.MarketTicker;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MarketBook book;

	@MockitoBean
	private BinanceRestClient binanceRestClient;

	@MockitoBean
	private BinanceFuturesRestClient binanceFuturesRestClient;

	@BeforeEach
	void seed() {
		book.spotTickers().clear();
		book.futuresTickers().clear();
		book.spotTickers().upsert(ticker("BTCUSDT", "Bitcoin", "65000", "2.5"));
		book.spotTickers().upsert(ticker("ETHUSDT", "Ethereum", "3400", "-1.2"));
		book.spotTickers().upsert(ticker("SOLUSDT", "Solana", "150", "8.1"));
		book.futuresTickers().upsert(ticker("BTCUSDT", "Bitcoin", "65100", "3.1"));
		book.futuresTickers().upsert(ticker("ETHUSDT", "Ethereum", "3390", "-2.4"));
	}

	@Test
	void snapshotGainersLosersAndSymbolDefaultToSpot() throws Exception {
		mockMvc.perform(get("/api/markets"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mode").value("SPOT"))
				.andExpect(jsonPath("$.count").value(3))
				.andExpect(jsonPath("$.tickers[?(@.symbol=='BTCUSDT')].name").value("Bitcoin"));

		mockMvc.perform(get("/api/markets/gainers"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tickers[0].symbol").value("SOLUSDT"));

		mockMvc.perform(get("/api/markets/losers"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tickers[0].symbol").value("ETHUSDT"));

		mockMvc.perform(get("/api/markets/BTCUSDT"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.price").value(65000));

		mockMvc.perform(get("/api/markets/UNKNOWN"))
				.andExpect(status().isNotFound());
	}

	@Test
	void futuresModeReadsFuturesStoreOnly() throws Exception {
		mockMvc.perform(get("/api/markets").param("mode", "FUTURES"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mode").value("FUTURES"))
				.andExpect(jsonPath("$.count").value(2))
				.andExpect(jsonPath("$.tickers[?(@.symbol=='SOLUSDT')]").isEmpty());

		mockMvc.perform(get("/api/markets/gainers").param("mode", "futures"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tickers[0].symbol").value("BTCUSDT"));

		mockMvc.perform(get("/api/markets/losers").param("mode", "FUTURES"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tickers[0].symbol").value("ETHUSDT"));

		mockMvc.perform(get("/api/markets/BTCUSDT").param("mode", "FUTURES"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.price").value(65100));
	}

	@Test
	void optionsModeReturnsEmptyPlaceholder() throws Exception {
		mockMvc.perform(get("/api/markets").param("mode", "OPTIONS"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mode").value("OPTIONS"))
				.andExpect(jsonPath("$.count").value(0))
				.andExpect(jsonPath("$.tickers").isEmpty())
				.andExpect(jsonPath("$.message").value("Options data not yet available"));

		mockMvc.perform(get("/api/markets/gainers").param("mode", "OPTIONS"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Options data not yet available"));

		mockMvc.perform(get("/api/markets/losers").param("mode", "OPTIONS"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tickers").isEmpty());
	}

	@Test
	void invalidModeIsBadRequest() throws Exception {
		mockMvc.perform(get("/api/markets").param("mode", "SWAPS"))
				.andExpect(status().isBadRequest());
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

	@Test
	void futuresKlinesDelegateToFuturesRest() throws Exception {
		when(binanceFuturesRestClient.klines(anyString(), anyString(), anyInt()))
				.thenReturn(List.of(new KlineResponse(
						1L,
						new BigDecimal("1"),
						new BigDecimal("2"),
						new BigDecimal("0.5"),
						new BigDecimal("1.6"),
						new BigDecimal("100"),
						2L
				)));

		mockMvc.perform(get("/api/markets/BTCUSDT/klines")
						.param("interval", "1h")
						.param("limit", "10")
						.param("mode", "FUTURES"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].close").value(1.6));
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
