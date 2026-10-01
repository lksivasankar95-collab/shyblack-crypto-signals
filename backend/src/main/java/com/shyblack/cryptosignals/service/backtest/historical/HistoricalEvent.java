package com.shyblack.cryptosignals.service.backtest.historical;

import com.shyblack.cryptosignals.entity.enums.NewsEventCategory;
import com.shyblack.cryptosignals.entity.enums.NewsEventStage;
import com.shyblack.cryptosignals.entity.enums.NewsEventType;
import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import com.shyblack.cryptosignals.entity.enums.NewsSourceTier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable historical NFM event supplied to an event-aware backtest strategy.
 * The {@code time} is authoritative and is the only temporal gate — the engine
 * must never hand a strategy an event dated after the candle being evaluated.
 */
public record HistoricalEvent(
		UUID id,
		Instant time,
		String symbol,
		NewsEventType eventType,
		NewsEventCategory eventCategory,
		NewsEventStage eventStage,
		NewsSourceTier sourceTier,
		NewsImpact relevance,
		BigDecimal expectedValue,
		BigDecimal actualValue,
		BigDecimal surpriseValue,
		String source,
		String headline
) {
}
