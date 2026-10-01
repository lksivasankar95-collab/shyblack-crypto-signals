package com.shyblack.cryptosignals.signal.nfm;

import com.shyblack.cryptosignals.entity.enums.NewsEventCategory;
import com.shyblack.cryptosignals.entity.enums.NewsEventStage;
import com.shyblack.cryptosignals.entity.enums.NewsEventType;
import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import com.shyblack.cryptosignals.entity.enums.NewsSourceTier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable, engine-facing view of an NFM event. Keeps the analyzer pure and
 * unit-testable without touching JPA.
 */
public record NfmEventView(
		UUID eventId,
		NewsEventType eventType,
		NewsEventCategory eventCategory,
		NewsEventStage eventStage,
		NewsSourceTier sourceTier,
		NewsImpact assetRelevance,
		Integer confidence,
		BigDecimal expectedValue,
		BigDecimal actualValue,
		BigDecimal surpriseValue,
		String surpriseDirection,
		Instant eventTime
) {
}
