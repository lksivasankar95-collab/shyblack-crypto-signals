package com.shyblack.cryptosignals.service.backtest.historical;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable historical candle used by the backtest engine. Distinct from
 * live streaming DTOs so the engine's contract is self-contained.
 */
public record HistoricalCandle(
		Instant openTime,
		BigDecimal open,
		BigDecimal high,
		BigDecimal low,
		BigDecimal close,
		BigDecimal volume,
		Instant closeTime
) {
	/** OHLC sanity gate — Phase 32. */
	public boolean isValid() {
		if (open == null || high == null || low == null || close == null) return false;
		if (open.signum() <= 0 || high.signum() <= 0
				|| low.signum() <= 0 || close.signum() <= 0) return false;
		if (low.compareTo(open) > 0 || low.compareTo(close) > 0) return false;
		if (high.compareTo(open) < 0 || high.compareTo(close) < 0) return false;
		if (high.compareTo(low) < 0) return false;
		return true;
	}

	/** Adapt to the existing {@link KlineResponse} shape used by IndicatorEngine. */
	public KlineResponse toKline() {
		return new KlineResponse(
				openTime.toEpochMilli(),
				open, high, low, close, volume,
				closeTime.toEpochMilli());
	}
}
