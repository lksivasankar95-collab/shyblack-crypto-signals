package com.shyblack.cryptosignals.dto.portfolio;

import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import java.util.List;

/**
 * Wallet holdings for one account scope.
 *
 * <p>Only LIVE SPOT has authoritative per-asset holdings. Every other scope reports
 * {@code UNSUPPORTED} with an empty list, so an absent capability is never indistinguishable from an
 * empty wallet.
 */
public record PortfolioHoldingsResponse(
		AccountMode accountMode,
		AccountCategory accountCategory,
		AccountAvailability availability,
		/** Where the balances came from, so local and exchange data are never conflated. */
		String source,
		List<PortfolioHoldingView> holdings,
		String statusMessage) {

	public PortfolioHoldingsResponse {
		holdings = holdings == null ? List.of() : List.copyOf(holdings);
	}
}