package com.shyblack.cryptosignals.dto.transaction;

import com.shyblack.cryptosignals.entity.enums.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
		UUID id,
		UUID portfolioId,
		String symbol,
		TransactionType type,
		BigDecimal quantity,
		BigDecimal price,
		Instant executedAt,
		String notes,
		Instant createdAt
) {
}
