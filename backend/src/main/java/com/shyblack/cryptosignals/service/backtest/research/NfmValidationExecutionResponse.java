package com.shyblack.cryptosignals.service.backtest.research;

import java.util.List;
import java.util.UUID;

/**
 * Outcome of an execution request: one entry per symbol. {@code executionStatus}
 * is a precise {@link NfmValidationStatus} name; {@code duplicate} is true when
 * an identical deterministic run already existed and was NOT written again.
 */
public record NfmValidationExecutionResponse(String runType, List<SymbolOutcome> runs) {

	public record SymbolOutcome(
			String symbol,
			UUID runId,
			String executionStatus,
			String dataQuality,
			String configurationHash,
			int tradeCount,
			boolean duplicate) {
	}
}
