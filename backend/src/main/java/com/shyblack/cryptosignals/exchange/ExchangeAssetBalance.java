package com.shyblack.cryptosignals.exchange;

import java.math.BigDecimal;

/**
 * One asset balance row as reported by the exchange.
 *
 * <p>{@code free} and {@code locked} are nullable because the exchange either reported the asset or
 * it did not appear in the response at all. An absent asset must be represented by the absence of
 * the row, never by a zero balance.
 */
public record ExchangeAssetBalance(String asset, BigDecimal free, BigDecimal locked) {

	public ExchangeAssetBalance {
		if (asset == null || asset.isBlank()) {
			throw new IllegalArgumentException("asset is required");
		}
	}

	/** Free + locked, or null when either component is unknown. */
	public BigDecimal total() {
		if (free == null || locked == null) {
			return null;
		}
		return free.add(locked);
	}
}