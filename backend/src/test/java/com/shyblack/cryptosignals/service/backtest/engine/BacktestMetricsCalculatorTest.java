package com.shyblack.cryptosignals.service.backtest.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.BacktestEquityPoint;
import com.shyblack.cryptosignals.entity.BacktestTrade;
import com.shyblack.cryptosignals.entity.enums.BacktestExitReason;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class BacktestMetricsCalculatorTest {

	private static BacktestTrade trade(double net, BacktestExitReason reason) {
		BacktestTrade t = new BacktestTrade();
		t.setSymbol("BTCUSDT");
		t.setSide(PositionSide.LONG);
		t.setQuantity(new BigDecimal("1"));
		t.setEntryPrice(new BigDecimal("100"));
		t.setExitPrice(new BigDecimal("100"));
		t.setNotional(new BigDecimal("100"));
		t.setEntryFee(BigDecimal.ZERO);
		t.setExitFee(BigDecimal.ZERO);
		t.setGrossPnl(BigDecimal.valueOf(net));
		t.setNetPnl(BigDecimal.valueOf(net));
		t.setExitReason(reason);
		t.setEntryTime(Instant.EPOCH);
		t.setExitTime(Instant.EPOCH.plusSeconds(3600));
		return t;
	}

	private static BacktestEquityPoint equity(double eq, double peak, double dd) {
		BacktestEquityPoint p = new BacktestEquityPoint();
		p.setTime(Instant.EPOCH);
		p.setEquity(BigDecimal.valueOf(eq));
		p.setPeakEquity(BigDecimal.valueOf(peak));
		p.setDrawdown(BigDecimal.valueOf(dd));
		p.setDrawdownPct(peak == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(dd * 100 / peak));
		p.setAvailableBalance(BigDecimal.valueOf(eq));
		p.setUnrealizedPnl(BigDecimal.ZERO);
		p.setRealizedPnl(BigDecimal.ZERO);
		return p;
	}

	@Test
	void empty_trades_returnsSaneMetrics_noFakeValues() {
		var m = BacktestMetricsCalculator.compute(List.of(), List.of(), new BigDecimal("10000"));
		assertThat(m.totalTrades()).isZero();
		// Undefined metrics come back as null (per Phase 22).
		assertThat(m.winRatePct()).isNull();
		assertThat(m.profitFactor()).isNull();
		assertThat(m.averageWin()).isNull();
		assertThat(m.averageLoss()).isNull();
	}

	@Test
	void basic_metrics_areComputed() {
		var m = BacktestMetricsCalculator.compute(
				List.of(
						trade(50, BacktestExitReason.TAKE_PROFIT),
						trade(-30, BacktestExitReason.STOP_LOSS),
						trade(20, BacktestExitReason.TAKE_PROFIT)),
				List.of(
						equity(10050, 10050, 0),
						equity(10020, 10050, 30),
						equity(10040, 10050, 10)),
				new BigDecimal("10000"));

		assertThat(m.totalTrades()).isEqualTo(3);
		assertThat(m.winningTrades()).isEqualTo(2);
		assertThat(m.losingTrades()).isEqualTo(1);
		assertThat(m.winRatePct().doubleValue()).isCloseTo(66.6667, org.assertj.core.data.Offset.offset(0.01));
		assertThat(m.grossProfit()).isEqualByComparingTo("70");
		assertThat(m.grossLoss()).isEqualByComparingTo("-30");
		assertThat(m.totalNetPnl()).isEqualByComparingTo("40");
		assertThat(m.profitFactor().doubleValue()).isCloseTo(70.0 / 30.0, org.assertj.core.data.Offset.offset(0.001));
		assertThat(m.maxDrawdown()).isEqualByComparingTo("30");
		assertThat(m.averageWin()).isEqualByComparingTo("35");
		assertThat(m.averageLoss()).isEqualByComparingTo("30");
		assertThat(m.largestWin()).isEqualByComparingTo("50");
		assertThat(m.largestLoss()).isEqualByComparingTo("-30");
	}

	@Test
	void liquidations_areCounted() {
		var m = BacktestMetricsCalculator.compute(
				List.of(trade(-100, BacktestExitReason.LIQUIDATION)),
				List.of(equity(9900, 10000, 100)),
				new BigDecimal("10000"));
		assertThat(m.liquidations()).isEqualTo(1);
	}

	// ── Drawdown percent is its own maximum ─────────────────────

	/**
	 * Regression: maxDrawdownPct was only recorded on the candle that had the
	 * largest ABSOLUTE drawdown. Once the running peak falls, the deepest
	 * percentage drawdown can land on a different, shallower point, so that
	 * point's percentage was discarded.
	 */
	@Test
	void maxDrawdownPct_isTrackedIndependentlyOfMaxDrawdown() {
		// peak 10000, dd 900 absolute (9.0%)  <- largest absolute drawdown
		// peak then collapses to 2000, dd 400 absolute (20.0%) <- deepest percent
		var m = BacktestMetricsCalculator.compute(
				List.of(trade(-500, BacktestExitReason.STOP_LOSS)),
				List.of(
						equity(10000, 10000, 0),
						equity(9100, 10000, 900),
						equity(2000, 2000, 0),
						equity(1600, 2000, 400)),
				new BigDecimal("10000"));

		assertThat(m.maxDrawdown()).isEqualByComparingTo("900");
		assertThat(m.maxDrawdownPct())
				.as("20% on a collapsed peak beats 9% on the original peak")
				.isEqualByComparingTo("20");
	}

	@Test
	void maxDrawdown_isZero_whenTheCurveNeverFalls() {
		var m = BacktestMetricsCalculator.compute(
				List.of(trade(100, BacktestExitReason.TAKE_PROFIT)),
				List.of(equity(10000, 10000, 0), equity(10100, 10100, 0)),
				new BigDecimal("10000"));
		assertThat(m.maxDrawdown()).isEqualByComparingTo("0");
		assertThat(m.maxDrawdownPct()).isEqualByComparingTo("0");
	}

	// ── Sharpe / Sortino ────────────────────────────────────────

	/**
	 * Regression: the downside deviation squared raw negative P&amp;L instead of
	 * its distance below the mean, and divided by the number of losing trades
	 * instead of the full sample. Both distortions are pinned here by comparing
	 * against the textbook definition.
	 */
	@Test
	void sortino_usesDownsideDeviationFromTheMean() {
		// P&L: +10, -5, +10, -5  -> mean 2.5
		// shortfall below mean: 7.5, 0, 7.5, 0
		// downside dev = sqrt((56.25 + 56.25) / 4) = sqrt(28.125) = 5.3033
		// sortino = 2.5 / 5.3033 = 0.4714
		var trades = List.of(
				trade(10, BacktestExitReason.TAKE_PROFIT),
				trade(-5, BacktestExitReason.STOP_LOSS),
				trade(10, BacktestExitReason.TAKE_PROFIT),
				trade(-5, BacktestExitReason.STOP_LOSS));
		var m = BacktestMetricsCalculator.compute(trades, List.of(equity(10000, 10000, 0)),
				new BigDecimal("10000"));

		double mean = 2.5;
		double expectedDownside = Math.sqrt((7.5 * 7.5 + 7.5 * 7.5) / 4);
		assertThat(m.sortinoRatio().doubleValue())
				.as("mean / sqrt(sum(min(0, x-mean)^2) / n)")
				.isCloseTo(mean / expectedDownside,
						org.assertj.core.data.Offset.offset(0.001));
	}

	@Test
	void sortino_isUndefinedWhenEveryTradeSitsAtTheMean() {
		// Mean 10 and both trades at 10 => zero downside deviation.
		var m = BacktestMetricsCalculator.compute(
				List.of(trade(10, BacktestExitReason.TAKE_PROFIT), trade(10, BacktestExitReason.TAKE_PROFIT)),
				List.of(equity(10000, 10000, 0)),
				new BigDecimal("10000"));
		assertThat(m.sortinoRatio()).as("no downside deviation to divide by").isNull();
	}

	@Test
	void sortino_exceedsSharpeWhenTheDownsideIsShallower() {
		// +10, -5, +10, -5: mean 2.5, sd 7.5, downside deviation 5.3033.
		// A ratio with the smaller divisor must be the larger number.
		var m = BacktestMetricsCalculator.compute(
				List.of(trade(10, BacktestExitReason.TAKE_PROFIT),
						trade(-5, BacktestExitReason.STOP_LOSS),
						trade(10, BacktestExitReason.TAKE_PROFIT),
						trade(-5, BacktestExitReason.STOP_LOSS)),
				List.of(equity(10000, 10000, 0)),
				new BigDecimal("10000"));
		assertThat(m.sharpeRatio()).isEqualByComparingTo("0.3333");
		assertThat(m.sortinoRatio()).isEqualByComparingTo("0.4714");
		assertThat(m.sortinoRatio()).isGreaterThan(m.sharpeRatio());
	}

	@Test
	void sharpe_usesStandardDeviationOfTheWholeSample() {
		// P&L: +10, -5, +10, -5 -> mean 2.5, deviations ±7.5,
		// sd = sqrt(225 / 4) = 7.5, sharpe = 2.5 / 7.5 = 0.3333
		var m = BacktestMetricsCalculator.compute(
				List.of(trade(10, BacktestExitReason.TAKE_PROFIT),
						trade(-5, BacktestExitReason.STOP_LOSS),
						trade(10, BacktestExitReason.TAKE_PROFIT),
						trade(-5, BacktestExitReason.STOP_LOSS)),
				List.of(equity(10000, 10000, 0)),
				new BigDecimal("10000"));
		assertThat(m.sharpeRatio().doubleValue())
				.isCloseTo(2.5 / 7.5, org.assertj.core.data.Offset.offset(0.001));
	}

	@Test
	void profitFactor_isNullWithNoLosingTrades() {
		var m = BacktestMetricsCalculator.compute(
				List.of(trade(50, BacktestExitReason.TAKE_PROFIT)),
				List.of(equity(10050, 10050, 0)),
				new BigDecimal("10000"));
		assertThat(m.profitFactor()).isNull();
		assertThat(m.grossLoss()).isEqualByComparingTo("0");
	}

	@Test
	void nullInitialCapital_yieldsNullReturnInsteadOfThrowing() {
		var m = BacktestMetricsCalculator.compute(
				List.of(trade(10, BacktestExitReason.TAKE_PROFIT)),
				List.of(equity(10000, 10000, 0)),
				null);
		assertThat(m.returnPct()).isNull();
	}

	@Test
	void tradeWithNullNetPnl_isCountedWithoutThrowing() {
		BacktestTrade t = trade(0, BacktestExitReason.END_OF_TEST);
		t.setNetPnl(null);
		t.setEntryFee(null);
		var m = BacktestMetricsCalculator.compute(
				List.of(t), List.of(equity(10000, 10000, 0)), new BigDecimal("10000"));
		assertThat(m.totalTrades()).isEqualTo(1);
		assertThat(m.totalNetPnl()).isEqualByComparingTo("0");
		assertThat(m.totalFees()).isEqualByComparingTo("0");
		assertThat(m.sharpeRatio()).isNull();
	}
}
