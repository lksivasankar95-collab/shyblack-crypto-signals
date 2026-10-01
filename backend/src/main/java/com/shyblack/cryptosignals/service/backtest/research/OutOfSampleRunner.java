package com.shyblack.cryptosignals.service.backtest.research;

import com.shyblack.cryptosignals.service.backtest.engine.BacktestConfig;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalEvent;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategy;
import java.util.List;

/**
 * Strict out-of-sample evaluation over a single held-out window using the FROZEN
 * configuration and strategy instance. No parameter selection happens here — the
 * caller must have chosen parameters on train/validation only.
 */
public final class OutOfSampleRunner {

	private OutOfSampleRunner() {
	}

	public static ResearchWindowResult run(BacktestConfig base, BacktestStrategy frozenStrategy,
			List<HistoricalCandle> candles, List<HistoricalEvent> events) {
		List<HistoricalCandle> windowCandles =
				WalkForwardEngine.sliceCandles(candles, base.startDate(), base.endDate());
		List<HistoricalEvent> windowEvents =
				WalkForwardEngine.sliceEvents(events, base.startDate(), base.endDate());
		return WalkForwardEngine.evaluate("OOS", base, frozenStrategy, windowCandles, windowEvents,
				base.startDate(), base.endDate(), base.strategyParams());
	}
}
