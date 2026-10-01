package com.shyblack.cryptosignals.entity.enums;

/**
 * NFM funding crowding classification (spec §14). Used as a risk filter,
 * never as a standalone entry trigger.
 */
public enum FundingState {
	NORMAL,
	ELEVATED_LONG,
	EXTREME_LONG,
	ELEVATED_SHORT,
	EXTREME_SHORT,
	UNKNOWN
}
