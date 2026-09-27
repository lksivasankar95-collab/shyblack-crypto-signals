package com.shyblack.cryptosignals.signal.pullback;

/**
 * Lifecycle state of the TREND_PULLBACK setup for one symbol at one candle.
 *
 * The state is derived causally from the closed-candle window on every
 * evaluation — it is never carried as mutable cross-call state, which is
 * what allows live signal generation and backtesting to share one code path
 * deterministically.
 */
public enum TrendPullbackState {
    INSUFFICIENT_DATA,
    NO_TREND,
    TREND_CONFIRMED,
    PULLBACK_DETECTED,
    PULLBACK_VALID,
    WAITING_CONFIRMATION,
    SIGNAL_READY,
    SIGNAL_GENERATED,
    INVALIDATED,
    EXPIRED
}
