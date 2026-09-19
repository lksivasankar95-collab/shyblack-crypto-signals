package com.shyblack.cryptosignals.exchange.futures;

import com.shyblack.cryptosignals.entity.enums.FuturesOrderStatus;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import java.math.BigDecimal;
import java.time.Instant;

public record FuturesOrderResult(
		String exchangeOrderId,
		String clientOrderId,
		String symbol,
		PositionSide side,
		PositionSide positionSide,
		FuturesOrderStatus status,
		boolean reduceOnly,
		BigDecimal requestedQuantity,
		BigDecimal executedQuantity,
		BigDecimal cumulativeQuoteQty,
		BigDecimal avgFillPrice,
		BigDecimal fee,
		String feeAsset,
		Instant transactionTime,
		String raw
) {}
