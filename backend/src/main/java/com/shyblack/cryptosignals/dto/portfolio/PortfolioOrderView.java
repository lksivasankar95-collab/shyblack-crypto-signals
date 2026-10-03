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
 *
 * <p><b>Actionable cancellation.</b> {@link #orderId()} and {@link #clientOrderId()} are the
 * exchange's own identifiers, which are what a detail view should display but are <em>not</em> what
 * the cancel endpoint accepts: the existing cancel services address a locally persisted
 * {@code LiveOrder} / {@code FuturesOrder} and then look the order up by client order id.
 * {@link #cancelId()} therefore carries that local key, and is resolved per row from the caller's
 * own account.
 *
 * <p>It is null — meaning "cannot be cancelled from here" — when no local record matches (an order
 * placed outside this application), when the account is PAPER (which works no order book at all),
 * or when the exchange state has already reached a terminal status. A cancel control is shown only
 * when this is non-null, so the UI can never offer a button that cannot execute. PAPER orders never
 * exist, so a paper account always reports null rather than a synthetic identifier.
 */
public record PortfolioOrderView(
		/**
		 * Key of the caller's own persisted order record, or null when the order is not addressable
		 * by the cancel endpoint. Never a list index and never synthesised.
		 */
		String cancelId,
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
		Instant updatedAt) {

	/** Back-compat constructor for scopes with no addressable local record. */
	public PortfolioOrderView(
			AccountMode accountMode,
			AccountCategory accountCategory,
			String symbol,
			String side,
			String positionSide,
			String orderType,
			PortfolioOrderStatus status,
			String rawStatus,
			BigDecimal price,
			BigDecimal stopPrice,
			BigDecimal averageFillPrice,
			BigDecimal originalQuantity,
			BigDecimal executedQuantity,
			BigDecimal remainingQuantity,
			Boolean reduceOnly,
			Long orderId,
			String clientOrderId,
			Instant createdAt,
			Instant updatedAt) {
		this(null, accountMode, accountCategory, symbol, side, positionSide, orderType,
				status, rawStatus, price, stopPrice, averageFillPrice, originalQuantity,
				executedQuantity, remainingQuantity, reduceOnly, orderId, clientOrderId,
				createdAt, updatedAt);
	}

	/**
	 * True when this order can actually be cancelled through the existing execution
	 * architecture: a local record must be addressable, and the exchange state must not
	 * already be terminal.
	 */
	public boolean cancellable() {
		return cancelId != null && status != null && status.isOpen();
	}
}