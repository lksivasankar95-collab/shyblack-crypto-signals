package com.shyblack.cryptosignals.entity.enums;

/**
 * Immutable lifecycle events emitted while a paper-trading position moves
 * through its state machine. Serves as an audit log.
 */
public enum PositionLifecycleEventType {
	CREATED,
	OPENED,
	TP_HIT,
	SL_HIT,
	MANUAL_CLOSE,
	CLOSED,
	REJECTED
}
