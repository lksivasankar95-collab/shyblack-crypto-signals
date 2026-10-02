package com.shyblack.cryptosignals.dto.settings;

import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import java.time.Instant;
import java.util.UUID;

/**
 * Result of a connectivity check.
 *
 * <p>Backed by a real authenticated, read-only exchange call through the configured adapter.
 * {@code ok} is true only when the exchange accepted the signed request; {@code latencyMs} is
 * measured, never simulated. No order is ever placed, cancelled or modified.
 *
 * <p>{@code validationStatus} is the machine-readable failure class. It exists because {@code ok}
 * alone cannot distinguish an invalid API key from a network fault, which need completely
 * different user actions. {@code status} remains the stored credential lifecycle, and
 * {@code message} is authored by {@code ExchangeConnectionClassifier}: no API key, secret,
 * signature, authorization header or raw exchange payload appears in any field.
 *
 * @param id credential that was tested
 * @param exchange exchange the credential belongs to
 * @param scope market scope actually validated: SPOT or FUTURES
 * @param ok true only when the exchange authenticated the credential
 * @param message safe, human-readable outcome
 * @param latencyMs measured round-trip time
 * @param testedAt when the attempt finished
 * @param status persisted credential lifecycle after the attempt
 * @param validationStatus machine-readable outcome
 * @param canTrade whether the key carries trading permission; false with a read-only key
 */
public record ExchangeCredentialConnectionResponse(
		UUID id,
		ExchangeName exchange,
		String scope,
		boolean ok,
		String message,
		long latencyMs,
		Instant testedAt,
		ExchangeConnectionStatus status,
		ConnectionValidationStatus validationStatus,
		boolean canTrade
) {

	/** Overload preserving the original field order for existing callers. */
	public ExchangeCredentialConnectionResponse(
			UUID id,
			ExchangeName exchange,
			boolean ok,
			String message,
			long latencyMs,
			Instant testedAt,
			ExchangeConnectionStatus status) {
		this(id, exchange, null, ok, message, latencyMs, testedAt, status,
				ok ? ConnectionValidationStatus.CONNECTED : ConnectionValidationStatus.UNKNOWN_ERROR,
				false);
	}
}