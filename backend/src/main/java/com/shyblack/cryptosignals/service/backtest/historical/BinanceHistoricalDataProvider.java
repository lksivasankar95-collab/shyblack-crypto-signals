package com.shyblack.cryptosignals.service.backtest.historical;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.market.BinanceRestClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Historical candles from Binance's public REST — reuses the existing
 * {@link BinanceRestClient#klines} pipe. In this drop the request is a
 * single fetch capped at 1000 candles by Binance; longer ranges require
 * paginated fetches which are documented as a next-drop enhancement.
 */
@Component
@RequiredArgsConstructor
public class BinanceHistoricalDataProvider implements HistoricalMarketDataProvider {

	private static final Logger log = LoggerFactory.getLogger(BinanceHistoricalDataProvider.class);

	private final BinanceRestClient restClient;

	@Override
	public List<HistoricalCandle> load(String symbol, String timeframe, Instant start, Instant end) {
		int limit = 1000;
		List<KlineResponse> raw = restClient.klines(symbol, timeframe, limit);
		List<HistoricalCandle> out = new ArrayList<>();
		for (KlineResponse k : raw) {
			Instant openTime = Instant.ofEpochMilli(k.openTime());
			if (openTime.isBefore(start) || !openTime.isBefore(end)) continue;
			HistoricalCandle c = new HistoricalCandle(
					openTime, k.open(), k.high(), k.low(), k.close(), k.volume(),
					Instant.ofEpochMilli(k.closeTime()));
			if (c.isValid()) out.add(c);
		}
		log.info("[BinanceHist] {} {} loaded {} candles in [{}, {}]",
				symbol, timeframe, out.size(), start, end);
		return out;
	}
}
