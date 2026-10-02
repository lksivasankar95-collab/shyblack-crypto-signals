package com.shyblack.cryptosignals.exchange.futures;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One executed futures trade as reported by the exchange user-trades endpoint.
 *
 * <p>Only ever sourced from the exchange; never reconstructed locally. {@code tradeId} is the
 * natural identity used for deduplication across pages.
 */
public record FuturesTradeSnapshot(
		String symbol,
		Long tradeId,
		Long orderId,
		String side,
		String positionSide,
		BigDecimal price,
		BigDecimal quantity,
		BigDecimal quoteQuantity,
		BigDecimal commission,
		String commissionAsset,
		/** Realized P&amp;L attributed to this trade by the exchange. Null when not supplied. */
		BigDecimal realizedPnl,
		/** Raw exchange margin asset. Null when not supplied. */
		String marginAsset,
		Boolean maker,
		Instant tradedAt) {}
