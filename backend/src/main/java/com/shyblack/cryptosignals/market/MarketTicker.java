package com.shyblack.cryptosignals.market;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketTicker(
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
