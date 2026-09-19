package com.shyblack.cryptosignals.dto.paper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaperAccountResponse(
		UUID id,
		String quoteCurrency,
		BigDecimal initialBalance,
		BigDecimal availableBalance,
		BigDecimal invested,
		BigDecimal totalBalance,
		BigDecimal equity,
		BigDecimal realizedPnl,
		BigDecimal unrealizedPnl,
		BigDecimal totalFees,
		int totalTrades,
		int winningTrades,
		int losingTrades,
		BigDecimal winRatePct,
		Instant createdAt
) {}
