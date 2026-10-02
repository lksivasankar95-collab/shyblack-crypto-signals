package com.shyblack.cryptosignals.dto.portfolio;

import com.shyblack.cryptosignals.entity.enums.AccountAvailability;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.AccountMode;
import java.util.List;

/**
 * Open orders for exactly one account scope, answering "what is resting on the book right now".
 *
 * <p>The source is the exchange, never local intent. An order that is {@code UNKNOWN} is still
 * listed, because an undetermined order is exactly the one a user needs to see.
 *
 * <p>PAPER has no resting-order concept — the paper engine executes a position rather than working
 * an order book — so it reports {@link AccountAvailability#UNSUPPORTED} rather than an empty list
 * that would read as "no orders".
 */
public record PortfolioOpenOrdersResponse(
		AccountMode accountMode,
		AccountCategory accountCategory,
		AccountAvailability availability,
		/** EXCHANGE or LOCAL_PAPER. */
		String source,
		List<PortfolioOrderView> orders,
		String statusMessage) {

	public PortfolioOpenOrdersResponse {
		orders = orders == null ? List.of() : List.copyOf(orders);
	}
}