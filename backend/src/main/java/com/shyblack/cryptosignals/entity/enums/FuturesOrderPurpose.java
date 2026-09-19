package com.shyblack.cryptosignals.entity.enums;

/**
 * Why the local system created a particular Futures exchange order.
 *
 * ENTRY         — opens the position (BUY for LONG, SELL for SHORT).
 * STOP_LOSS     — protective reduce-only order (SELL for LONG, BUY for SHORT).
 * TAKE_PROFIT   — reduce-only take-profit; currently TP1 only.
 * MANUAL_CLOSE  — user-initiated reduce-only market close.
 * EMERGENCY_CLOSE — kill-switch driven reduce-only market close.
 */
public enum FuturesOrderPurpose {
	ENTRY,
	STOP_LOSS,
	TAKE_PROFIT,
	MANUAL_CLOSE,
	EMERGENCY_CLOSE
}
