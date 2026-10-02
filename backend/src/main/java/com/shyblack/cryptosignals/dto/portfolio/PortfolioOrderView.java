package com.shyblack.cryptosignals.dto.portfolio;

import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.PortfolioOrderStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One order resting on the exchange right now, or one historical order, in the shape a
 * Binance-style account screen needs.
 *
 * <p>Every field originates from an order-read endpoint. Nothing is inferred: an order that has not
 * filled has a null {@link #averageFillPrice()} and a null {@link #stopPrice()} unless the exchange
 * actually reported one.
 *
 * <p>{@link #status()} is normalised through {@link PortfolioOrderStatus} so an undeterminable
 * state stays {@link PortfolioOrderStatus#UNKNOWN} and can never be read as FILLED.
 */
public record PortfolioOrderView(
		AccountMode accountMode,
		AccountCategory accountCategory,
		String symbol,
		/** BUY or SELL as reported. */
		String side,
		/** BOTH, LONG or SHORT for futures; null for spot, which has no position side. */
		String positionSide,
		/** LIMIT, MARKET, STOP_LOSS_LIMIT, ... as reported. */
		String orderType,
		PortfolioOrderStatus status,
		/** The raw exchange status string, preserved so an unmapped state stays visible. */
		String rawStatus,
		BigDecimal price,
		/** Trigger price. Null when the order type has no trigger. */
		BigDecimal stopPrice,
		/** Exchange-reported average fill price. Null when nothing has filled. */
		BigDecimal averageFillPrice,
		BigDecimal originalQuantity,
		BigDecimal executedQuantity,
		/** originalQuantity minus executedQuantity, or null when either is unknown. */
		BigDecimal remainingQuantity,
		Boolean reduceOnly,
		Long orderId,
		String clientOrderId,
		Instant createdAt,
		Instant updatedAt) {}