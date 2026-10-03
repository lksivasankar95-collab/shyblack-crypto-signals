package com.shyblack.cryptosignals.dto.paper;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Request to add to or withdraw from the caller's paper capital.
 *
 * <p>Amount is always a positive magnitude; the direction comes from the
 * endpoint, so a negative amount is rejected rather than silently flipping the
 * operation.</p>
 */
public record PaperCapitalAdjustmentRequest(
		@NotNull(message = "amount is required")
		@DecimalMin(value = "0.0", inclusive = false, message = "amount must be greater than zero")
		@Digits(integer = 15, fraction = 8)
		BigDecimal amount,

		@Size(max = 500, message = "reason must be 500 characters or fewer")
		String reason
) {
}
