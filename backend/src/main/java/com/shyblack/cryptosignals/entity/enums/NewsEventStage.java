package com.shyblack.cryptosignals.entity.enums;

/**
 * NFM event lifecycle stage (spec §6). Stage is part of the signal
 * deduplication identity so the same event does not re-fire signals across
 * its lifecycle.
 */
public enum NewsEventStage {
	RUMOR,
	REPORT,
	OFFICIAL_CONFIRMATION,
	EXPECTATION,
	APPROVAL,
	LAUNCH,
	FOLLOW_UP,
	FLOW
}
