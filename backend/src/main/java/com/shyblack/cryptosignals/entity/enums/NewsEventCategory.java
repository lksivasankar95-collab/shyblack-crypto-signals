package com.shyblack.cryptosignals.entity.enums;

/**
 * High-level NFM event taxonomy group (spec §5 A–F). Purely descriptive —
 * the category never decides trade direction on its own.
 */
public enum NewsEventCategory {
	MACRO,
	CENTRAL_BANK,
	CRYPTO_STRUCTURAL,
	REGULATION,
	SECURITY,
	MARKET_SHOCK,
	OTHER
}
