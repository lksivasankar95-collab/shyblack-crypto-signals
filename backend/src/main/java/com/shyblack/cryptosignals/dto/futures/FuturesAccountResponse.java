package com.shyblack.cryptosignals.dto.futures;

import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import com.shyblack.cryptosignals.entity.enums.FuturesMarginMode;
import com.shyblack.cryptosignals.entity.enums.FuturesPositionMode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FuturesAccountResponse(
		UUID id,
		ExchangeName exchange,
		ExchangeConnectionStatus connectionStatus,
		boolean enabled,
		boolean killSwitchActive,
		boolean acknowledged,
		String marginAsset,
		FuturesMarginMode marginMode,
		FuturesPositionMode positionMode,
		int maxLeverage,
		BigDecimal maxNotionalPerTrade,
		int maxActivePositions,
		BigDecimal dailyLossLimitPct,
		BigDecimal walletBalance,
		BigDecimal availableBalance,
		BigDecimal marginBalance,
		BigDecimal usedMargin,
		BigDecimal maintenanceMargin,
		BigDecimal unrealizedPnl,
		BigDecimal realizedPnlToday,
		BigDecimal totalFundingPaid,
		Instant lastValidatedAt,
		String lastValidationMessage
) {}
