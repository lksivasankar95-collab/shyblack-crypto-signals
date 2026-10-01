package com.shyblack.cryptosignals.dto.nfm;

import com.shyblack.cryptosignals.entity.enums.NewsEventStage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Admin payload to record a scheduled macro / central-bank release with an
 * expected vs actual pair so NFM can compute a surprise (spec §4, §8, §36).
 */
public record MacroEventRequest(
		String eventType,
		String source,
		Instant eventTime,
		BigDecimal expectedValue,
		BigDecimal actualValue,
		BigDecimal revisedValue,
		String headline,
		String summary,
		String marketInterpretation,
		NewsEventStage eventStage,
		List<String> assets
) {
}
