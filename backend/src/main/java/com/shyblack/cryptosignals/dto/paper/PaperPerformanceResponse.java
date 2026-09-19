package com.shyblack.cryptosignals.dto.paper;

import java.math.BigDecimal;

public record PaperPerformanceResponse(
		int totalTrades,
		int winningTrades,
		int losingTrades,
		BigDecimal winRatePct,
		BigDecimal totalNetPnl,
		BigDecimal averageWin,
		BigDecimal averageLoss,
		BigDecimal profitFactor,
		BigDecimal bestTradePnl,
		BigDecimal worstTradePnl,
		BigDecimal totalFees,
		BigDecimal returnPct
) {}
