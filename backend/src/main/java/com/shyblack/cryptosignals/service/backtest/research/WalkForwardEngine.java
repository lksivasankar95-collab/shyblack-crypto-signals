package com.shyblack.cryptosignals.service.backtest.research;

import com.shyblack.cryptosignals.entity.BacktestRun;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestConfig;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestEngine;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestMetricsCalculator;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalEvent;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Chronological walk-forward runner. Rolls a test window forward in time and
 * evaluates each window with a FRESH strategy instance using the frozen config.
 *
 * <p>NFM currently has no tunable parameter dimension wired into selection, so
 * this performs honest rolling out-of-sample evaluation (no fitting). When a
 * tunable dimension is added, train/validation selection plugs in here; it must
 * never read the test window.</p>
 *
 * <p>Look-ahead: candles/events are sliced to {@code [tStart,tEnd)} and the
 * engine's own {@code event.time <= candle.closeTime} filter still applies.</p>
 */
public final class WalkForwardEngine {

	private WalkForwardEngine() {
	}

	public static List<ResearchWindowResult> run(BacktestConfig base, Supplier<BacktestStrategy> strategyFactory,
			List<HistoricalCandle> candles, List<HistoricalEvent> events, int windowDays, int stepDays) {
		List<ResearchWindowResult> out = new ArrayList<>();
		if (windowDays <= 0 || stepDays <= 0 || candles == null || candles.isEmpty()) {
			return out;
		}
		Instant end = base.endDate();
		int index = 0;
		for (Instant tStart = base.startDate();
				!tStart.plus(windowDays, ChronoUnit.DAYS).isAfter(end);
				tStart = tStart.plus(stepDays, ChronoUnit.DAYS)) {
			Instant tEnd = tStart.plus(windowDays, ChronoUnit.DAYS);
			List<HistoricalCandle> windowCandles = sliceCandles(candles, tStart, tEnd);
			List<HistoricalEvent> windowEvents = sliceEvents(events, tStart, tEnd);
			if (!windowCandles.isEmpty()) {
				out.add(evaluate("WF_" + index, base, strategyFactory.get(), windowCandles, windowEvents,
						tStart, tEnd, null));
			}
			index++;
		}
		return out;
	}

	static ResearchWindowResult evaluate(String label, BacktestConfig base, BacktestStrategy strategy,
			List<HistoricalCandle> candles, List<HistoricalEvent> events, Instant start, Instant end,
			String paramsJson) {
		BacktestConfig windowConfig = new BacktestConfig(base.strategyId(), base.symbol(), base.timeframe(),
				base.tradingMode(), start, end, base.initialCapital(), base.riskPerTradePct(), base.feePct(),
				base.slippagePct(), base.leverage(), base.executionModel(), base.sameCandlePolicy(), paramsJson);
		BacktestRun run = new BacktestRun();
		run.setSymbol(base.symbol());
		BacktestEngine.Result result = BacktestEngine.run(run, windowConfig, strategy, candles, events,
				new AtomicBoolean(false));
		BacktestMetricsCalculator.Metrics m = BacktestMetricsCalculator.compute(
				result.trades(), result.equity(), base.initialCapital());
		return new ResearchWindowResult(label, start, end, paramsJson,
				m.totalTrades(), m.winningTrades(), m.losingTrades(), m.winRatePct(), m.totalNetPnl(),
				m.grossProfit(), m.grossLoss(), m.totalFees(), m.expectancy(), m.profitFactor(),
				m.maxDrawdownPct(), m.returnPct());
	}

	static List<HistoricalCandle> sliceCandles(List<HistoricalCandle> candles, Instant start, Instant end) {
		return candles.stream()
				.filter(c -> c.openTime() != null && !c.openTime().isBefore(start) && c.openTime().isBefore(end))
				.toList();
	}

	static List<HistoricalEvent> sliceEvents(List<HistoricalEvent> events, Instant start, Instant end) {
		if (events == null) {
			return List.of();
		}
		return events.stream()
				.filter(e -> e.time() != null && !e.time().isBefore(start) && e.time().isBefore(end))
				.toList();
	}
}
