package com.shyblack.cryptosignals.dto.paper;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;

/**
 * Request to change the protective levels of an open paper position.
 *
 * <p>Every field is optional: a null level is left exactly as it is, so a
 * caller can move only the stop without disturbing the target. There is no way
 * to clear a level — protection is never silently removed, only repositioned.
 * Pass an explicit non-positive value to be rejected (a stop at zero has no
 * level to trigger on).</p>
 */
public record PaperPositionRiskRequest(
		@DecimalMin(value = "0.0", message = "stopLoss must be greater than zero")
		@Digits(integer = 15, fraction = 8)
		BigDecimal stopLoss,

		@DecimalMin(value = "0.0", message = "takeProfit must be greater than zero")
		@Digits(integer = 15, fraction = 8)
		BigDecimal takeProfit
) {
}
