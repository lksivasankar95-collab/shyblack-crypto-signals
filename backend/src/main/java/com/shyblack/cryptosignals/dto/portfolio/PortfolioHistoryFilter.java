package com.shyblack.cryptosignals.dto.portfolio;

import java.util.Locale;
import java.util.Set;

/**
 * Optional narrowing applied to an already-fetched, window-bounded exchange read.
 *
 * <p>Every field is nullable and null means "no narrowing". A blank value is treated as absent so an
 * empty query-parameter box does not filter everything away.
 *
 * <p>The filter is applied <em>after</em> the window and the record cap have been applied by the
 * exchange read. That ordering is deliberate and is the reason {@code complete} on the response is
 * still meaningful: a narrow filter inside a wide window can return fewer rows than the cap even
 * though more matching records exist earlier in the window. Narrowing the window is therefore the
 * correct tool for finding a specific old record, and the filter is for reading a known period.
 */
public record PortfolioHistoryFilter(
		String symbol,
		String side,
		String orderType,
		String status,
		String positionSide) {

	public static final PortfolioHistoryFilter NONE = new PortfolioHistoryFilter(null, null, null, null, null);

	/**
	 * Normalises the caller-supplied values. Unknown enum spellings are rejected outright rather than
	 * silently dropped, so a typo surfaces as a bad request instead of an unfiltered result that looks
	 * like "no matches".
	 */
	public static PortfolioHistoryFilter of(
			String symbol, String side, String orderType, String status, String positionSide) {

		return new PortfolioHistoryFilter(
				normaliseSymbol(symbol),
				oneOf(upper(side), "SIDE", Set.of("BUY", "SELL", "LONG", "SHORT")),
				upper(orderType),
				oneOf(upper(status), "STATUS", Set.of(
						"NEW", "PARTIALLY_FILLED", "FILLED", "CANCELED", "REJECTED", "EXPIRED", "UNKNOWN")),
				oneOf(upper(positionSide), "POSITION_SIDE", Set.of("BOTH", "LONG", "SHORT")));
	}

	public boolean hasNarrowing() {
		return symbol != null || side != null || orderType != null || status != null || positionSide != null;
	}

	private static String normaliseSymbol(String raw) {
		String value = upper(raw);
		return value;
	}

	private static String upper(String raw) {
		if (raw == null) {
			return null;
		}
		String trimmed = raw.trim();
		return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
	}

	private static String oneOf(String value, String field, Set<String> allowed) {
		if (value == null) {
			return null;
		}
		if (!allowed.contains(value)) {
			throw new com.shyblack.cryptosignals.exception.BadRequestException(
					"Unsupported " + field + " '" + value + "'; expected one of " + allowed);
		}
		return value;
	}
}