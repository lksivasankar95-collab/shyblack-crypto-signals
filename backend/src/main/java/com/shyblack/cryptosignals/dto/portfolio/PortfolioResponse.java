package com.shyblack.cryptosignals.dto.portfolio;

import com.shyblack.cryptosignals.entity.enums.AccountType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PortfolioResponse(
		UUID id,
		UUID userId,
		String name,
		AccountType accountType,
		String quoteCurrency,
		BigDecimal totalBalance,
		BigDecimal availableBalance,
		BigDecimal invested,
		Instant createdAt
) {
}
