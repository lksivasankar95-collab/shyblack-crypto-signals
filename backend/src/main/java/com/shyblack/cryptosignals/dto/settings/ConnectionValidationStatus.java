package com.shyblack.cryptosignals.dto.settings;

/**
 * Machine-readable outcome of a credential validation attempt.
 *
 * <p>Exists so a caller can act on the failure class rather than parsing prose. The previous
 * contract collapsed every cause into {@code FAILED}, which made an invalid API key
 * indistinguishable from a transient network fault.
 *
 * <p>Deliberately separate from {@code ExchangeConnectionStatus}, which models the stored
 * credential's lifecycle. A credential is only marked {@code FAILED} when the failure is
 * attributable to the credential itself; a timeout says nothing about the key.
 */
public enum ConnectionValidationStatus {

	/** The exchange accepted the signed, authenticated account read. */
	CONNECTED,

	/**
	 * The exchange rejected the API key or secret: HTTP 401, or Binance codes
	 * {@code -2014}, {@code -2015}, {@code -1022}.
	 *
	 * <p>Distinguishing this from {@link #NETWORK_ERROR} is the point of the enum: one needs a
	 * corrected key, the other needs a retry.
	 */
	INVALID_CREDENTIALS,

	/**
	 * The signature did not verify, which usually means a wrong API secret rather than a wrong
	 * key. Binance reports it as HTTP 401 with code {@code -1022}.
	 */
	INVALID_SIGNATURE,

	/**
	 * The key authenticated but lacks the required permission. Surfaced as its own state so a
	 * read-only key is never mistaken for a broken one, and so a UI can say "read-only" rather
	 * than "failed".
	 */
	PERMISSION_DENIED,

	/** The request timestamp fell outside the receive window (Binance code {@code -1021}). */
	TIMESTAMP_ERROR,

	/** Rate limited or IP banned: HTTP 429, HTTP 418, or code {@code -1003}. */
	RATE_LIMITED,

	/** The request never reached the exchange: DNS failure, connection refused, reset. */
	NETWORK_ERROR,

	/** The request reached the exchange but no reply arrived within the read timeout. */
	TIMEOUT,

	/** The exchange answered with a server-side error, HTTP 5xx. */
	BINANCE_API_ERROR,

	/**
	 * A client-side rejection that is none of the above: HTTP 4xx with an unrecognised code.
	 *
	 * <p>Explicit rather than folded into {@link #BINANCE_API_ERROR}, because "your request was
	 * wrong" and "Binance is broken" call for different actions.
	 */
	BINANCE_CLIENT_ERROR,

	/**
	 * The failure could not be classified, including a malformed or non-JSON response.
	 *
	 * <p>Present so an unexpected failure is reported honestly instead of being guessed at.
	 */
	UNKNOWN_ERROR;

	/** True only when the exchange actually authenticated the credential. */
	public boolean isConnected() {
		return this == CONNECTED;
	}

	/**
	 * True when the failure is attributable to the credential itself, and therefore the only class
	 * that justifies persisting {@code ExchangeConnectionStatus.FAILED}.
	 *
	 * <p>Transport, timeout and rate-limit faults say nothing about the key's validity, so
	 * overwriting a previously valid status with {@code FAILED} on those would be a lie.
	 */
	public boolean isCredentialAttributable() {
		return this == INVALID_CREDENTIALS
				|| this == INVALID_SIGNATURE
				|| this == PERMISSION_DENIED;
	}
}