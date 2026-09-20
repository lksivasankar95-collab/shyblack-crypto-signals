package com.shyblack.cryptosignals.entity.enums;

/**
 * How to resolve the ambiguity when a single OHLC candle touches both SL and TP.
 *
 * From OHLC alone we cannot know the intrabar sequence. The default is the
 * CONSERVATIVE choice — assume the stop hit first (safer for the trader in
 * a real market and prevents optimistic reports).
 *
 * TP_FIRST is available for research only; never a production default.
 * LOWER_TIMEFRAME is deferred — noted in the report as a future enhancement.
 */
public enum BacktestSameCandlePolicy {
	CONSERVATIVE,   // alias for SL_FIRST — the shipping default
	SL_FIRST,
	TP_FIRST,
	REQUIRE_LOWER_TIMEFRAME
}
