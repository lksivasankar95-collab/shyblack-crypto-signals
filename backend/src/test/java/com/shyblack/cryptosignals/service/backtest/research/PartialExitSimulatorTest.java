package com.shyblack.cryptosignals.service.backtest.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.service.backtest.research.PartialExitSimulator.Bar;
import com.shyblack.cryptosignals.service.backtest.research.PartialExitSimulator.Lifecycle;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartialExitSimulatorTest {

	private static final BigDecimal ENTRY = new BigDecimal("100");
	private static final BigDecimal QTY = new BigDecimal("3");
	private static final BigDecimal FEE = new BigDecimal("0.1");

	private static Bar bar(String high, String low) {
		return new Bar(new BigDecimal(high), new BigDecimal(low));
	}

	private static Lifecycle longRun(List<Bar> bars) {
		return PartialExitSimulator.run(PositionSide.LONG, ENTRY, QTY,
				new BigDecimal("99"), new BigDecimal("101.5"), new BigDecimal("102.5"),
				new BigDecimal("104"), FEE, bars);
	}

	private static Lifecycle shortRun(List<Bar> bars) {
		return PartialExitSimulator.run(PositionSide.SHORT, ENTRY, QTY,
				new BigDecimal("101"), new BigDecimal("98.5"), new BigDecimal("97.5"),
				new BigDecimal("96"), FEE, bars);
	}

	private static void assertConserved(Lifecycle l) {
		BigDecimal exited = l.fills().stream().map(f -> f.quantity()).reduce(BigDecimal.ZERO, BigDecimal::add);
		assertThat(l.remainingQty()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
		assertThat(l.remainingQty()).isLessThanOrEqualTo(l.originalQty());
		assertThat(exited.add(l.remainingQty())).isEqualByComparingTo(l.originalQty());
	}

	@Test
	void long_tp1_tp2_tp3_scenarioA() {
		Lifecycle l = longRun(List.of(bar("101.5", "100"), bar("102.5", "101"), bar("104", "102")));
		assertThat(l.closed()).isTrue();
		assertThat(l.finalReason()).isEqualTo("TAKE_PROFIT");
		assertThat(l.fills()).hasSize(3);
		assertThat(l.remainingQty()).isEqualByComparingTo("0");
		assertThat(l.netPnl()).isGreaterThan(BigDecimal.ZERO);
		assertConserved(l);
	}

	@Test
	void long_tp1_then_sl_scenarioB() {
		Lifecycle l = longRun(List.of(bar("101.5", "100"), bar("101", "99")));
		assertThat(l.closed()).isTrue();
		assertThat(l.finalReason()).isEqualTo("STOP_LOSS");
		assertThat(l.fills()).hasSize(2);
		assertThat(l.remainingQty()).isEqualByComparingTo("0");
		assertConserved(l);
	}

	@Test
	void long_tp1_tp2_then_sl_scenarioC() {
		Lifecycle l = longRun(List.of(bar("101.5", "100"), bar("102.5", "101"), bar("101", "99")));
		assertThat(l.finalReason()).isEqualTo("STOP_LOSS");
		assertThat(l.fills()).hasSize(3);
		assertConserved(l);
	}

	@Test
	void long_sl_before_tp_scenarioD() {
		Lifecycle l = longRun(List.of(bar("101", "99")));
		assertThat(l.closed()).isTrue();
		assertThat(l.finalReason()).isEqualTo("STOP_LOSS");
		assertThat(l.fills()).hasSize(1);
		assertThat(l.netPnl()).isLessThan(BigDecimal.ZERO);
		assertConserved(l);
	}

	@Test
	void short_tp1_tp2_tp3_and_sl() {
		Lifecycle win = shortRun(List.of(bar("100", "98.5"), bar("99", "97.5"), bar("97", "96")));
		assertThat(win.finalReason()).isEqualTo("TAKE_PROFIT");
		assertThat(win.netPnl()).isGreaterThan(BigDecimal.ZERO);
		assertConserved(win);

		Lifecycle loss = shortRun(List.of(bar("101", "100")));
		assertThat(loss.finalReason()).isEqualTo("STOP_LOSS");
		assertThat(loss.netPnl()).isLessThan(BigDecimal.ZERO);
		assertConserved(loss);
	}

	@Test
	void sameBar_stopWinsBeforeTakeProfit_conservative() {
		// One bar touches both SL (low<=99) and TP1 (high>=101.5) -> SL closes all.
		Lifecycle l = longRun(List.of(bar("102", "98.5")));
		assertThat(l.finalReason()).isEqualTo("STOP_LOSS");
		assertThat(l.fills()).hasSize(1);
		assertThat(l.remainingQty()).isEqualByComparingTo("0");
	}

	@Test
	void deterministic_repeatRunsMatch() {
		List<Bar> bars = List.of(bar("101.5", "100"), bar("102.5", "101"), bar("104", "102"));
		Lifecycle a = longRun(bars);
		Lifecycle b = longRun(bars);
		assertThat(a.netPnl()).isEqualByComparingTo(b.netPnl());
		assertThat(a.fills()).hasSameSizeAs(b.fills());
		assertThat(a.finalReason()).isEqualTo(b.finalReason());
	}

	@Test
	void openPosition_neverClosedTwice_andQtyNeverNegative() {
		Lifecycle l = longRun(List.of(bar("104", "99")));
		assertThat(l.remainingQty()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
		assertThat(l.fills().stream().map(f -> f.quantity()).reduce(BigDecimal.ZERO, BigDecimal::add))
				.isLessThanOrEqualTo(l.originalQty());
	}
}
