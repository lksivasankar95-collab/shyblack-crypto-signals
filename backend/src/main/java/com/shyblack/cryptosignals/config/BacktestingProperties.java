package com.shyblack.cryptosignals.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Backtest engine configuration. Resource limits are enforced BEFORE a run
 * starts — a single request cannot exhaust backend memory.
 *
 * engineVersion       stored on every completed run so historical results
 *                     remain identifiable if the engine changes later.
 * maxCandles          hard cap on the number of candles processed per run.
 * maxConcurrentRuns   worker pool size.
 * maxRangeDays        wall-clock window between startDate and endDate.
 */
@ConfigurationProperties(prefix = "app.backtesting")
public record BacktestingProperties(
		String engineVersion,
		int maxCandles,
		int maxConcurrentRuns,
		int maxRangeDays,
		int defaultWarmupCandles
) {
	public BacktestingProperties {
		if (engineVersion == null || engineVersion.isBlank()) engineVersion = "backtest-engine/v1";
		if (maxCandles <= 0) maxCandles = 20_000;
		if (maxConcurrentRuns <= 0) maxConcurrentRuns = 2;
		if (maxRangeDays <= 0) maxRangeDays = 365;
		if (defaultWarmupCandles <= 0) defaultWarmupCandles = 200;
	}
}
