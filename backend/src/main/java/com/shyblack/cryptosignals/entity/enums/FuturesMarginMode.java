package com.shyblack.cryptosignals.entity.enums;

/**
 * Binance USDT-M margin mode. Default is ISOLATED — CROSS is documented as
 * unsupported in this drop and rejected at the risk gate.
 */
public enum FuturesMarginMode {
	ISOLATED,
	CROSS
}
