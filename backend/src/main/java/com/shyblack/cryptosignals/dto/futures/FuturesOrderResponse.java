package com.shyblack.cryptosignals.dto.futures;

import com.shyblack.cryptosignals.entity.enums.FuturesOrderPurpose;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderStatus;
import com.shyblack.cryptosignals.entity.enums.FuturesOrderType;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FuturesOrderResponse(
		UUID id,
		UUID signalId,
		UUID parentOrderId,
		String clientOrderId,
		String exchangeOrderId,
		String symbol,
		PositionSide side,
		PositionSide positionSide,
		FuturesOrderType type,
		FuturesOrderPurpose purpose,
		FuturesOrderStatus status,
		boolean reduceOnly,
		int leverage,
		BigDecimal requestedQuantity,
		BigDecimal executedQuantity,
		BigDecimal avgFillPrice,
		BigDecimal cumulativeQuoteQty,
		BigDecimal fees,
		String feeAsset,
		String rejectReason,
		Instant submittedAt,
		Instant lastFillAt,
		Instant completedAt,
		Instant createdAt
) {}
