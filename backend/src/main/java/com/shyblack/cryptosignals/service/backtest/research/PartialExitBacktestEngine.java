package com.shyblack.cryptosignals.service.backtest.research;

import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestConfig;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestExecutionSimulator;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalEvent;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategy;
import com.shyblack.cryptosignals.service.backtest.strategy.EventAwareBacktestStrategy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * OPT-IN partial-exit backtest engine (TP1/TP2/TP3 + SL). The existing
 * {@code BacktestEngine} (single-TP) remains the default and is NOT modified.
 *
 * <p>Single open position at a time; entries fill at the next candle's open
 * with adverse slippage; SL/TP are evaluated from the following candle (never
 * the fill candle) using {@link PartialExitSimulator}'s conservative same-bar
 * rule. No look-ahead: the strategy sees only candles up to the current index
 * and events up to the current candle close time.</p>
 */
public final class PartialExitBacktestEngine {

	private PartialExitBacktestEngine() {
	}

	public record Result(
			int trades, int wins, int losses,
			BigDecimal winRatePct, BigDecimal grossPnl, BigDecimal totalFees, BigDecimal netPnl,
			List<PartialExitSimulator.Lifecycle> lifecycles, List<Entry> entries) {}

	/** Additive per-trade capture for analytics (signal + entry context + lifecycle). */
	public record Entry(BacktestStrategy.Signal signal, int entryIndex, java.time.Instant entryTime,
			BigDecimal entryPrice, PartialExitSimulator.Lifecycle lifecycle) {}

	public static Result run(BacktestConfig config, BacktestStrategy strategy,
			List<HistoricalCandle> candles, List<HistoricalEvent> events) {
		List<PartialExitSimulator.Lifecycle> lifecycles = new ArrayList<>();
		List<Entry> entries = new ArrayList<>();
		int n = candles == null ? 0 : candles.size();
		if (n < 3) {
			return new Result(0, 0, 0, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, lifecycles,
					entries);
		}
		BigDecimal capital = config.initialCapital();

		int i = 0;
		while (i < n - 1) {
			List<HistoricalCandle> history = candles.subList(0, i + 1);
			BacktestStrategy.Signal signal = evaluate(strategy, history, i, events);
			if (signal == null) {
				i++;
				continue;
			}
			HistoricalCandle entryCandle = candles.get(i + 1);
			BigDecimal entry = BacktestExecutionSimulator.applySlippage(
					entryCandle.open(), signal.side(), true, config.slippagePct());
			BigDecimal qty = BacktestExecutionSimulator.riskBasedQuantity(
					capital, config.riskPerTradePct(), entry, signal.stopLoss());
			if (qty.signum() <= 0) {
				i++;
				continue;
			}
			boolean singleTp = signal.takeProfit2() == null && signal.takeProfit3() == null;
			BigDecimal tp1Frac = singleTp ? BigDecimal.ONE : PartialExitSimulator.DEFAULT_TP1_FRACTION;
			BigDecimal tp2Frac = singleTp ? BigDecimal.ZERO : PartialExitSimulator.DEFAULT_TP2_FRACTION;
			List<PartialExitSimulator.Bar> bars = new ArrayList<>();
			for (int j = i + 2; j < n; j++) {
				bars.add(new PartialExitSimulator.Bar(candles.get(j).high(), candles.get(j).low()));
			}
			PartialExitSimulator.Lifecycle lc = PartialExitSimulator.run(signal.side(), entry, qty,
					signal.stopLoss(), signal.takeProfit(), signal.takeProfit2(), signal.takeProfit3(),
					config.feePct(), tp1Frac, tp2Frac, bars);
			lifecycles.add(lc);
			entries.add(new Entry(signal, i + 1, entryCandle.openTime(), entry, lc));
			capital = capital.add(lc.netPnl());
			i = i + 2; // advance past the entry bar; sequential single-position model
		}

		int trades = lifecycles.size();
		int wins = 0, losses = 0;
		BigDecimal gross = BigDecimal.ZERO, fees = BigDecimal.ZERO, net = BigDecimal.ZERO;
		for (PartialExitSimulator.Lifecycle lc : lifecycles) {
			gross = gross.add(lc.grossPnl());
			fees = fees.add(lc.totalFees());
			net = net.add(lc.netPnl());
			if (lc.netPnl().signum() > 0) wins++;
			else if (lc.netPnl().signum() < 0) losses++;
		}
		BigDecimal winRate = trades == 0 ? null : BigDecimal.valueOf(wins)
				.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(trades), 4, RoundingMode.HALF_UP);
		return new Result(trades, wins, losses, winRate, gross, fees, net, lifecycles, entries);
	}

	private static BacktestStrategy.Signal evaluate(BacktestStrategy strategy,
			List<HistoricalCandle> history, int index, List<HistoricalEvent> events) {
		if (strategy instanceof EventAwareBacktestStrategy eventAware) {
			HistoricalCandle current = history.get(index);
			List<HistoricalEvent> visible = events == null ? List.of()
					: events.stream().filter(e -> e.time() != null && current.closeTime() != null
							&& !e.time().isAfter(current.closeTime())).toList();
			return eventAware.evaluate(history, index, visible).orElse(null);
		}
		return strategy.evaluate(history, index).orElse(null);
	}
}
