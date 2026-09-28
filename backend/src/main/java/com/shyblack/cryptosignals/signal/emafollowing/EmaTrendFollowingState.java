package com.shyblack.cryptosignals.signal.emafollowing;

/** Causally-derived lifecycle state for one symbol at the current closed candle. */
public enum EmaTrendFollowingState {
	INSUFFICIENT_DATA,
	NO_TREND,
	NO_TRANSITION,
	WAITING_CONFIRMATION,
	SIGNAL_READY
}
