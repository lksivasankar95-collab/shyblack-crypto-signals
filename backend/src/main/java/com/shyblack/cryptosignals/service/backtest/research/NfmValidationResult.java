package com.shyblack.cryptosignals.service.backtest.research;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Auditable result of one NFM historical validation run. Metrics that cannot be
 * legitimately computed are null (UNKNOWN), never zero. No winner/ranking field
 * exists anywhere in this model.
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
		int wins,
		int losses,
		BigDecimal netPnl,
		BigDecimal grossProfit,
		BigDecimal grossLoss,
		BigDecimal fees,
		BigDecimal slippage,
		BigDecimal winRatePct,
		BigDecimal expectancy,
		BigDecimal profitFactor,
		BigDecimal maxDrawdownPct,
		BigDecimal returnPct,
		String notes,
		List<ResearchWindowResult> windows
) {
}
