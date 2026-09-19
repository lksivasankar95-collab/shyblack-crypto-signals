package com.shyblack.cryptosignals.dto.paper;

import com.shyblack.cryptosignals.entity.enums.CloseReason;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.PositionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaperPositionResponse(
		UUID id,
		UUID signalId,
		String symbol,
		PositionSide side,
		BigDecimal quantity,
		BigDecimal entryPrice,
		BigDecimal currentPrice,
		BigDecimal exitPrice,
		BigDecimal stopLoss,
		BigDecimal takeProfit1,
		BigDecimal takeProfit2,
		BigDecimal takeProfit3,
		BigDecimal notional,
		BigDecimal entryFee,
		BigDecimal exitFee,
		BigDecimal realizedPnl,
		BigDecimal unrealizedPnl,
		BigDecimal unrealizedPnlPct,
		PositionStatus status,
		CloseReason closeReason,
		Instant openedAt,
		Instant closedAt,
		Instant createdAt
) {}
