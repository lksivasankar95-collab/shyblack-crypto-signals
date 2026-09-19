package com.shyblack.cryptosignals.entity.enums;

/**
 * Deterministic reasons a live order was blocked before being sent to the
 * exchange. Every rejection must have exactly one of these.
 */
public enum LiveTradingRiskReason {
	OK,
	TRADING_DISABLED,
	KILL_SWITCH_ACTIVE,
	ACCOUNT_DISCONNECTED,
	ACCOUNT_NOT_ACTIVATED,
	CREDENTIALS_INVALID,
	SYMBOL_NOT_ALLOWED,
	INVALID_SYMBOL_RULES,
	INSUFFICIENT_BALANCE,
	MAX_POSITIONS_REACHED,
	MAX_NOTIONAL_EXCEEDED,
	DAILY_LOSS_LIMIT,
	DUPLICATE_SIGNAL,
	SIGNAL_EXPIRED,
	INVALID_STOP_LOSS,
	INVALID_QUANTITY,
	/** Signal trading mode is not SPOT — the live module only supports SPOT in this drop. */
	UNSUPPORTED_TRADING_MODE,
	/** SPOT does not permit opening a naked SHORT position. */
	UNSUPPORTED_SIDE,
	/** An open entry position already exists for this symbol; refuse to stack. */
	EXISTING_POSITION
}

