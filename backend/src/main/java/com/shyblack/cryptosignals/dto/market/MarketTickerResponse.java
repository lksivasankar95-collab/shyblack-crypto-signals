package com.shyblack.cryptosignals.dto.market;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketTickerResponse(
		String symbol,
		String name,
		BigDecimal price,
		BigDecimal change24h,
		BigDecimal changePercent24h,
		BigDecimal volume24h,
		BigDecimal high24h,
		BigDecimal low24h,
		Instant updatedAt
) {
}
