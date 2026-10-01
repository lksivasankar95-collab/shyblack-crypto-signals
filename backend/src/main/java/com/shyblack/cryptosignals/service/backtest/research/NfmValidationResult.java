package com.shyblack.cryptosignals.service.backtest.research;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Auditable result of one NFM historical validation run. Metrics that cannot be
 * legitimately computed are null (UNKNOWN), never zero.
 */
public record NfmValidationResult(
		UUID runId,
		String strategyId,
		String strategyVersion,
		NfmValidationRunType runType,
		List<String> symbols,
		String timeframe,
		Instant start,
		Instant end,
		String configurationHash,
		String datasetVersion,
		String eventDatasetVersion,
		String derivativesDatasetVersion,
		String dataQuality,
		NfmValidationStatus executionStatus,
		int tradeCount,
		BigDecimal netPnl,
		BigDecimal maxDrawdownPct,
		BigDecimal winRatePct,
		BigDecimal expectancy,
		BigDecimal profitFactor,
		String notes,
		List<ResearchWindowResult> windows
) {
}
