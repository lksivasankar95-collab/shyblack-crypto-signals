package com.shyblack.cryptosignals.dto.live;

import java.math.BigDecimal;

public record LivePerformanceResponse(
		int totalOrders,
		int filledEntries,
		int rejections,
		BigDecimal totalFees,
		BigDecimal totalNotional
) {}
