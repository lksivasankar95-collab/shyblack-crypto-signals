package com.shyblack.cryptosignals.service.backtest.historical;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory candle store used by the CI test suite. Tests seed candles for
 * (symbol, timeframe) and then run the engine deterministically. No network
 * traffic — safe by construction.
 */
public class FixtureHistoricalDataProvider implements HistoricalMarketDataProvider {

	private final Map<String, List<HistoricalCandle>> store = new ConcurrentHashMap<>();

	public void put(String symbol, String timeframe, List<HistoricalCandle> candles) {
		store.put(key(symbol, timeframe), new ArrayList<>(candles));
	}

	@Override
	public List<HistoricalCandle> load(String symbol, String timeframe, Instant start, Instant end) {
		List<HistoricalCandle> all = store.getOrDefault(key(symbol, timeframe), List.of());
		List<HistoricalCandle> out = new ArrayList<>();
		for (HistoricalCandle c : all) {
			if (!c.openTime().isBefore(start) && c.openTime().isBefore(end)) out.add(c);
		}
		return out;
	}

	private static String key(String symbol, String timeframe) {
		return symbol.toUpperCase() + "|" + timeframe;
	}
}
