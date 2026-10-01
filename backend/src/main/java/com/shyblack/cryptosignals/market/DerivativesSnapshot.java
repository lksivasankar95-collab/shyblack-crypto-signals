package com.shyblack.cryptosignals.market;

import java.math.BigDecimal;

/**
 * Point-in-time derivatives context for an NFM symbol (spec §12–§15). Every
 * field is nullable and paired with an availability flag — unavailable data is
 * never silently treated as zero (spec §47).
 */
public record DerivativesSnapshot(
		BigDecimal markPrice,
		BigDecimal indexPrice,
		BigDecimal lastFundingRate,
		BigDecimal openInterest,
		BigDecimal openInterestChangePct,
		BigDecimal longLiquidationVolume,
		BigDecimal shortLiquidationVolume,
		boolean fundingAvailable,
		boolean openInterestAvailable,
		boolean liquidationAvailable
) {

	public static DerivativesSnapshot unavailable() {
		return new DerivativesSnapshot(null, null, null, null, null, null, null, false, false, false);
	}
}
