package com.shyblack.cryptosignals.dto.backtest;

import java.math.BigDecimal;
import java.time.Instant;

public record BacktestEquityPointResponse(
		Instant time,
		BigDecimal equity,
		BigDecimal availableBalance,
		BigDecimal unrealizedPnl,
		BigDecimal realizedPnl,
		BigDecimal peakEquity,
		BigDecimal drawdown,
		BigDecimal drawdownPct
) {}
