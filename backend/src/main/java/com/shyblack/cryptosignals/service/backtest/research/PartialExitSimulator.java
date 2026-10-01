package com.shyblack.cryptosignals.service.backtest.research;

import com.shyblack.cryptosignals.entity.enums.PositionSide;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic partial-exit trade lifecycle (TP1/TP2/TP3 + SL) for NFM, LONG
 * and SHORT. Isolated from the existing single-TP {@code BacktestEngine} so it
 * can be adopted without regressing current behaviour.
 *
 * <p>Same-bar convention (conservative, deterministic): if a bar touches the
 * stop, the ENTIRE remaining position closes at the stop BEFORE any take-profit
 * on that same bar (mirrors the engine's SL_FIRST policy). Take-profits are then
 * processed in ascending level order within a bar, each exiting a fraction of the
 * ORIGINAL quantity; the final TP closes the remainder.</p>
 *
 * <p>No look-ahead: bars are consumed strictly in the order given.</p>
 */
public final class PartialExitSimulator {

	private PartialExitSimulator() {
	}

	public record Bar(BigDecimal high, BigDecimal low) {}

	public record ExitFill(BigDecimal price, BigDecimal quantity, String reason,
			BigDecimal fee, BigDecimal grossPnl) {}

	public record Lifecycle(
			PositionSide side,
			BigDecimal entry,
			BigDecimal originalQty,
			BigDecimal remainingQty,
			BigDecimal entryFee,
			BigDecimal totalFees,
			BigDecimal grossPnl,
			BigDecimal netPnl,
			String finalReason,
			boolean closed,
			List<ExitFill> fills) {}

	/** Default TP split: 1/3, 1/3, remainder (exact by construction). */
	public static final BigDecimal DEFAULT_TP1_FRACTION = new BigDecimal("0.33333333");
	public static final BigDecimal DEFAULT_TP2_FRACTION = new BigDecimal("0.33333333");

	public static Lifecycle run(PositionSide side, BigDecimal entry, BigDecimal originalQty,
			BigDecimal stopLoss, BigDecimal tp1, BigDecimal tp2, BigDecimal tp3, BigDecimal feePct,
			List<Bar> bars) {
		return run(side, entry, originalQty, stopLoss, tp1, tp2, tp3, feePct,
				DEFAULT_TP1_FRACTION, DEFAULT_TP2_FRACTION, bars);
	}

	public static Lifecycle run(PositionSide side, BigDecimal entry, BigDecimal originalQty,
			BigDecimal stopLoss, BigDecimal tp1, BigDecimal tp2, BigDecimal tp3, BigDecimal feePct,
			BigDecimal tp1Fraction, BigDecimal tp2Fraction, List<Bar> bars) {

		BigDecimal fee = feePct == null ? BigDecimal.ZERO
				: feePct.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
		BigDecimal entryFee = notional(entry, originalQty).multiply(fee).setScale(8, RoundingMode.HALF_UP);

		BigDecimal qty1 = originalQty.multiply(tp1Fraction).setScale(8, RoundingMode.DOWN);
		BigDecimal qty2 = originalQty.multiply(tp2Fraction).setScale(8, RoundingMode.DOWN);

		BigDecimal remaining = originalQty;
		BigDecimal gross = BigDecimal.ZERO;
		BigDecimal exitFees = BigDecimal.ZERO;
		List<ExitFill> fills = new ArrayList<>();
		String finalReason = null;
		boolean closed = false;
		boolean tp1Done = false, tp2Done = false, tp3Done = false;

		for (Bar bar : bars) {
			if (closed) {
				break;
			}
			if (stopTouched(side, bar, stopLoss)) {
				Fill f = exit(side, entry, remaining, stopLoss, fee);
				fills.add(f.fill());
				gross = gross.add(f.fill().grossPnl());
				exitFees = exitFees.add(f.fill().fee());
				remaining = BigDecimal.ZERO;
				finalReason = "STOP_LOSS";
				closed = true;
				break;
			}
			// TP1 (fires at most once)
			if (!tp1Done && remaining.signum() > 0 && tpTouched(side, bar, tp1)) {
				BigDecimal q = qty1.min(remaining);
				if (q.signum() > 0) {
					Fill f = exit(side, entry, q, tp1, fee);
					fills.add(f.fill());
					gross = gross.add(f.fill().grossPnl());
					exitFees = exitFees.add(f.fill().fee());
					remaining = remaining.subtract(q);
					tp1Done = true;
				}
			}
			// TP2 (fires at most once)
			if (!tp2Done && remaining.signum() > 0 && tpTouched(side, bar, tp2)) {
				BigDecimal q = qty2.min(remaining);
				if (q.signum() > 0) {
					Fill f = exit(side, entry, q, tp2, fee);
					fills.add(f.fill());
					gross = gross.add(f.fill().grossPnl());
					exitFees = exitFees.add(f.fill().fee());
					remaining = remaining.subtract(q);
					tp2Done = true;
				}
			}
			// TP3 closes the remainder (fires at most once)
			if (!tp3Done && remaining.signum() > 0 && tpTouched(side, bar, tp3)) {
				Fill f = exit(side, entry, remaining, tp3, fee);
				fills.add(f.fill());
				gross = gross.add(f.fill().grossPnl());
				exitFees = exitFees.add(f.fill().fee());
				remaining = BigDecimal.ZERO;
				tp3Done = true;
				finalReason = "TAKE_PROFIT";
				closed = true;
				break;
			}
		}

		BigDecimal totalFees = entryFee.add(exitFees).setScale(8, RoundingMode.HALF_UP);
		BigDecimal net = gross.subtract(totalFees).setScale(8, RoundingMode.HALF_UP);
		return new Lifecycle(side, entry, originalQty, remaining, entryFee, totalFees, gross, net,
				finalReason, closed, fills);
	}

	private record Fill(ExitFill fill) {}

	private static Fill exit(PositionSide side, BigDecimal entry, BigDecimal qty, BigDecimal price,
			BigDecimal fee) {
		BigDecimal gross = side == PositionSide.LONG
				? price.subtract(entry).multiply(qty)
				: entry.subtract(price).multiply(qty);
		gross = gross.setScale(8, RoundingMode.HALF_UP);
		BigDecimal exitFee = price.multiply(qty).multiply(fee).setScale(8, RoundingMode.HALF_UP);
		return new Fill(new ExitFill(price, qty, null, exitFee, gross));
	}

	private static boolean stopTouched(PositionSide side, Bar bar, BigDecimal stop) {
		if (stop == null) return false;
		return side == PositionSide.LONG
				? bar.low().compareTo(stop) <= 0
				: bar.high().compareTo(stop) >= 0;
	}

	private static boolean tpTouched(PositionSide side, Bar bar, BigDecimal tp) {
		if (tp == null) return false;
		return side == PositionSide.LONG
				? bar.high().compareTo(tp) >= 0
				: bar.low().compareTo(tp) <= 0;
	}

	private static BigDecimal notional(BigDecimal price, BigDecimal qty) {
		return price.multiply(qty).setScale(8, RoundingMode.HALF_UP);
	}
}
