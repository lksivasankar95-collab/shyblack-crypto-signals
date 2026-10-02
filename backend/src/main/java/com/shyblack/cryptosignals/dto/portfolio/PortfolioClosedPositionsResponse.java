package com.shyblack.cryptosignals.dto.portfolio;

import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import java.util.List;

/**
 * Closed positions for one account scope, answering "what trades were closed".
 *
 * <p>{@link #partial()} is true when the reconstruction could not observe the whole round trip for
 * at least one record, which is the honest signal that some entry prices or durations are
 * unavailable. It is not an error: the records themselves are real.
 */
public record PortfolioClosedPositionsResponse(
		AccountMode accountMode,
		AccountCategory accountCategory,
		AccountAvailability availability,
		/** EXCHANGE or LOCAL_PAPER. */
		String source,
		/** True when at least one record had an unobservable side of its round trip. */
		boolean partial,
		List<PortfolioClosedPositionView> positions,
		String statusMessage) {

	public PortfolioClosedPositionsResponse {
		positions = positions == null ? List.of() : List.copyOf(positions);
	}
}