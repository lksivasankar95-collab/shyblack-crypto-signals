package com.shyblack.cryptosignals.service.backtest.engine;

import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * In-memory portfolio state for a single backtest run. Not persisted directly —
 * the engine emits equity snapshots + trade rows on every meaningful change.
 *
 * SPOT: {@code invested} tracks the notional deployed into an open BUY.
 * FUTURES: {@code invested} tracks initial margin locked; unrealized P&L
 *          floats separately and is combined into equity mark-to-market.
 */
public class BacktestPortfolio {

	private final BigDecimal initialCapital;
	private final TradingMode mode;
	private final int leverage;

	private BigDecimal availableBalance;
	private BigDecimal realizedPnl = BigDecimal.ZERO;
	private BigDecimal totalFees = BigDecimal.ZERO;
	private BigDecimal peakEquity;

	// One open position at a time (single-symbol backtest). Multi-symbol
	// support requires expanding this to a map, documented as a limitation.
	private OpenPosition open;

	public BacktestPortfolio(BigDecimal initialCapital, TradingMode mode, int leverage) {
		this.initialCapital = initialCapital;
		this.mode = mode;
		this.leverage = Math.max(1, leverage);
		this.availableBalance = initialCapital;
		this.peakEquity = initialCapital;
	}

	public BigDecimal initialCapital() { return initialCapital; }
	public BigDecimal availableBalance() { return availableBalance; }
	public BigDecimal realizedPnl() { return realizedPnl; }
	public BigDecimal totalFees() { return totalFees; }
	public BigDecimal peakEquity() { return peakEquity; }
	public boolean hasOpenPosition() { return open != null; }
	public OpenPosition openPosition() { return open; }

	public BigDecimal equity(BigDecimal markPrice) {
		BigDecimal unreal = unrealized(markPrice);
		BigDecimal e = availableBalance.add(unreal);
		if (open != null && mode == TradingMode.SPOT) {
			e = e.add(open.notionalAtEntry());
		} else if (open != null && mode == TradingMode.FUTURES) {
			e = e.add(open.marginLocked());
		}
		return e.setScale(8, RoundingMode.HALF_UP);
	}

	public BigDecimal unrealized(BigDecimal markPrice) {
		if (open == null || markPrice == null || markPrice.signum() <= 0) return BigDecimal.ZERO;
		return grossPnl(open.side, open.entryPrice, markPrice, open.quantity);
	}

	public BigDecimal drawdownAgainst(BigDecimal equity) {
		if (peakEquity.compareTo(equity) < 0) peakEquity = equity;
		return peakEquity.subtract(equity).max(BigDecimal.ZERO);
	}

	public OpenPosition openPosition(UUID signalId, String symbol, PositionSide side,
			BigDecimal entryPrice, BigDecimal quantity, BigDecimal stopLoss,
			BigDecimal takeProfit, BigDecimal entryFee, BigDecimal liquidationPrice,
			Instant time) {
		BigDecimal notional = entryPrice.multiply(quantity);
		BigDecimal margin = mode == TradingMode.FUTURES
				? notional.divide(BigDecimal.valueOf(leverage), 8, RoundingMode.HALF_UP)
				: notional;
		this.open = new OpenPosition(signalId, symbol, side, entryPrice, quantity,
				stopLoss, takeProfit, notional, margin, entryFee, liquidationPrice,
				leverage, mode, time);
		// Debit margin + entry fee from available; invested/margin tracked on the position.
		this.availableBalance = availableBalance.subtract(margin).subtract(entryFee);
		this.totalFees = totalFees.add(entryFee);
		return open;
	}

	public ClosedPosition closePosition(BigDecimal exitPrice, BigDecimal exitFee) {
		OpenPosition o = this.open;
		if (o == null) throw new IllegalStateException("no open position");
		BigDecimal gross = grossPnl(o.side, o.entryPrice, exitPrice, o.quantity);
		BigDecimal net = gross.subtract(exitFee); // entry fee was already debited
		this.availableBalance = availableBalance
				.add(o.marginLocked)
				.add(gross)
				.subtract(exitFee);
		this.realizedPnl = realizedPnl.add(net.subtract(o.entryFee));
		this.totalFees = totalFees.add(exitFee);
		this.open = null;
		return new ClosedPosition(o, exitPrice, exitFee, gross, net);
	}

	private static BigDecimal grossPnl(PositionSide side, BigDecimal entry,
			BigDecimal exit, BigDecimal qty) {
		BigDecimal diff = side == PositionSide.LONG
				? exit.subtract(entry) : entry.subtract(exit);
		return diff.multiply(qty).setScale(8, RoundingMode.HALF_UP);
	}

	// ── Value types ─────────────────────────────────────────────

	public static final class OpenPosition {
		public final UUID signalId;
		public final String symbol;
		public final PositionSide side;
		public final BigDecimal entryPrice;
		public final BigDecimal quantity;
		public final BigDecimal stopLoss;
		public final BigDecimal takeProfit;
		private final BigDecimal notionalAtEntry;
		private final BigDecimal marginLocked;
		public final BigDecimal entryFee;
		public final BigDecimal liquidationPrice;
		public final int leverage;
		public final TradingMode mode;
		public final Instant entryTime;

		OpenPosition(UUID signalId, String symbol, PositionSide side, BigDecimal entryPrice,
				BigDecimal quantity, BigDecimal stopLoss, BigDecimal takeProfit,
				BigDecimal notionalAtEntry, BigDecimal marginLocked, BigDecimal entryFee,
				BigDecimal liquidationPrice, int leverage, TradingMode mode, Instant entryTime) {
			this.signalId = signalId;
			this.symbol = symbol;
			this.side = side;
			this.entryPrice = entryPrice;
			this.quantity = quantity;
			this.stopLoss = stopLoss;
			this.takeProfit = takeProfit;
			this.notionalAtEntry = notionalAtEntry;
			this.marginLocked = marginLocked;
			this.entryFee = entryFee;
			this.liquidationPrice = liquidationPrice;
			this.leverage = leverage;
			this.mode = mode;
			this.entryTime = entryTime;
		}

		public BigDecimal notionalAtEntry() { return notionalAtEntry; }
		public BigDecimal marginLocked() { return marginLocked; }
	}

	public record ClosedPosition(OpenPosition entry, BigDecimal exitPrice,
			BigDecimal exitFee, BigDecimal grossPnl, BigDecimal netPnl) {}
}
