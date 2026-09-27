package com.shyblack.cryptosignals.signal.pullback;

/** Deterministic reason a setup was rejected or a signal was not emitted. */
public enum TrendRejectReason {
    NONE,
    INSUFFICIENT_DATA,
    NO_BULLISH_TREND,
    ADX_BELOW_MINIMUM,
    SLOPE_NOT_POSITIVE,
    NO_PULLBACK,
    PULLBACK_TOO_DEEP,
    BEARISH_BREAKDOWN_CANDLE,
    SETUP_EXPIRED,
    SETUP_INVALIDATED,
    RSI_OUT_OF_RANGE,
    RSI_NOT_RECOVERING,
    NO_STRUCTURE_BREAKOUT,
    CANDLE_NOT_CONFIRMED,
    VOLUME_TOO_LOW,
    INVALID_STOP,
    STOP_TOO_WIDE,
    RESISTANCE_TOO_CLOSE,
    INSUFFICIENT_RR,
    SCORE_BELOW_MINIMUM,
    SCORE_NOT_CONFIGURED,
    DUPLICATE_SETUP
}
