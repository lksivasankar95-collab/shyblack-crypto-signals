package com.shyblack.cryptosignals.service.backtest.research;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Aggregates per-window {@link ResearchWindowResult}s into descriptive totals.
 * Undefined values are null, never sentinels. No winner selection, no tuning.
 */
public final class WindowMetricsAggregator {

	private WindowMetricsAggregator() {
	}

	public record Aggregate(
			int windows,
			int trades,
			int wins,
			int losses,
			BigDecimal winRatePct,
			BigDecimal netPnl,
			BigDecimal grossProfit,
			BigDecimal grossLoss,
			BigDecimal fees,
			BigDecimal expectancy,
			BigDecimal profitFactor,
			BigDecimal worstDrawdownPct,
			BigDecimal avgReturnPct
	) {}

	public static Aggregate aggregate(List<ResearchWindowResult> windows) {
		int n = windows == null ? 0 : windows.size();
		int trades = 0, wins = 0, losses = 0;
		BigDecimal net = BigDecimal.ZERO, gp = BigDecimal.ZERO, gl = BigDecimal.ZERO, fees = BigDecimal.ZERO;
		BigDecimal worstDd = BigDecimal.ZERO;
		BigDecimal returnSum = BigDecimal.ZERO;
		int returnCount = 0;
		if (windows != null) {
			for (ResearchWindowResult w : windows) {
				trades += w.trades();
				wins += w.wins();
				losses += w.losses();
				net = net.add(nz(w.netPnl()));
				gp = gp.add(nz(w.grossProfit()));
				gl = gl.add(nz(w.grossLoss()));
				fees = fees.add(nz(w.fees()));
				if (w.maxDrawdownPct() != null && w.maxDrawdownPct().compareTo(worstDd) > 0) {
					worstDd = w.maxDrawdownPct();
				}
				if (w.returnPct() != null) {
					returnSum = returnSum.add(w.returnPct());
					returnCount++;
				}
			}
		}
		BigDecimal winRate = trades == 0 ? null : BigDecimal.valueOf(wins)
				.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(trades), 4, RoundingMode.HALF_UP);
		BigDecimal expectancy = trades == 0 ? null
				: net.divide(BigDecimal.valueOf(trades), 8, RoundingMode.HALF_UP);
		BigDecimal profitFactor = gl.signum() == 0 ? null : gp.divide(gl, 4, RoundingMode.HALF_UP);
		BigDecimal avgReturn = returnCount == 0 ? null
				: returnSum.divide(BigDecimal.valueOf(returnCount), 4, RoundingMode.HALF_UP);
		return new Aggregate(n, trades, wins, losses, winRate, net, gp, gl, fees, expectancy, profitFactor,
				worstDd, avgReturn);
	}

	private static BigDecimal nz(BigDecimal v) {
		return v == null ? BigDecimal.ZERO : v;
	}
}
