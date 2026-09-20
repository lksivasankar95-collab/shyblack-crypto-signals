package com.shyblack.cryptosignals.dto.backtest;

import com.shyblack.cryptosignals.entity.enums.PositionSide;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BacktestSignalResponse(
		UUID id,
		String symbol,
		PositionSide side,
		Instant candleTime,
		BigDecimal referencePrice,
		BigDecimal entryPrice,
		BigDecimal stopLoss,
		BigDecimal takeProfit,
		String strategyId,
		String strategyVersion,
		String notes
) {}
