package com.shyblack.cryptosignals.service.paper;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.service.paper.PaperPartialExitState.ExitEvent;
import com.shyblack.cryptosignals.service.paper.PaperPartialExitState.Status;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** FIXTURE TEST — deterministic paper partial-exit state machine. */
class PaperPartialExitStateTest {

	private static PaperPartialExitState longState() {
		return PaperPartialExitState.open(PositionSide.LONG, new BigDecimal("100"), new BigDecimal("3"),
				new BigDecimal("99"), new BigDecimal("101.5"), new BigDecimal("102.5"),
				new BigDecimal("104"), new BigDecimal("0.1"));
	}

	private static PaperPartialExitState shortState() {
		return PaperPartialExitState.open(PositionSide.SHORT, new BigDecimal("100"), new BigDecimal("3"),
				new BigDecimal("101"), new BigDecimal("98.5"), new BigDecimal("97.5"),
				new BigDecimal("96"), new BigDecimal("0.1"));
	}

	private static BigDecimal exited(List<ExitEvent> events) {
		return events.stream().map(ExitEvent::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	@Test
	void long_tp1_tp2_tp3() {
		PaperPartialExitState s = longState();
		assertThat(s.onPrice(new BigDecimal("101.5"))).hasSize(1);
		assertThat(s.status()).isEqualTo(Status.PARTIALLY_CLOSED);
		assertThat(s.onPrice(new BigDecimal("102.5"))).hasSize(1);
		List<ExitEvent> last = s.onPrice(new BigDecimal("104"));
		assertThat(last).hasSize(1);
		assertThat(s.status()).isEqualTo(Status.CLOSED);
		assertThat(s.closeReason()).isEqualTo("TAKE_PROFIT");
		assertThat(s.remainingQty()).isEqualByComparingTo("0");
		assertThat(s.netRealizedPnl()).isGreaterThan(BigDecimal.ZERO);
	}

	@Test
	void long_tp1_then_sl_and_tp1_tp2_then_sl() {
		PaperPartialExitState b = longState();
		b.onPrice(new BigDecimal("101.5"));
		b.onPrice(new BigDecimal("99"));
		assertThat(b.status()).isEqualTo(Status.CLOSED);
		assertThat(b.closeReason()).isEqualTo("STOP_LOSS");
		assertThat(b.remainingQty()).isEqualByComparingTo("0");

		PaperPartialExitState c = longState();
		c.onPrice(new BigDecimal("101.5"));
		c.onPrice(new BigDecimal("102.5"));
		c.onPrice(new BigDecimal("99"));
		assertThat(c.closeReason()).isEqualTo("STOP_LOSS");
		assertThat(c.remainingQty()).isEqualByComparingTo("0");
	}

	@Test
	void long_sl_before_tp() {
		PaperPartialExitState s = longState();
		assertThat(s.onPrice(new BigDecimal("99"))).hasSize(1);
		assertThat(s.closeReason()).isEqualTo("STOP_LOSS");
		assertThat(s.netRealizedPnl()).isLessThan(BigDecimal.ZERO);
	}

	@Test
	void short_scenarios() {
		PaperPartialExitState win = shortState();
		win.onPrice(new BigDecimal("98.5"));
		win.onPrice(new BigDecimal("97.5"));
		win.onPrice(new BigDecimal("96"));
		assertThat(win.status()).isEqualTo(Status.CLOSED);
		assertThat(win.closeReason()).isEqualTo("TAKE_PROFIT");
		assertThat(win.netRealizedPnl()).isGreaterThan(BigDecimal.ZERO);

		PaperPartialExitState loss = shortState();
		loss.onPrice(new BigDecimal("101"));
		assertThat(loss.closeReason()).isEqualTo("STOP_LOSS");
	}

	@Test
	void duplicateTicks_fireEachLevelOnce() {
		PaperPartialExitState s = longState();
		assertThat(s.onPrice(new BigDecimal("101.5"))).hasSize(1);
		assertThat(s.onPrice(new BigDecimal("101.5"))).isEmpty(); // duplicate TP1 ignored
		assertThat(s.onPrice(new BigDecimal("102.5"))).hasSize(1);
		assertThat(s.onPrice(new BigDecimal("102.5"))).isEmpty(); // duplicate TP2 ignored
		assertThat(s.onPrice(new BigDecimal("104"))).hasSize(1);
		assertThat(s.onPrice(new BigDecimal("104"))).isEmpty();   // CLOSED -> no further exits
		assertThat(s.onPrice(new BigDecimal("200"))).isEmpty();   // after CLOSED
	}

	@Test
	void sameTickStopWinsBeforeTakeProfit() {
		PaperPartialExitState s = longState();
		// price 98.5 <= SL(99) and would be below TP1 anyway -> SL closes all.
		List<ExitEvent> e = s.onPrice(new BigDecimal("98.5"));
		assertThat(e).hasSize(1);
		assertThat(e.get(0).reason()).isEqualTo("STOP_LOSS");
		assertThat(s.remainingQty()).isEqualByComparingTo("0");
	}

	@Test
	void quantityAndFeeConservation() {
		PaperPartialExitState s = longState();
		BigDecimal exitedTotal = BigDecimal.ZERO;
		exitedTotal = exitedTotal.add(exited(s.onPrice(new BigDecimal("101.5"))));
		exitedTotal = exitedTotal.add(exited(s.onPrice(new BigDecimal("102.5"))));
		exitedTotal = exitedTotal.add(exited(s.onPrice(new BigDecimal("104"))));
		assertThat(exitedTotal.add(s.remainingQty())).isEqualByComparingTo(s.originalQty());
		assertThat(s.remainingQty()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
		assertThat(s.remainingQty()).isLessThanOrEqualTo(s.originalQty());
		assertThat(s.totalFees()).isGreaterThan(s.entryFee());
		assertThat(s.averageExitPrice()).isNotNull();
	}
}
