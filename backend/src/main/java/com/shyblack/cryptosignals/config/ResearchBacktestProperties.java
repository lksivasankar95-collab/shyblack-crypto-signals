package com.shyblack.cryptosignals.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Research-only backtest limits. Disabled by default, so production backtest
 * limits ({@code app.backtesting}) remain unchanged. When enabled, a research
 * run may exceed the production candle/range/concurrency guards.
 */
@ConfigurationProperties(prefix = "app.research.backtest")
public record ResearchBacktestProperties(
		boolean enabled,
		int maxCandles,
		int maxRangeDays,
		int maxConcurrentRuns,
		int maxQueueDepth
) {
	public ResearchBacktestProperties {
		if (maxCandles <= 0) maxCandles = 2_000_000;
		if (maxRangeDays <= 0) maxRangeDays = 1_200;
		if (maxConcurrentRuns <= 0) maxConcurrentRuns = 1;
		if (maxQueueDepth <= 0) maxQueueDepth = 20;
	}
}
