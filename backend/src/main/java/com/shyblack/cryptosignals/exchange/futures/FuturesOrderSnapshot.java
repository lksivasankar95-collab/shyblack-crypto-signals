package com.shyblack.cryptosignals.exchange.futures;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One futures order as reported by the exchange history endpoints.
 *
 * <p>Separate from the spot snapshot because futures adds {@code positionSide} and {@code reduceOnly},
 * and because its field set differs. As with the spot snapshot every field is nullable.
 */
public record FuturesOrderSnapshot(
		String symbol,
		Long orderId,
		String clientOrderId,
		/** Raw exchange side: BUY or SELL. */
		String side,
		/** Raw exchange position side: BOTH, LONG or SHORT. Null when the exchange omits it. */
		String positionSide,
		String orderType,
		String status,
		Boolean reduceOnly,
		BigDecimal price,
		BigDecimal originalQuantity,
		BigDecimal executedQuantity,
		BigDecimal cumulativeQuoteQuantity,
		Instant createdAt,
		Instant updatedAt) {}
