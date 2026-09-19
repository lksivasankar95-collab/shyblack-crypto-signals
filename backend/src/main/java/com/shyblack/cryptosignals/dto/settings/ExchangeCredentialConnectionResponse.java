package com.shyblack.cryptosignals.dto.settings;

import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import java.time.Instant;
import java.util.UUID;

/**
 * Result of a connectivity check. Deterministic simulated happy path; no real
 * network calls are made.
 */
public record ExchangeCredentialConnectionResponse(
		UUID id,
		ExchangeName exchange,
		boolean ok,
		String message,
		long latencyMs,
		Instant testedAt,
		ExchangeConnectionStatus status
) {
}