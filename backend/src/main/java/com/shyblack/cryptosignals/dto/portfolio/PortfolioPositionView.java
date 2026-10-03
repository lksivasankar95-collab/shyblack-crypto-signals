package com.shyblack.cryptosignals.dto.portfolio;

import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.PositionStatus;
import java.math.BigDecimal;

/**
 * One position within a single account scope.
 *
 * <p>Every numeric field is nullable and {@code null} always means "this scope's source does not
 * provide the value" �?" for example a futures position has no take-profit ladder, and Binance Spot has
 * no position concept at all. A null is never rendered as a zero.
 *
 * <p>{@code accountCategory} is null only for a simulated position with no originating signal, which
 * cannot be attributed to a market category; such a position appears in {@code MAIN} alone and is
 * never assigned to a category by guess.
 *
 * <p><b>Identifier semantics.</b> {@code positionId} is the key of the underlying position record in
 * the scope's own source, and its meaning therefore differs by scope:
 *
 * <ul>
 *   <li><b>PAPER</b> — the {@code Position} primary key, directly addressable by the paper
 *       trading actions (close, reposition stop/target).</li>
 *   <li><b>LIVE FUTURES</b> — the key of the stored exchange position snapshot for this symbol.</li>
 *   <li><b>LIVE SPOT</b> — always null. A spot wallet holding is an asset balance, not a position,
 *       so there is no addressable record and no native stop/target to expose. Reported as null
 *       rather than a fabricated identifier.</li>
 * </ul>
 *
 * <p>The identifier is scoped to the authenticated caller's own account and carries no authority on
 * its own: every action endpoint re-verifies ownership before it will act, so holding another user's
 * identifier grants nothing. It is not a list index and is never synthesised client-side.
 */
public record PortfolioPositionView(
		/** Key of the underlying position record in this scope's source; see the class note. */
		String positionId,
		com.shyblack.cryptosignals.entity.enums.AccountMode accountMode,
		com.shyblack.cryptosignals.entity.enums.AccountCategory accountCategory,
		String symbol,
		PositionSide side,
		BigDecimal quantity,
		BigDecimal entryPrice,
		BigDecimal currentPrice,
		BigDecimal stopLoss,
		BigDecimal takeProfit1,
		BigDecimal takeProfit2,
		BigDecimal takeProfit3,
		BigDecimal liquidationPrice,
		Integer leverage,
		BigDecimal notional,
		BigDecimal unrealizedPnl,
		BigDecimal realizedPnl,
		PositionStatus status) {

	public PortfolioPositionView(
			com.shyblack.cryptosignals.entity.enums.AccountMode accountMode,
			com.shyblack.cryptosignals.entity.enums.AccountCategory accountCategory,
			String symbol,
			PositionSide side,
			BigDecimal quantity,
			BigDecimal entryPrice,
			BigDecimal currentPrice,
			BigDecimal stopLoss,
			BigDecimal takeProfit1,
			BigDecimal takeProfit2,
			BigDecimal takeProfit3,
			BigDecimal liquidationPrice,
			Integer leverage,
			BigDecimal notional,
			BigDecimal unrealizedPnl,
			BigDecimal realizedPnl,
			PositionStatus status) {
		this(null, accountMode, accountCategory, symbol, side, quantity, entryPrice,
				currentPrice, stopLoss, takeProfit1, takeProfit2, takeProfit3,
				liquidationPrice, leverage, notional, unrealizedPnl, realizedPnl, status);
	}
}