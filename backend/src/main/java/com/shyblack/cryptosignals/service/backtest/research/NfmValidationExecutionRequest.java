package com.shyblack.cryptosignals.service.backtest.research;

import com.shyblack.cryptosignals.entity.enums.BacktestExecutionModel;
import com.shyblack.cryptosignals.entity.enums.BacktestSameCandlePolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Request to execute one NFM historical validation run over one or more symbols.
 * {@code runType} is a string so an invalid value is rejected explicitly rather
 * than silently coerced. Null optional fields fall back to frozen defaults in
 * {@link NfmValidationExecutionService}. No field can inject events/derivatives.
 */
public record NfmValidationExecutionRequest(
		String runType,
		List<String> symbols,
		Instant start,
		Instant end,
		String timeframe,
		BigDecimal initialCapital,
		BigDecimal riskPerTradePct,
		BigDecimal feePct,
		BigDecimal slippagePct,
		Integer leverage,
		BacktestExecutionModel executionModel,
		BacktestSameCandlePolicy sameCandlePolicy,
		String marketDatasetVersion,
		String eventDatasetVersion,
		String derivativesDatasetVersion,
		Integer walkForwardWindowDays,
		Integer walkForwardStepDays,
		List<SensitivityVariantRequest> variants) {

	public record SensitivityVariantRequest(String label, String paramsJson) {
	}
}
