package com.shyblack.cryptosignals.dto.portfolio;

import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import java.util.List;

/**
 * Positions for exactly one account scope.
 *
 * <p>{@link #availability()} exists so an empty {@link #positions()} list is never ambiguous. For
 * Binance Spot the list is empty because spot has no open-position concept, and the scope reports
 * {@code UNSUPPORTED} rather than implying an account that genuinely holds nothing. The same applies
 * to Options, and to LIVE {@code MAIN}, which cannot be served without mixing the spot and futures
 * wallets.
 */
public record PortfolioPositionsResponse(
		AccountMode accountMode,
		AccountCategory accountCategory,
		AccountAvailability availability,
		List<PortfolioPositionView> positions,
		String statusMessage) {

	public PortfolioPositionsResponse {
		positions = positions == null ? List.of() : List.copyOf(positions);
	}
}