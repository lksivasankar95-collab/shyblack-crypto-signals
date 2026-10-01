package com.shyblack.cryptosignals.signal.nfm;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Observational snapshot of the values the NFM engine ALREADY computed for a
 * decision. It is populated from the same {@link NfmAssessment} / event / regime
 * used to make the trade decision — nothing is recalculated and nothing here can
 * influence the decision. Every unavailable value is null (UNKNOWN), never zero.
 */
public record NfmDecisionContext(
		List<UUID> eventIds,
		Integer score,
		String grade,
		String eventType,
		String eventStage,
		String sourceTier,
		BigDecimal priceReaction,
		BigDecimal volumeRatio,
		BigDecimal oiChange,
		BigDecimal funding,
		BigDecimal liquidation,
		String marketRegime,
		String tradeabilityState,
		String rejectReason,
		Long eventAgeSeconds,
		BigDecimal expected,
		BigDecimal actual,
		BigDecimal surprise
) {
}
