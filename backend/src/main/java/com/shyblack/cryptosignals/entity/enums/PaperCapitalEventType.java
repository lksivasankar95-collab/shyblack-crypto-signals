package com.shyblack.cryptosignals.entity.enums;

/**
 * Type of a paper-account capital movement. Audited in
 * {@code PaperCapitalEvent} so a balance can always be explained.
 */
public enum PaperCapitalEventType {

	/** Seed balance applied when the paper account was first created. */
	INITIAL,

	/** User added capital via the Portfolio capital sheet. */
	ADD,

	/** User withdrew capital via the Portfolio capital sheet. */
	REDUCE,

	/** Account was reset back to the configured initial balance. */
	RESET
}
