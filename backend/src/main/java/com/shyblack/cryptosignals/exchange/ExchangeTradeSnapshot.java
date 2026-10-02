package com.shyblack.cryptosignals.exchange;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One executed trade (fill) as reported by the exchange trade-history endpoint.
 *
 * <p>A fill is only ever sourced from the exchange. It is never reconstructed from a signal, a local
 * order, a position quantity or a price difference, because such a reconstruction would be a
 * fabricated fill.
 *
 * <p>{@code tradeId} is the natural identity of a fill and is the deduplication key across pages.
 */
public record ExchangeTradeSnapshot(
		String symbol,
		Long tradeId,
		Long orderId,
		/** Raw exchange side: BUY or SELL. */
		String side,
		BigDecimal price,
		BigDecimal quantity,
		BigDecimal quoteQuantity,
		BigDecimal commission,
		String commissionAsset,
		/** Null when the exchange does not report maker/taker. */
		Boolean maker,
		Instant tradedAt) {}
