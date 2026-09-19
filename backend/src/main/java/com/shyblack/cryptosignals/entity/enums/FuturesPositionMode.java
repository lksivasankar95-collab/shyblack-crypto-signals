package com.shyblack.cryptosignals.entity.enums;

/**
 * Binance USDT-M account position mode.
 *
 * ONE_WAY — default; a symbol has at most one net position (BOTH).
 * HEDGE   — LONG and SHORT positions are tracked separately per symbol.
 *
 * Only ONE_WAY is implemented in this drop. HEDGE accounts are refused at
 * the risk gate so we never accidentally place ambiguous orders.
 */
public enum FuturesPositionMode {
	ONE_WAY,
	HEDGE
}
