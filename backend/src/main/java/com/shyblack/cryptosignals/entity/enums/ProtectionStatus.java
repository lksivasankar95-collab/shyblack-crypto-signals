package com.shyblack.cryptosignals.entity.enums;

/**
 * Tracks whether an entry order's exchange-side protective SELL is safely
 * placed. Surfaced to UI + ops so a filled entry never looks protected when
 * it isn't.
 *
 * NOT_APPLICABLE  — order isn't an ENTRY (e.g. it's a STOP_LOSS itself).
 * PENDING         — entry filled, protective SL not yet acknowledged.
 * PROTECTED       — protective SL is live on the exchange.
 * PROTECTION_FAILED — SL submission failed after fill; requires operator attention.
 */
public enum ProtectionStatus {
	NOT_APPLICABLE,
	PENDING,
	PROTECTED,
	PROTECTION_FAILED
}
