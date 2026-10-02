package com.shyblack.cryptosignals.dto.portfolio;

import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * One position that has been closed, reconstructed from real exchange or paper records.
 *
 * <p>Field provenance is strict and differs per field:
 *
 * <ul>
 *   <li>{@link #realizedPnl()} is the sum of the {@code realizedPnl} values the exchange itself
 *       reported on the closing fills. It is never recomputed from entry and exit prices.</li>
 *   <li>{@link #entryPrice()} and {@link #exitPrice()} are quantity-weighted averages of the actual
 *       fills on each side of the round trip. They are null when the corresponding side of the round
 *       trip is not present in the requested window — a position opened before the window has no
 *       observable opening fill, so no entry price is reported rather than a guessed one.</li>
 *   <li>{@link #funding()} is null for the same reason: a funding-fee income record is not
 *       attributable to one specific position, so it is never assigned to one.</li>
 *   <li>{@link #leverage()}, {@link #marginType()} and the position identifiers are null unless a
 *       source reported them.</li>
 * </ul>
 *
 * <p>A null field therefore always means "not determinable from available data". It is never a zero.
 */
public record PortfolioClosedPositionView(
		AccountMode accountMode,
		AccountCategory accountCategory,
		String symbol,
		/** LONG or SHORT. Null only when the source reports no position side at all. */
		String side,
		BigDecimal entryPrice,
		BigDecimal exitPrice,
		BigDecimal quantity,
		/** Exchange-reported realized P&L summed over the closing fills. Null when unreported. */
		BigDecimal realizedPnl,
		/** Sum of the commissions the source reported on this round trip. Null when unreported. */
		BigDecimal fees,
		/** Always null: funding fees are account-level income records, not position attributes. */
		BigDecimal funding,
		String feesAsset,
		/** ISOLATED or CROSS when reported. */
		String marginType,
		Integer leverage,
		Instant openedAt,
		Instant closedAt,
		/** closedAt minus openedAt. Null when either timestamp is unknown. */
		Duration duration,
		/** Distinct exchange order ids behind this round trip, oldest first. */
		List<Long> orderIds,
		/** Distinct exchange trade ids behind this round trip, oldest first. */
		List<Long> tradeIds) {

	public PortfolioClosedPositionView {
		orderIds = orderIds == null ? List.of() : List.copyOf(orderIds);
		tradeIds = tradeIds == null ? List.of() : List.copyOf(tradeIds);
	}
}