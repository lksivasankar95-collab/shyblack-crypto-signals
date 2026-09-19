package com.shyblack.cryptosignals.dto.futures;

import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.entity.enums.FuturesPositionStatus;
import com.shyblack.cryptosignals.entity.enums.FuturesProtectionStatus;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FuturesPositionResponse(
		UUID id,
		UUID signalId,
		UUID entryOrderId,
		UUID stopOrderId,
		String symbol,
		PositionSide positionSide,
		FuturesMarginMode marginMode,
		int leverage,
		BigDecimal quantity,
		BigDecimal entryPrice,
		BigDecimal exitPrice,
		BigDecimal stopLoss,
		BigDecimal takeProfit,
		BigDecimal initialMargin,
		BigDecimal liquidationPrice,
		BigDecimal realizedPnl,
		BigDecimal unrealizedPnl,
		BigDecimal tradingFees,
		BigDecimal fundingFees,
		FuturesPositionStatus status,
		FuturesProtectionStatus protectionStatus,
		Instant openedAt,
		Instant closedAt,
		Instant createdAt
) {}
