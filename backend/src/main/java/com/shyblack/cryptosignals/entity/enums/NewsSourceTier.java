package com.shyblack.cryptosignals.entity.enums;

/**
 * NFM source quality tier (spec §7). Tier 4 never directly generates a trade —
 * it may only produce a watch/untradeable event.
 */
public enum NewsSourceTier {
	TIER_1,
	TIER_2,
	TIER_3,
	TIER_4;

	/** Only tiers 1–3 are eligible to become tradeable events. */
	public boolean tradeable() {
		return this != TIER_4;
	}
}
