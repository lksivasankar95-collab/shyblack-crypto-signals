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
}
