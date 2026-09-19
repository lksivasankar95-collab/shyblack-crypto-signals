package com.shyblack.cryptosignals.entity.enums;

/**
 * Live order state machine. Every mutation must be justified by exchange
 * evidence or an explicit local decision. No direct CREATED -> FILLED.
 */
public enum LiveOrderStatus {
	CREATED,
	SUBMITTING,
	SUBMITTED,
	ACKNOWLEDGED,
	PARTIALLY_FILLED,
	FILLED,
	CANCEL_REQUESTED,
	CANCELLED,
	REJECTED,
	EXPIRED,
	FAILED,
	UNKNOWN,
	RECONCILING
}
