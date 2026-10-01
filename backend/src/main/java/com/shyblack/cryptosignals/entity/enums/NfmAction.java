package com.shyblack.cryptosignals.entity.enums;

/**
 * NFM trade state (spec §20). {@link #NO_TRADE} and {@link #WAIT_CONFIRMATION}
 * are first-class, expected outcomes.
 */
public enum NfmAction {
	LONG,
	SHORT,
	WAIT_CONFIRMATION,
	NO_TRADE,
	RISK_BLOCKED;

	public boolean isDirectional() {
		return this == LONG || this == SHORT;
	}
}
