package com.shyblack.cryptosignals.entity.enums;

/**
 * Account mode of the unified Portfolio read model.
 *
 * <p>This is the {@code accountMode} discriminator (PAPER / LIVE) required by the master
 * Portfolio architecture. It is deliberately a <em>separate</em> enum from the legacy
 * {@link AccountType} discriminator already stored on {@code User} and {@code Portfolio}:
 * {@code AccountType} keeps its original {@code {LIVE, PAPER}} meaning and is never renamed, so
 * existing rows, derived repository queries and the paper-trading call sites are untouched.
 *
 * <p>{@link #fromAccountType(AccountType)} is the single conversion point between the two.
 */
public enum AccountMode {

	/** Simulated account. No exchange interaction, no real orders. */
	PAPER,

	/** Real exchange account. Binance is the source of truth for its state. */
	LIVE;

	/**
	 * Converts the legacy discriminator to the read-model mode.
	 *
	 * @throws IllegalArgumentException if {@code accountType} is {@code null}; the mapping is
	 *     total for every declared value and must never silently degrade to a default.
	 */
	public static AccountMode fromAccountType(AccountType accountType) {
		if (accountType == null) {
			throw new IllegalArgumentException("accountType must not be null");
		}
		return accountType == AccountType.LIVE ? LIVE : PAPER;
	}

	/** Converts back to the legacy discriminator. Total for every declared value. */
	public AccountType toAccountType() {
		return this == LIVE ? AccountType.LIVE : AccountType.PAPER;
	}

	public boolean isPaper() {
		return this == PAPER;
	}

	public boolean isLive() {
		return this == LIVE;
	}
}