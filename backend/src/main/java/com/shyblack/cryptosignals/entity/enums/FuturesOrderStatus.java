package com.shyblack.cryptosignals.entity.enums;

/**
 * Futures order state machine — mirrors Binance Futures order statuses.
 * Every mutation must be justified by exchange evidence. No CREATED -> FILLED
 * without acknowledgement + execution report.
 */
public enum FuturesOrderStatus {
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
