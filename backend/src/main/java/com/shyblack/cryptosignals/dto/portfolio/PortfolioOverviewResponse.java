package com.shyblack.cryptosignals.dto.portfolio;

import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import java.util.List;

/**
 * Portfolio overview for one account mode.
 *
 * <p>Carries exactly one {@link AccountMode}: a response never mixes PAPER and LIVE, so the client
 * can never misread one as the other. {@link #accountMode()} is always populated, including when the
 * caller relied on the default, so a client never has to guess which mode it received.
 *
 * <p>{@link #accounts()} always contains all four categories, including those reported as
 * {@code UNSUPPORTED} or {@code UNAVAILABLE}, so the shape is stable and a missing entry is never
 * mistaken for an empty account.
 */
public record PortfolioOverviewResponse(
		AccountMode accountMode,
		List<AccountCategory> categories,
		List<PortfolioAccountView> accounts) {

	public PortfolioOverviewResponse {
		categories = categories == null ? List.of() : List.copyOf(categories);
		accounts = accounts == null ? List.of() : List.copyOf(accounts);
	}
}