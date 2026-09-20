package com.shyblack.cryptosignals.service.backtest.engine;

import com.shyblack.cryptosignals.entity.BacktestEquityPoint;
import com.shyblack.cryptosignals.entity.BacktestTrade;
import com.shyblack.cryptosignals.entity.enums.BacktestExitReason;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Computes summary metrics for a completed backtest. Undefined metrics
 * return null rather than fake sentinel values.
 */
public final class BacktestMetricsCalculator {

	private BacktestMetricsCalculator() {}

	public record Metrics(
			BigDecimal grossProfit,
			BigDecimal grossLoss,
			BigDecimal totalNetPnl,
			BigDecimal totalFees,
			int totalTrades,
			int winningTrades,
			int losingTrades,
			int liquidations,
			BigDecimal winRatePct,
			BigDecimal profitFactor,
			BigDecimal averageWin,
			BigDecimal averageLoss,
			BigDecimal largestWin,
			BigDecimal largestLoss,
			BigDecimal expectancy,
			BigDecimal sharpeRatio,
			BigDecimal sortinoRatio,
			BigDecimal maxDrawdown,
			BigDecimal maxDrawdownPct,
			BigDecimal returnPct
	) {}

	public static Metrics compute(List<BacktestTrade> trades, List<BacktestEquityPoint> equity,
			BigDecimal initialCapital) {

		int total = trades.size();
		int wins = 0, losses = 0, liquidations = 0;
		BigDecimal gross = BigDecimal.ZERO, grossWin = BigDecimal.ZERO, grossLoss = BigDecimal.ZERO;
		BigDecimal fees = BigDecimal.ZERO;
		BigDecimal largestWin = null, largestLoss = null;
		for (BacktestTrade t : trades) {
			BigDecimal net = safe(t.getNetPnl());
			gross = gross.add(net);
			fees = fees.add(safe(t.getEntryFee())).add(safe(t.getExitFee()));
			if (t.getExitReason() == BacktestExitReason.LIQUIDATION) liquidations++;
			if (net.signum() > 0) {
				wins++;
				grossWin = grossWin.add(net);
				if (largestWin == null || net.compareTo(largestWin) > 0) largestWin = net;
			} else if (net.signum() < 0) {
				losses++;
				grossLoss = grossLoss.add(net.abs());
				if (largestLoss == null || net.compareTo(largestLoss) < 0) largestLoss = net;
			}
		}
		BigDecimal winRate = total == 0 ? null
				: BigDecimal.valueOf(wins).multiply(BigDecimal.valueOf(100))
						.divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
		BigDecimal profitFactor = grossLoss.signum() == 0
				? (grossWin.signum() > 0 ? null : null)
				: grossWin.divide(grossLoss, 4, RoundingMode.HALF_UP);
		BigDecimal avgWin = wins == 0 ? null
				: grossWin.divide(BigDecimal.valueOf(wins), 8, RoundingMode.HALF_UP);
		BigDecimal avgLoss = losses == 0 ? null
				: grossLoss.divide(BigDecimal.valueOf(losses), 8, RoundingMode.HALF_UP);
		BigDecimal expectancy = total == 0 ? null
				: gross.divide(BigDecimal.valueOf(total), 8, RoundingMode.HALF_UP);

		BigDecimal maxDd = BigDecimal.ZERO;
		BigDecimal maxDdPct = BigDecimal.ZERO;
		for (BacktestEquityPoint p : equity) {
			BigDecimal dd = p.getDrawdown() == null ? BigDecimal.ZERO : p.getDrawdown();
			if (dd.compareTo(maxDd) > 0) {
				maxDd = dd;
				maxDdPct = p.getDrawdownPct() == null ? BigDecimal.ZERO : p.getDrawdownPct();
			}
		}
		BigDecimal finalEquity = equity.isEmpty()
				? initialCapital
				: equity.get(equity.size() - 1).getEquity();
		BigDecimal returnPct = initialCapital.signum() == 0 ? null
				: finalEquity.subtract(initialCapital)
						.multiply(BigDecimal.valueOf(100))
						.divide(initialCapital, 4, RoundingMode.HALF_UP);

		BigDecimal sharpe = trades.isEmpty() ? null : ratio(trades, false);
		BigDecimal sortino = trades.isEmpty() ? null : ratio(trades, true);

		return new Metrics(grossWin, grossLoss.negate(), gross, fees, total,
				wins, losses, liquidations, winRate, profitFactor,
				avgWin, avgLoss, largestWin, largestLoss, expectancy,
				sharpe, sortino, maxDd, maxDdPct, returnPct);
	}

	/**
	 * Non-annualized Sharpe / Sortino computed over per-trade returns. Values
	 * are indicative — sub-daily backtests should scale by trades/day. We do
	 * not fabricate an annualization factor when we don't know the strategy
	 * cadence.
	 */
	private static BigDecimal ratio(List<BacktestTrade> trades, boolean downsideOnly) {
		double mean = 0;
		int n = trades.size();
		for (BacktestTrade t : trades) mean += t.getNetPnl().doubleValue();
		mean /= n;
		double variance = 0;
		int downCount = 0;
		for (BacktestTrade t : trades) {
			double x = t.getNetPnl().doubleValue();
			double d = x - mean;
			if (downsideOnly) {
				if (x < 0) { variance += x * x; downCount++; }
			} else {
				variance += d * d;
			}
		}
		int denom = downsideOnly ? Math.max(downCount, 1) : n;
		variance = variance / denom;
		double sd = Math.sqrt(variance);
		if (sd <= 0) return null;
		return BigDecimal.valueOf(mean / sd).setScale(4, RoundingMode.HALF_UP);
	}

	private static BigDecimal safe(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
