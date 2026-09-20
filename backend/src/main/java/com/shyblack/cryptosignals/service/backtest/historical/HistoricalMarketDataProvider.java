package com.shyblack.cryptosignals.service.backtest.historical;

import java.time.Instant;
import java.util.List;

/**
 * Sole abstraction between the backtest engine and any candle source. Two
 * shipping implementations:
 *
 *   BinanceHistoricalDataProvider  — hits the existing Binance public REST.
 *   FixtureHistoricalDataProvider  — deterministic in-memory fixtures for CI.
 *
 * A third-party CSV loader can be added later without touching the engine.
 */
public interface HistoricalMarketDataProvider {

	/**
	 * Load candles for {@code symbol}/{@code timeframe} between {@code start}
	 * (inclusive) and {@code end} (exclusive). Result must be
	 * chronologically ordered, deduplicated, and OHLC-valid — the engine
	 * treats the returned list as authoritative.
	 */
	List<HistoricalCandle> load(String symbol, String timeframe, Instant start, Instant end);
}
