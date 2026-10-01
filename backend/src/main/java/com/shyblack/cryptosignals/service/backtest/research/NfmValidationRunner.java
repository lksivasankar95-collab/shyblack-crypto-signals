package com.shyblack.cryptosignals.service.backtest.research;

import com.shyblack.cryptosignals.service.backtest.engine.BacktestConfig;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalEvent;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Single deterministic orchestration entry point for NFM historical validation.
 * Dispatches to the existing engines (no duplicates), runs the data-quality
 * gate first, and returns an auditable {@link NfmValidationResult} with explicit
 * status handling. It never fabricates data and never converts UNKNOWN to zero.
 *
 * <p>Runtime execution (PA runtime) is out of scope and remains RUNTIME_BLOCKED
 * when the externally-managed backend is unavailable.</p>
 */
public final class NfmValidationRunner {

	private NfmValidationRunner() {
	}

	public static NfmValidationResult run(NfmValidationRunType runType, BacktestConfig baseConfig,
			Supplier<BacktestStrategy> strategyFactory, List<HistoricalCandle> candles,
			List<HistoricalEvent> events, String datasetVersion, String eventDatasetVersion,
			String derivativesDatasetVersion, int windowDays, int stepDays,
			List<SensitivityRunner.Variant> variants) {

		UUID runId = UUID.nameUUIDFromBytes(
				(baseConfig.hash() + "|" + runType).getBytes(StandardCharsets.UTF_8));
		String dataQuality;
		ValidationDataQualityGate.Result gate =
				ValidationDataQualityGate.checkAll(candles, events, baseConfig.startDate(), baseConfig.endDate());
		if (!gate.passed()) {
			dataQuality = "BLOCKED";
			return result(runId, baseConfig, runType, dataQuality, NfmValidationStatus.DATA_QUALITY_BLOCKED,
					0, null, null, null, null, null, "DATA_QUALITY_BLOCKED: " + String.join("; ", gate.failures()),
					List.of(), eventDatasetVersion, derivativesDatasetVersion, datasetVersion);
		}
		dataQuality = "PASS";

		boolean emptyEvents = events == null || events.isEmpty();
		boolean partialCoverage = emptyEvents;

		List<ResearchWindowResult> windows = List.of();
		int trades = 0;
		BigDecimal netPnl = null;
		BigDecimal maxDd = null;
		BigDecimal winRate = null;
		BigDecimal expectancy = null;
		BigDecimal profitFactor = null;
		NfmValidationStatus status = NfmValidationStatus.COMPLETED;

		switch (runType) {
			case BASELINE -> {
				PartialExitBacktestEngine.Result r =
						PartialExitBacktestEngine.run(baseConfig, strategyFactory.get(), candles, events);
				trades = r.trades();
				netPnl = r.netPnl();
				winRate = r.winRatePct();
				expectancy = trades == 0 ? null
						: r.netPnl().divide(BigDecimal.valueOf(trades), 8, RoundingMode.HALF_UP);
				profitFactor = profitFactor(r);
			}
			case WALK_FORWARD -> {
				windows = WalkForwardEngine.run(baseConfig, strategyFactory, candles, events, windowDays, stepDays);
				if (windows.isEmpty()) {
					status = NfmValidationStatus.NOT_EXECUTED;
			} else {
					WindowMetricsAggregator.Aggregate a = WindowMetricsAggregator.aggregate(windows);
					trades = a.trades();
					netPnl = a.netPnl();
					maxDd = a.worstDrawdownPct();
					winRate = a.winRatePct();
					expectancy = a.expectancy();
					profitFactor = a.profitFactor();
				}
			}
			case OOS -> {
				windows = List.of(OutOfSampleRunner.run(baseConfig, strategyFactory.get(), candles, events));
				WindowMetricsAggregator.Aggregate a = WindowMetricsAggregator.aggregate(windows);
				trades = a.trades();
				netPnl = a.netPnl();
				maxDd = a.worstDrawdownPct();
				winRate = a.winRatePct();
				expectancy = a.expectancy();
				profitFactor = a.profitFactor();
			}
			case SENSITIVITY -> {
				windows = SensitivityRunner.run(baseConfig, params -> strategyFactory.get(), candles, events,
						variants);
				WindowMetricsAggregator.Aggregate a = WindowMetricsAggregator.aggregate(windows);
				trades = a.trades();
				netPnl = a.netPnl();
				winRate = a.winRatePct();
				profitFactor = a.profitFactor();
			}
		}

		if (status == NfmValidationStatus.COMPLETED && partialCoverage) {
			status = NfmValidationStatus.DATA_COVERAGE_PARTIAL;
		}
		String notes = partialCoverage
				? "event coverage PARTIAL (no exact-time events in range); no fabrication"
				: "ok";
		return result(runId, baseConfig, runType, dataQuality, status, trades, netPnl, maxDd, winRate,
				expectancy, profitFactor, notes, windows, eventDatasetVersion, derivativesDatasetVersion,
				datasetVersion);
	}

	private static BigDecimal profitFactor(PartialExitBacktestEngine.Result r) {
		BigDecimal grossWin = BigDecimal.ZERO, grossLoss = BigDecimal.ZERO;
		for (PartialExitSimulator.Lifecycle lc : r.lifecycles()) {
			BigDecimal n = lc.netPnl();
			if (n.signum() > 0) grossWin = grossWin.add(n);
			else if (n.signum() < 0) grossLoss = grossLoss.add(n.abs());
		}
		return grossLoss.signum() == 0 ? null : grossWin.divide(grossLoss, 4, RoundingMode.HALF_UP);
	}

	private static NfmValidationResult result(UUID runId, BacktestConfig baseConfig,
			NfmValidationRunType runType, String dataQuality, NfmValidationStatus status, int trades,
			BigDecimal netPnl, BigDecimal maxDd, BigDecimal winRate, BigDecimal expectancy,
			BigDecimal profitFactor, String notes, List<ResearchWindowResult> windows,
			String eventDatasetVersion, String derivativesDatasetVersion, String datasetVersion) {
		return new NfmValidationResult(runId, baseConfig.strategyId(), baseConfig.strategyParams() == null
				? "NFM_FUTURES_V1" : baseConfig.strategyParams(), runType, List.of(baseConfig.symbol()),
				baseConfig.timeframe(), baseConfig.startDate(), baseConfig.endDate(), baseConfig.hash(),
				datasetVersion, eventDatasetVersion, derivativesDatasetVersion, dataQuality, status, trades,
				netPnl, maxDd, winRate, expectancy, profitFactor, notes, windows);
	}
}
