package com.shyblack.cryptosignals.dto.portfolio;

import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import java.time.Instant;
import java.util.List;

/**
 * History for one account scope, over an explicitly resolved window.
 *
 * <p>The resolved window is always returned so a caller can tell exactly which period the data
 * covers. {@link #complete()} is false when the record cap stopped the result before the end of the
 * window, in which case the history is partial and says so rather than pretending to be complete.
 *
 * <p>Records are newest first. Ties are broken by the natural identifier (trade id then order id)
 * so the ordering is total and reproducible.
 */
public record PortfolioHistoryResponse(
		AccountMode accountMode,
		AccountCategory accountCategory,
		AccountAvailability availability,
		/** EXCHANGE or LOCAL_PAPER. */
		String source,
		String entryType,
		Instant windowFrom,
		Instant windowTo,
		boolean complete,
		List<PortfolioHistoryEntry> entries,
		String statusMessage) {

	public PortfolioHistoryResponse {
		entries = entries == null ? List.of() : List.copyOf(entries);
	}
}