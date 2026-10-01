package com.shyblack.cryptosignals.dto.portfolio;

import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.PositionStatus;
import java.math.BigDecimal;

/**
 * One position within a single account scope.
 *
 * <p>Every numeric field is nullable and {@code null} always means "this scope's source does not
 * provide the value" — for example a futures position has no take-profit ladder, and Binance Spot has
 * no position concept at all. A null is never rendered as a zero.
 *
 * <p>{@code accountCategory} is null only for a simulated position with no originating signal, which
 * cannot be attributed to a market category; such a position appears in {@code MAIN} alone and is
 * never assigned to a category by guess.
 */
public record PortfolioPositionView(
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
}