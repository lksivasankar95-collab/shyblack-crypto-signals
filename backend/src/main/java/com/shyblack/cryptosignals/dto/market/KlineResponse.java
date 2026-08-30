package com.shyblack.cryptosignals.dto.market;

import java.math.BigDecimal;

public record KlineResponse(
		long openTime,
		BigDecimal open,
		BigDecimal high,
		BigDecimal low,
		BigDecimal close,
		BigDecimal volume,
		long closeTime
) {
}
