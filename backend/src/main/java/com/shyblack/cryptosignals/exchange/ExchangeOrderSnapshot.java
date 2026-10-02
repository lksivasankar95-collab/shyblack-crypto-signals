package com.shyblack.cryptosignals.exchange;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One order as reported by the exchange history endpoints.
 *
 * <p>Distinct from {@link ExchangeOrderResult}, which is shaped for the response to placing an
 * order. Every field here is nullable because the exchange omits values depending on order state:
 * an unfilled limit order has no average fill price, and a cancelled order has no completion time.
 * A missing value is never replaced with zero.
 */
public record ExchangeOrderSnapshot(
		String symbol,
		Long orderId,
		String clientOrderId,
		/** Raw exchange side: BUY or SELL. */
		String side,
		/** Raw exchange order type: LIMIT, MARKET, STOP_LOSS_LIMIT, ... */
		String orderType,
		/** Raw exchange status: NEW, PARTIALLY_FILLED, FILLED, CANCELED, REJECTED, EXPIRED. */
		String status,
		BigDecimal price,
		/** Trigger price for a stop order. Null for every order type that has no trigger. */
		BigDecimal stopPrice,
		BigDecimal originalQuantity,
		BigDecimal executedQuantity,
		BigDecimal cumulativeQuoteQuantity,
		Instant createdAt,
		Instant updatedAt) {}
