package com.shyblack.cryptosignals.dto.backtest;

import com.shyblack.cryptosignals.entity.enums.BacktestExitReason;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BacktestTradeResponse(
		UUID id,
		UUID signalId,
		String symbol,
		PositionSide side,
		BigDecimal quantity,
		BigDecimal entryPrice,
		BigDecimal exitPrice,
		BigDecimal notional,
		BigDecimal stopLoss,
		BigDecimal takeProfit,
		BigDecimal entryFee,
		BigDecimal exitFee,
		BigDecimal grossPnl,
		BigDecimal netPnl,
		BigDecimal rMultiple,
		int leverage,
		BacktestExitReason exitReason,
		Instant entryTime,
		Instant exitTime,
		long holdingSeconds
) {}
