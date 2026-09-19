package com.shyblack.cryptosignals.exchange;

import com.shyblack.cryptosignals.entity.enums.LiveOrderStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Result of an exchange order call. Fields mirror Binance response fields
 * (executedQty, cummulativeQuoteQty, status) so callers never need to touch
 * exchange JSON directly.
 */
public record ExchangeOrderResult(
		String exchangeOrderId,
		String clientOrderId,
		String symbol,
		LiveOrderStatus status,
		BigDecimal requestedQuantity,
		BigDecimal executedQuantity,
		BigDecimal cumulativeQuoteQty,
		BigDecimal avgFillPrice,
		BigDecimal fee,
		String feeAsset,
		Instant transactionTime,
		String rawResponseSummary
) {}
