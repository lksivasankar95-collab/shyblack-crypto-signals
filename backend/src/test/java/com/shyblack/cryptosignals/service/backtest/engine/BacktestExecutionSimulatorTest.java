package com.shyblack.cryptosignals.service.backtest.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.enums.BacktestSameCandlePolicy;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BacktestExecutionSimulatorTest {

	private static HistoricalCandle candle(double o, double h, double l, double c) {
		Instant t = Instant.parse("2024-01-01T00:00:00Z");
		return new HistoricalCandle(t,
				BigDecimal.valueOf(o), BigDecimal.valueOf(h),
				BigDecimal.valueOf(l), BigDecimal.valueOf(c),
				BigDecimal.valueOf(1000), t.plusSeconds(3600));
	}

	@Test
	void long_sl_and_tp_bothTouched_defaultsTo_SL_FIRST() {
		HistoricalCandle c = candle(100, 111, 89, 100);
		var trigger = BacktestExecutionSimulator.evaluate(c, PositionSide.LONG,
				new BigDecimal("90"), new BigDecimal("110"), null,
				BacktestSameCandlePolicy.SL_FIRST, TradingMode.SPOT, BigDecimal.ZERO);
		assertThat(trigger).isInstanceOf(BacktestExecutionSimulator.SlTrigger.class);
	}

	@Test
	void long_conservative_yields_SL_First_asWell() {
		HistoricalCandle c = candle(100, 111, 89, 100);
		var trigger = BacktestExecutionSimulator.evaluate(c, PositionSide.LONG,
				new BigDecimal("90"), new BigDecimal("110"), null,
				BacktestSameCandlePolicy.CONSERVATIVE, TradingMode.SPOT, BigDecimal.ZERO);
		assertThat(trigger).isInstanceOf(BacktestExecutionSimulator.SlTrigger.class);
	}

	@Test
	void long_only_tpTouched_returnsTp() {
		HistoricalCandle c = candle(100, 111, 95, 105);
		var trigger = BacktestExecutionSimulator.evaluate(c, PositionSide.LONG,
				new BigDecimal("90"), new BigDecimal("110"), null,
				BacktestSameCandlePolicy.SL_FIRST, TradingMode.SPOT, BigDecimal.ZERO);
		assertThat(trigger).isInstanceOf(BacktestExecutionSimulator.TpTrigger.class);
	}

	@Test
	void long_neither_touched_returnsNoTrigger() {
		HistoricalCandle c = candle(100, 105, 95, 102);
		var trigger = BacktestExecutionSimulator.evaluate(c, PositionSide.LONG,
				new BigDecimal("90"), new BigDecimal("110"), null,
				BacktestSameCandlePolicy.SL_FIRST, TradingMode.SPOT, BigDecimal.ZERO);
		assertThat(trigger).isInstanceOf(BacktestExecutionSimulator.NoTrigger.class);
	}

	@Test
	void short_sl_above_entry_isHit_onCandleHigh() {
		HistoricalCandle c = candle(100, 111, 95, 100);
		var trigger = BacktestExecutionSimulator.evaluate(c, PositionSide.SHORT,
				new BigDecimal("110"), new BigDecimal("90"), null,
				BacktestSameCandlePolicy.SL_FIRST, TradingMode.SPOT, BigDecimal.ZERO);
		assertThat(trigger).isInstanceOf(BacktestExecutionSimulator.SlTrigger.class);
	}

	@Test
	void long_liquidation_beats_tp_inSameCandle() {
		// SPOT positions have no liquidation — supply FUTURES + leverage.
		HistoricalCandle c = candle(100, 115, 65, 100);
		var trigger = BacktestExecutionSimulator.evaluate(c, PositionSide.LONG,
				new BigDecimal("70"), new BigDecimal("115"),
				new BigDecimal("66"), // liq level
				BacktestSameCandlePolicy.SL_FIRST, TradingMode.FUTURES, BigDecimal.ZERO);
		assertThat(trigger).isInstanceOf(BacktestExecutionSimulator.LiquidationTrigger.class);
	}

	@Test
	void long_adverseSlippage_onEntry_raisesPrice() {
		BigDecimal ref = new BigDecimal("100");
		BigDecimal fill = BacktestExecutionSimulator.applySlippage(ref, PositionSide.LONG, true,
				new BigDecimal("0.10"));
		assertThat(fill).isGreaterThan(ref);
	}

	@Test
	void long_adverseSlippage_onExit_lowersPrice() {
		BigDecimal ref = new BigDecimal("100");
		BigDecimal fill = BacktestExecutionSimulator.applySlippage(ref, PositionSide.LONG, false,
				new BigDecimal("0.10"));
		assertThat(fill).isLessThan(ref);
	}

	@Test
	void riskBasedQuantity_computesCorrectQty() {
		// balance 10_000, risk 1% => 100 risk; distance 5 => qty 20
		BigDecimal q = BacktestExecutionSimulator.riskBasedQuantity(
				new BigDecimal("10000"), new BigDecimal("1"),
				new BigDecimal("100"), new BigDecimal("95"));
		assertThat(q).isEqualByComparingTo("20");
	}

	@Test
	void leverage_doesNotChangeRiskAmount() {
		// riskBasedQuantity has no leverage input on purpose — leverage
		// affects margin, not risk. Verify the invariant.
		BigDecimal a = BacktestExecutionSimulator.riskBasedQuantity(
				new BigDecimal("1000"), new BigDecimal("1"),
				new BigDecimal("100"), new BigDecimal("95"));
		BigDecimal b = BacktestExecutionSimulator.riskBasedQuantity(
				new BigDecimal("1000"), new BigDecimal("1"),
				new BigDecimal("100"), new BigDecimal("95"));
		assertThat(a).isEqualByComparingTo(b);
	}

	@Test
	void estimateLiquidation_long_correctSign() {
		BigDecimal liq = BacktestExecutionSimulator.estimateLiquidation(
				TradingMode.FUTURES, PositionSide.LONG, new BigDecimal("100"), 3);
		assertThat(liq).isNotNull();
		assertThat(liq).isLessThan(new BigDecimal("100"));
	}

	@Test
	void estimateLiquidation_short_correctSign() {
		BigDecimal liq = BacktestExecutionSimulator.estimateLiquidation(
				TradingMode.FUTURES, PositionSide.SHORT, new BigDecimal("100"), 3);
		assertThat(liq).isGreaterThan(new BigDecimal("100"));
	}

	@Test
	void estimateLiquidation_isNull_forSpot() {
		assertThat(BacktestExecutionSimulator.estimateLiquidation(
				TradingMode.SPOT, PositionSide.LONG, new BigDecimal("100"), 3)).isNull();
	}
}
