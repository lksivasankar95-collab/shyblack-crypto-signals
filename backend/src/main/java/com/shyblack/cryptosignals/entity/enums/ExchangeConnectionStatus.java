package com.shyblack.cryptosignals.entity.enums;

/**
 * Lifecycle state of an exchange credential connection.
 * {@code CONNECTED} requires a verified live-trading-enabled user; it is never
 * set automatically.
 */
public enum ExchangeConnectionStatus {
	NOT_CONNECTED,
	CONNECTING,
	CONNECTED,
	FAILED,
	REVOKED
}
