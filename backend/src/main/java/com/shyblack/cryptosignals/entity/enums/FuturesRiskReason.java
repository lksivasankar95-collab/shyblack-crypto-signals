package com.shyblack.cryptosignals.entity.enums;

/**
 * Deterministic reasons a Futures order was blocked before hitting the
 * exchange. Independent from the SPOT risk enum — Futures has its own set of
 * rules (leverage, margin, liquidation, hedge mode) that don't apply to Spot.
 */
public enum FuturesRiskReason {
	OK,
	FUTURES_DISABLED,
	ACCOUNT_NOT_ACTIVE,
	ACKNOWLEDGEMENT_REQUIRED,
	KILL_SWITCH_ENABLED,
	INVALID_SIGNAL,
	STALE_SIGNAL,
	DUPLICATE_SIGNAL,
	POSITION_ALREADY_EXISTS,
	LEVERAGE_EXCEEDED,
	MARGIN_INSUFFICIENT,
	RISK_LIMIT_EXCEEDED,
	STOP_LOSS_REQUIRED,
	STOP_DISTANCE_INVALID,
	POSITION_SIZE_INVALID,
	SYMBOL_NOT_SUPPORTED,
	LIQUIDATION_RISK,
	UNSUPPORTED_POSITION_MODE,
	UNSUPPORTED_MARGIN_MODE,
	MAX_POSITIONS_REACHED,
	DAILY_LOSS_LIMIT
}
