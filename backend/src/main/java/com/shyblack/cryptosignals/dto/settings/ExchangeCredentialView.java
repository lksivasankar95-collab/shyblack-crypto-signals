package com.shyblack.cryptosignals.dto.settings;

import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import java.time.Instant;
import java.util.UUID;

/**
 * Masked exchange credential. Fields always hold masked representations of the
 * API key and secret, never the raw values.
 */
public record ExchangeCredentialView(
		UUID id,
		ExchangeName exchange,
		ExchangeConnectionStatus status,
		String label,
		String displayName,
		String maskedApiKey,
		String maskedSecret,
		Instant createdAt,
		Instant updatedAt
) {
}