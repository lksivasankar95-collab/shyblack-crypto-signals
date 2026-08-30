package com.shyblack.cryptosignals.dto.position;

import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.PositionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PositionResponse(
		UUID id,
		UUID portfolioId,
		String symbol,
		PositionSide side,
		BigDecimal size,
		BigDecimal entryPrice,
		BigDecimal currentPrice,
		BigDecimal margin,
		BigDecimal liquidationPrice,
		PositionStatus status,
		Instant closedAt,
		Instant createdAt
) {
}
