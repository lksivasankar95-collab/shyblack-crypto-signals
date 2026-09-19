package com.shyblack.cryptosignals.entity.enums;

/**
 * Whether an open Futures position's protective SL is safely live on the
 * exchange. Mirrors the SPOT ProtectionStatus but kept separate so Futures
 * and Spot code never cross-import.
 */
public enum FuturesProtectionStatus {
	NOT_APPLICABLE,
	PENDING,
	PROTECTED,
	PROTECTION_FAILED
}
