package com.shyblack.cryptosignals.dto.settings;

import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import java.time.Instant;
import java.util.UUID;

/**
 * Result of a connectivity check.
 *
 * <p>Backed by a real authenticated, read-only exchange call through the configured adapter. The
 * status reflects what the exchange actually answered: {@code ok} is true only when the signed
 * request was accepted. {@code latencyMs} is measured, not simulated. No order is ever placed.
 *
 * <p>When {@code app.live-trading.mode=MOCK} — the default for dev and tests — the configured
 * adapter is the in-process simulator, so this validates the local path rather than a real exchange.
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