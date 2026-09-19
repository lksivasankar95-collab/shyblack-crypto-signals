package com.shyblack.cryptosignals.entity.enums;

/**
 * Why a paper-trading position was closed. Stored on the Position row.
 */
public enum CloseReason {
	STOP_LOSS,
	TAKE_PROFIT,
	MANUAL,
	SIGNAL_EXPIRED,
	SYSTEM,
	RESET
}
