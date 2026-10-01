package com.shyblack.cryptosignals.service.paper;

import com.shyblack.cryptosignals.entity.enums.PositionSide;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure paper-trading partial-exit state machine for NFM (TP1/TP2/TP3 + SL).
 *
 * <p>Encapsulates the paper lifecycle, quantity conservation, fee/PnL
 * accounting, and idempotency (each TP fires once; a CLOSED position never
 * transitions again). It is intentionally persistence-agnostic so it can be
 * driven by the paper tick/price pipeline.</p>
 *
 * <p>Same-tick rule: if a single price touches both SL and a TP, SL wins
 * (conservative, deterministic) — consistent with the backtest simulator.</p>
 *
 * <p>No real orders; no look-ahead (each call consumes one observed price).</p>
 */
public final class PaperPartialExitState {

	public enum Status { OPEN, PARTIALLY_CLOSED, CLOSED }

	public record ExitEvent(String reason, BigDecimal price, BigDecimal quantity,
			BigDecimal fee, BigDecimal grossPnl) {}

	private final PositionSide side;
	private final BigDecimal entry;
	private final BigDecimal originalQty;
	private final BigDecimal stopLoss;
	private final BigDecimal tp1;
	private final BigDecimal tp2;
	private final BigDecimal tp3;
	private final BigDecimal feeRate;
	private final BigDecimal tp1Fraction;
	private final BigDecimal tp2Fraction;
	private final BigDecimal entryFee;

	private BigDecimal remainingQty;
	private BigDecimal grossPnl = BigDecimal.ZERO;
	private BigDecimal exitFees = BigDecimal.ZERO;
	private BigDecimal sumExitNotional = BigDecimal.ZERO;
	private BigDecimal sumExitQty = BigDecimal.ZERO;
	private boolean tp1Hit, tp2Hit, tp3Hit;
	private Status status = Status.OPEN;
	private String closeReason;
	private Instant closedAt;

	private PaperPartialExitState(PositionSide side, BigDecimal entry, BigDecimal originalQty,
			BigDecimal stopLoss, BigDecimal tp1, BigDecimal tp2, BigDecimal tp3, BigDecimal feePct,
			BigDecimal tp1Fraction, BigDecimal tp2Fraction) {
		this.side = side;
		this.entry = entry;
		this.originalQty = originalQty;
		this.stopLoss = stopLoss;
		this.tp1 = tp1;
		this.tp2 = tp2;
		this.tp3 = tp3;
		this.feeRate = feePct == null ? BigDecimal.ZERO
				: feePct.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
		this.tp1Fraction = tp1Fraction;
		this.tp2Fraction = tp2Fraction;
		this.remainingQty = originalQty;
		this.entryFee = notional(entry, originalQty).multiply(this.feeRate).setScale(8, RoundingMode.HALF_UP);
	}

	public static PaperPartialExitState open(PositionSide side, BigDecimal entry, BigDecimal quantity,
			BigDecimal stopLoss, BigDecimal tp1, BigDecimal tp2, BigDecimal tp3, BigDecimal feePct) {
		return new PaperPartialExitState(side, entry, quantity, stopLoss, tp1, tp2, tp3, feePct,
				new BigDecimal("0.33333333"), new BigDecimal("0.33333333"));
	}

	/** Apply one observed price; returns the exits (possibly empty). Idempotent. */
	public List<ExitEvent> onPrice(BigDecimal price) {
		List<ExitEvent> events = new ArrayList<>();
		if (status == Status.CLOSED || price == null || remainingQty.signum() <= 0) {
			return events;
		}
		// SL first (conservative same-tick rule)
		if (stopLoss != null && stopTouched(price)) {
			events.add(exit(price, remainingQty, "STOP_LOSS"));
			remainingQty = BigDecimal.ZERO;
			closeReason = "STOP_LOSS";
			status = Status.CLOSED;
			closedAt = Instant.now();
			return events;
		}
		if (!tp1Hit && tp1 != null && tpTouched(price)) {
			events.add(exit(tp1, fractionQty(tp1Fraction), "TP1"));
			tp1Hit = true;
		}
		if (!tp2Hit && tp2 != null && remainingQty.signum() > 0 && tpTouched(price)) {
			events.add(exit(tp2, fractionQty(tp2Fraction), "TP2"));
			tp2Hit = true;
		}
		if (!tp3Hit && tp3 != null && remainingQty.signum() > 0 && tpTouched(price)) {
			events.add(exit(tp3, remainingQty, "TP3"));
			tp3Hit = true;
		}
		if (remainingQty.signum() == 0) {
			closeReason = "TAKE_PROFIT";
			status = Status.CLOSED;
			closedAt = Instant.now();
		} else if (tp1Hit || tp2Hit || tp3Hit) {
			status = Status.PARTIALLY_CLOSED;
		}
		return events;
	}

	private BigDecimal fractionQty(BigDecimal fraction) {
		return originalQty.multiply(fraction).setScale(8, RoundingMode.DOWN).min(remainingQty);
	}

	private ExitEvent exit(BigDecimal price, BigDecimal qty, String reason) {
		BigDecimal gross = side == PositionSide.LONG
				? price.subtract(entry).multiply(qty)
				: entry.subtract(price).multiply(qty);
		gross = gross.setScale(8, RoundingMode.HALF_UP);
		BigDecimal fee = price.multiply(qty).multiply(feeRate).setScale(8, RoundingMode.HALF_UP);
		remainingQty = remainingQty.subtract(qty);
		grossPnl = grossPnl.add(gross);
		exitFees = exitFees.add(fee);
		sumExitNotional = sumExitNotional.add(price.multiply(qty));
		sumExitQty = sumExitQty.add(qty);
		return new ExitEvent(reason, price, qty, fee, gross);
	}

	private boolean stopTouched(BigDecimal price) {
		return side == PositionSide.LONG ? price.compareTo(stopLoss) <= 0 : price.compareTo(stopLoss) >= 0;
	}

	private boolean tpTouched(BigDecimal price) {
		BigDecimal activeTp = !tp1Hit ? tp1 : (!tp2Hit ? tp2 : tp3);
		if (activeTp == null) return false;
		return side == PositionSide.LONG ? price.compareTo(activeTp) >= 0 : price.compareTo(activeTp) <= 0;
	}

	private static BigDecimal notional(BigDecimal price, BigDecimal qty) {
		return price.multiply(qty).setScale(8, RoundingMode.HALF_UP);
	}

	// ── read-side ───────────────────────────────────────────────────────────

	public Status status() { return status; }
	public BigDecimal remainingQty() { return remainingQty; }
	public BigDecimal originalQty() { return originalQty; }
	public BigDecimal grossPnl() { return grossPnl; }
	public BigDecimal entryFee() { return entryFee; }
	public BigDecimal totalFees() { return entryFee.add(exitFees).setScale(8, RoundingMode.HALF_UP); }
	public BigDecimal netRealizedPnl() {
		return grossPnl.subtract(entryFee).subtract(exitFees).setScale(8, RoundingMode.HALF_UP);
	}
	public BigDecimal averageExitPrice() {
		return sumExitQty.signum() == 0 ? null
				: sumExitNotional.divide(sumExitQty, 8, RoundingMode.HALF_UP);
	}
	public boolean tp1Hit() { return tp1Hit; }
	public boolean tp2Hit() { return tp2Hit; }
	public boolean tp3Hit() { return tp3Hit; }
	public String closeReason() { return closeReason; }
	public Instant closedAt() { return closedAt; }
	public BigDecimal unrealizedPnl(BigDecimal markPrice) {
		if (status == Status.CLOSED || markPrice == null) return BigDecimal.ZERO;
		BigDecimal u = side == PositionSide.LONG
				? markPrice.subtract(entry).multiply(remainingQty)
				: entry.subtract(markPrice).multiply(remainingQty);
		return u.setScale(8, RoundingMode.HALF_UP);
	}
}
