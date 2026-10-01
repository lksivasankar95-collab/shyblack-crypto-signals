package com.shyblack.cryptosignals.dto.portfolio;

import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Normalized read model for one {@link AccountMode} x {@link AccountCategory} scope.
 *
 * <p>Every numeric field is nullable and {@code null} always means "this source does not provide
 * the value". A null is never a zero: a zero may only appear when the underlying source explicitly
 * confirmed that the value is zero. {@link #availability()} explains why a value is missing, and
 * {@link #statusMessage()} carries the human-readable reason.
 *
 * <p>Field provenance is deliberately narrow:
 * <ul>
 *   <li>{@code equity} / {@code availableBalance} / {@code invested} — a single balance figure.
 *       Null for scopes that share one wallet across categories (paper SPOT and FUTURES) and for a
 *       derived aggregate with no single source (LIVE MAIN), so capital is never double counted.</li>
 *   <li>{@code realizedPnl} — lifetime realized P&amp;L attributable to the scope.</li>
 *   <li>{@code unrealizedPnl} — open P&amp;L, null when no mark price is known.</li>
 *   <li>{@code totalPositionCount} / {@code openPositionCount} — null when the scope is unsupported
 *       rather than zero, so an absent capability is never indistinguishable from an empty account.</li>
 *   <li>{@code orderCount} — only for scopes backed by an exchange order store.</li>
 * </ul>
 */
public record PortfolioAccountView(
		AccountMode accountMode,
		AccountCategory accountCategory,
		AccountAvailability availability,
		ExchangeName exchange,
		ExchangeConnectionStatus connectionStatus,
		String quoteCurrency,
		BigDecimal equity,
		BigDecimal availableBalance,
		BigDecimal invested,
		BigDecimal realizedPnl,
		BigDecimal unrealizedPnl,
		Integer totalPositionCount,
		Integer openPositionCount,
		Integer orderCount,
		Instant lastSyncedAt,
		String statusMessage) {

	/** True when the scope has no underlying source at all, rather than an empty one. */
	public boolean isUnavailable() {
		return availability != AccountAvailability.AVAILABLE;
	}
}