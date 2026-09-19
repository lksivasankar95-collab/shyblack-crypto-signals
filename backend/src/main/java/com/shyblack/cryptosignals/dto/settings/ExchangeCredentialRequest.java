package com.shyblack.cryptosignals.dto.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;

/**
 * Request body for connecting an exchange API credential.
 */
public record ExchangeCredentialRequest(
		@NotNull ExchangeName exchange,
		@NotBlank String apiKey,
		@NotBlank String apiSecret,
		String label,
		String displayName
) {
}