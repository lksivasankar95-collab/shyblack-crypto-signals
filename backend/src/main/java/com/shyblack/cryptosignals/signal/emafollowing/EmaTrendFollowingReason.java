package com.shyblack.cryptosignals.signal.emafollowing;

/** Deterministic reason a setup was rejected or a signal was not emitted. */
public enum EmaTrendFollowingReason {
	NONE,
	INSUFFICIENT_DATA,
	NO_BULLISH_TREND,
	SLOPE_NOT_POSITIVE,
	NO_EMA_TRANSITION,
	PRICE_NOT_CONFIRMED,
	WEAK_EMA_SEPARATION,
	RSI_OUT_OF_RANGE,
	VOLUME_TOO_LOW,
	ATR_TOO_LOW,
	INVALID_STOP,
	INSUFFICIENT_RR,
	SCORE_BELOW_MINIMUM,
	SCORE_NOT_CONFIGURED
}
