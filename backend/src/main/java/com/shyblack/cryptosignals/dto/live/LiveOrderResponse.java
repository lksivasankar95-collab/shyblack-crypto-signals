package com.shyblack.cryptosignals.dto.live;

import com.shyblack.cryptosignals.entity.enums.LiveOrderPurpose;
import com.shyblack.cryptosignals.entity.enums.LiveOrderStatus;
import com.shyblack.cryptosignals.entity.enums.LiveOrderType;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.ProtectionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LiveOrderResponse(
		UUID id,
		UUID signalId,
		UUID parentOrderId,
		String clientOrderId,
		String exchangeOrderId,
		String symbol,
		PositionSide side,
		LiveOrderType type,
		LiveOrderPurpose purpose,
		LiveOrderStatus status,
		ProtectionStatus protectionStatus,
		BigDecimal requestedQuantity,
		BigDecimal executedQuantity,
		BigDecimal remainingQuantity,
		BigDecimal price,
		BigDecimal stopPrice,
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
