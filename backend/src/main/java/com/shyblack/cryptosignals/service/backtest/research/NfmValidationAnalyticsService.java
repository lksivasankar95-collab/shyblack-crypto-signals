package com.shyblack.cryptosignals.service.backtest.research;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only run-scoped analytics. Descriptive only — no ranking, no winner.
 */
@Service
@RequiredArgsConstructor
public class NfmValidationAnalyticsService {

	private final NfmAttributionPersistenceService attributionService;

	@Transactional(readOnly = true)
	public NfmValidationAnalytics.Report report(UUID runId) {
		return NfmValidationAnalytics.analyze(attributionService.attributions(runId));
	}
}
