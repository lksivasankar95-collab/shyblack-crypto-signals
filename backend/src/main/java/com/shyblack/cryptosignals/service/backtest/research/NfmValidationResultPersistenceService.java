package com.shyblack.cryptosignals.service.backtest.research;

import com.shyblack.cryptosignals.entity.research.NfmValidationDetail;
import com.shyblack.cryptosignals.entity.research.NfmValidationRun;
import com.shyblack.cryptosignals.repository.research.NfmValidationDetailRepository;
import com.shyblack.cryptosignals.repository.research.NfmValidationRunRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists immutable NFM validation results and exposes descriptive, read-only
 * retrieval. This service never ranks, selects, or overwrites results, and
 * preserves NULL/UNKNOWN metrics exactly as produced by
 * {@link NfmValidationRunner}. It is the ONLY place validation results are
 * written; the runner stays persistence-free.
 */
@Service
@RequiredArgsConstructor
public class NfmValidationResultPersistenceService {

	public static final String KIND_WINDOW = "WINDOW";
	public static final String KIND_VARIANT = "VARIANT";

	private final NfmValidationRunRepository runRepository;
	private final NfmValidationDetailRepository detailRepository;

	/**
	 * Persist a completed, data-quality-blocked, or otherwise blocked result.
	 * Refuses to overwrite an existing run id (immutability).
	 */
	@Transactional
	public UUID persist(NfmValidationResult result, String configurationJson) {
		if (runRepository.existsByRunId(result.runId())) {
			throw new IllegalStateException(
					"Validation run already exists (immutable): " + result.runId());
		}
		NfmValidationRun run = new NfmValidationRun();
		run.setRunId(result.runId());
		run.setStrategyId(result.strategyId());
		run.setStrategyVersion(result.strategyVersion());
		run.setRunType(result.runType().name());
		run.setConfigurationHash(result.configurationHash());
		run.setConfigurationJson(configurationJson);
		run.setMarketDatasetVersion(result.datasetVersion());
		run.setEventDatasetVersion(result.eventDatasetVersion());
		run.setDerivativesDatasetVersion(result.derivativesDatasetVersion());
		run.setSymbols(result.symbols() == null ? null : String.join(",", result.symbols()));
		run.setTimeframe(result.timeframe());
		run.setStartTime(result.start());
		run.setEndTime(result.end());
		run.setDataQualityStatus(result.dataQuality());
		run.setExecutionStatus(result.executionStatus().name());
		run.setValidationStatus(overallStatus(result.executionStatus()));
		run.setEventCoverageStatus(result.executionStatus() == NfmValidationStatus.DATA_COVERAGE_PARTIAL
				? "EVENT_COVERAGE_PARTIAL" : "EVENT_COVERAGE_UNKNOWN");
		run.setTradeCount(result.tradeCount());
		run.setWins(result.wins());
		run.setLosses(result.losses());
		run.setNetPnl(result.netPnl());
		run.setGrossProfit(result.grossProfit());
		run.setGrossLoss(result.grossLoss());
		run.setFees(result.fees());
		run.setSlippage(result.slippage());
		run.setWinRate(result.winRatePct());
		run.setExpectancy(result.expectancy());
		run.setProfitFactor(result.profitFactor());
		run.setMaxDrawdownPct(result.maxDrawdownPct());
		run.setReturnPct(result.returnPct());
		run.setNotes(result.notes());
		runRepository.save(run);

		String kind = result.runType() == NfmValidationRunType.SENSITIVITY ? KIND_VARIANT : KIND_WINDOW;
		int ordinal = 0;
		for (ResearchWindowResult w : result.windows()) {
			detailRepository.save(toDetail(result.runId(), kind, ordinal++, w, result.configurationHash()));
		}
		return result.runId();
	}

	@Transactional(readOnly = true)
	public Optional<NfmValidationRun> findByRunId(UUID runId) {
		return runRepository.findByRunId(runId);
	}

	@Transactional(readOnly = true)
	public Optional<NfmValidationRun> findExisting(UUID runId) {
		return runRepository.findByRunId(runId);
	}

	@Transactional(readOnly = true)
	public List<NfmValidationRun> listRuns() {
		return runRepository.findAllByOrderByCreatedAtDesc();
	}

	@Transactional(readOnly = true)
	public List<ResearchWindowResult> windows(UUID runId) {
		return toWindows(detailRepository.findByRunIdAndKindOrderByOrdinalAsc(runId, KIND_WINDOW));
	}

	@Transactional(readOnly = true)
	public List<ResearchWindowResult> variants(UUID runId) {
		return toWindows(detailRepository.findByRunIdAndKindOrderByOrdinalAsc(runId, KIND_VARIANT));
	}

	private static String overallStatus(NfmValidationStatus status) {
		return switch (status) {
			case COMPLETED, DATA_COVERAGE_PARTIAL -> "COMPLETED";
			case DATA_QUALITY_BLOCKED, DATA_COVERAGE_BLOCKED, RUNTIME_BLOCKED -> "BLOCKED";
			case NOT_EXECUTED -> "NOT_VALIDATED";
		};
	}

	private static NfmValidationDetail toDetail(UUID runId, String kind, int ordinal,
			ResearchWindowResult w, String configurationHash) {
		NfmValidationDetail d = new NfmValidationDetail();
		d.setRunId(runId);
		d.setKind(kind);
		d.setOrdinal(ordinal);
		d.setLabel(w.label());
		d.setVariantId(kind.equals(KIND_VARIANT) ? w.label() : null);
		d.setParamsJson(w.paramsJson());
		d.setTestStart(w.start());
		d.setTestEnd(w.end());
		d.setConfigurationHash(configurationHash);
		d.setDataQuality(null);
		d.setStatus(null);
		d.setTrades(w.trades());
		d.setWins(w.wins());
		d.setLosses(w.losses());
		d.setNetPnl(w.netPnl());
		d.setGrossProfit(w.grossProfit());
		d.setGrossLoss(w.grossLoss());
		d.setFees(w.fees());
		d.setSlippage(null);
		d.setWinRate(w.winRatePct());
		d.setExpectancy(w.expectancy());
		d.setProfitFactor(w.profitFactor());
		d.setMaxDrawdownPct(w.maxDrawdownPct());
		d.setReturnPct(w.returnPct());
		return d;
	}

	private static List<ResearchWindowResult> toWindows(List<NfmValidationDetail> details) {
		return details.stream().map(d -> new ResearchWindowResult(
				d.getLabel(), d.getTestStart(), d.getTestEnd(), d.getParamsJson(),
				nz(d.getTrades()), nz(d.getWins()), nz(d.getLosses()),
				d.getWinRate(), d.getNetPnl(), d.getGrossProfit(), d.getGrossLoss(), d.getFees(),
				d.getExpectancy(), d.getProfitFactor(), d.getMaxDrawdownPct(), d.getReturnPct())).toList();
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}
}
