package com.shyblack.cryptosignals.dto.portfolio;

import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One normalised history record for a single account scope.
 *
 * <p>Orders, fills and income share this shape so one endpoint can serve all three views, but
 * {@link #entryType()} and {@link #source()} keep them distinguishable. Locally recorded paper
 * activity is reported as {@link #source()} {@code LOCAL_PAPER} and exchange activity as
 * {@code EXCHANGE}, so reconstructed history is never presented as exchange history.
 *
 * <p>Every field is nullable. An order that never filled has no fill price, and an exchange that
 * omits a commission reports a null rather than a zero.
 */
public record PortfolioHistoryEntry(
		/** ORDER, TRADE or INCOME. */
		String entryType,
		AccountMode accountMode,
		AccountCategory accountCategory,
		/** EXCHANGE for authoritative exchange data, LOCAL_PAPER for simulated activity. */
		String source,
		String symbol,
		/** Exchange order id when the source supplies one. */
		Long orderId,
		/** Exchange trade or income transaction id when the source supplies one. */
		Long tradeId,
		/** BUY, SELL, LONG or SHORT as reported. */
		String side,
		String status,
		BigDecimal price,
		BigDecimal quantity,
		BigDecimal quoteQuantity,
		BigDecimal fee,
		String feeAsset,
		/** Realized P&amp;L when the source reports it; never derived locally. */
		BigDecimal realizedPnl,
		Instant occurredAt) {}
