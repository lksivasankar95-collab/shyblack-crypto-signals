package com.shyblack.cryptosignals.entity.enums;

/**
 * NFM liquidation classification (spec §15). Confirmation/context only.
 * {@link #UNKNOWN} is used when liquidation data is unavailable — it is never
 * treated as zero.
 */
public enum LiquidationState {
	NORMAL,
	LONG_SQUEEZE,
	SHORT_SQUEEZE,
	EXTREME_LIQUIDATION,
	UNKNOWN
}
