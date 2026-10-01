package com.shyblack.cryptosignals.entity.enums;

/**
 * Availability of account data for one {@link AccountMode} x {@link AccountCategory} scope.
 *
 * <p>Exists so that a missing balance, an unconnected exchange account and a platform capability
 * that is not integrated are all reported honestly. No value may ever be silently replaced by a
 * zero balance.
 */
public enum AccountAvailability {

	/** Authoritative data is present for this scope. */
	AVAILABLE,

	/** A synchronization is currently in flight. */
	SYNCING,

	/** No exchange account has ever been connected for this scope. */
	NOT_CONNECTED,

	/** Previously connected, but the connection is no longer usable. */
	DISCONNECTED,

	/**
	 * The integrated API does not expose this scope. Used, for example, for a "main wallet"
	 * balance, which no Binance endpoint provides.
	 */
	UNAVAILABLE,

	/**
	 * The platform does not support this scope at all and no engine exists. Currently the case for
	 * {@link AccountCategory#OPTIONS}.
	 */
	UNSUPPORTED,

	/** The last synchronization attempt failed. */
	ERROR
}