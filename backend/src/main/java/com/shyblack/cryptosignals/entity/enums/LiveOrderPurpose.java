package com.shyblack.cryptosignals.entity.enums;

/**
 * Why the local system created a particular exchange order. Governs post-fill
 * follow-ups (an ENTRY fill kicks off protective SL orders; a STOP_LOSS fill
 * closes the parent position).
 */
public enum LiveOrderPurpose {
	ENTRY,
	STOP_LOSS,
	TAKE_PROFIT,
	MANUAL_CLOSE,
	EMERGENCY_CLOSE
}
