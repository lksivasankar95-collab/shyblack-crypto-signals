package com.shyblack.cryptosignals.dto.paper;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Request to change the initial capital of the caller's paper account. Only
 * accepted while the account has no trading history (no trades, no open
 * positions, no realised P&amp;L) — see
 * {@code PaperTradingAccountService.updateInitialCapital}.
 */
public record PaperCapitalUpdateRequest(
		@NotNull
		@DecimalMin(value = "0.0", inclusive = false, message = "initialCapital must be greater than zero")
		@Digits(integer = 15, fraction = 8)
		BigDecimal initialCapital
) {
}
