package com.shyblack.cryptosignals.dto.live;

import com.shyblack.cryptosignals.entity.enums.ExchangeConnectionStatus;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LiveAccountResponse(
		UUID id,
		ExchangeName exchange,
		ExchangeConnectionStatus connectionStatus,
		boolean enabled,
		boolean killSwitchActive,
		String quoteCurrency,
		BigDecimal cachedAvailableBalance,
		BigDecimal cachedTotalBalance,
		BigDecimal maxNotionalPerTrade,
		int maxActivePositions,
		BigDecimal dailyLossLimitPct,
		Instant lastValidatedAt,
		String lastValidationMessage
) {}
