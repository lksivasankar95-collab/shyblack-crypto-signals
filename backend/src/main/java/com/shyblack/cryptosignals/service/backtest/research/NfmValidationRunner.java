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
 * <p>Persistence is deliberately NOT done here; see
 * {@code NfmValidationResultPersistenceService}. Runtime execution (PA runtime)
 * is out of scope and remains RUNTIME_BLOCKED when the externally-managed
 * backend is unavailable.</p>
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
		ValidationDataQualityGate.Result gate =
				ValidationDataQualityGate.checkAll(candles, events, baseConfig.startDate(), baseConfig.endDate());
		if (!gate.passed()) {
			return new NfmValidationResult(runId, baseConfig.strategyId(), strategyVersion(baseConfig), runType,
					List.of(baseConfig.symbol()), baseConfig.timeframe(), baseConfig.startDate(),
					baseConfig.endDate(), baseConfig.hash(), datasetVersion, eventDatasetVersion,
					derivativesDatasetVersion, "BLOCKED", NfmValidationStatus.DATA_QUALITY_BLOCKED,
					0, 0, 0, null, null, null, null, null, null, null, null, null, null,
					"DATA_QUALITY_BLOCKED: " + String.join("; ", gate.failures()), List.of());
		}

		boolean partialCoverage = events == null || events.isEmpty();

		int trades = 0, wins = 0, losses = 0;
		BigDecimal netPnl = null, grossProfit = null, grossLoss = null, fees = null;
		BigDecimal winRate = null, expectancy = null, profitFactor = null, maxDd = null, returnPct = null;
		List<ResearchWindowResult> windows = List.of();
		NfmValidationStatus status = NfmValidationStatus.COMPLETED;

		switch (runType) {
			case BASELINE -> {
				PartialExitBacktestEngine.Result r =
						PartialExitBacktestEngine.run(baseConfig, strategyFactory.get(), candles, events);
				trades = r.trades();
				wins = r.wins();
				losses = r.losses();
				netPnl = r.netPnl();
				fees = r.totalFees();
				winRate = r.winRatePct();
				BigDecimal gp = BigDecimal.ZERO, gl = BigDecimal.ZERO;
				for (PartialExitSimulator.Lifecycle lc : r.lifecycles()) {
					if (lc.netPnl().signum() > 0) gp = gp.add(lc.netPnl());
					else if (lc.netPnl().signum() < 0) gl = gl.add(lc.netPnl().abs());
				}
				grossProfit = gp;
				grossLoss = gl;
				expectancy = ratio(netPnl, trades);
				profitFactor = gl.signum() == 0 ? null : gp.divide(gl, 4, RoundingMode.HALF_UP);
				returnPct = pctOf(netPnl, baseConfig.initialCapital());
			}
			case WALK_FORWARD -> {
				windows = WalkForwardEngine.run(baseConfig, strategyFactory, candles, events, windowDays, stepDays);
				if (windows.isEmpty()) {
					status = NfmValidationStatus.NOT_EXECUTED;
				} else {
					WindowMetricsAggregator.Aggregate a = WindowMetricsAggregator.aggregate(windows);
					trades = a.trades();
					wins = a.wins();
					losses = a.losses();
					netPnl = a.netPnl();
					grossProfit = a.grossProfit();
					grossLoss = a.grossLoss();
					fees = a.fees();
					winRate = a.winRatePct();
					expectancy = a.expectancy();
					profitFactor = a.profitFactor();
					maxDd = a.worstDrawdownPct();
					returnPct = a.avgReturnPct();
				}
			}
			case OOS -> {
				windows = List.of(OutOfSampleRunner.run(baseConfig, strategyFactory.get(), candles, events));
				WindowMetricsAggregator.Aggregate a = WindowMetricsAggregator.aggregate(windows);
				trades = a.trades();
				wins = a.wins();
				losses = a.losses();
				netPnl = a.netPnl();
				grossProfit = a.grossProfit();
				grossLoss = a.grossLoss();
				fees = a.fees();
				winRate = a.winRatePct();
				expectancy = a.expectancy();
				profitFactor = a.profitFactor();
				maxDd = a.worstDrawdownPct();
				returnPct = a.avgReturnPct();
			}
			case SENSITIVITY -> {
				windows = SensitivityRunner.run(baseConfig, params -> strategyFactory.get(), candles, events,
						variants);
				WindowMetricsAggregator.Aggregate a = WindowMetricsAggregator.aggregate(windows);
				trades = a.trades();
				wins = a.wins();
				losses = a.losses();
				netPnl = a.netPnl();
				grossProfit = a.grossProfit();
				grossLoss = a.grossLoss();
				fees = a.fees();
				winRate = a.winRatePct();
				expectancy = a.expectancy();
				profitFactor = a.profitFactor();
				maxDd = a.worstDrawdownPct();
				returnPct = a.avgReturnPct();
			}
		}

		if (status == NfmValidationStatus.COMPLETED && partialCoverage) {
			status = NfmValidationStatus.DATA_COVERAGE_PARTIAL;
		}
		String notes = partialCoverage
				? "event coverage PARTIAL (no exact-time events in range); no fabrication"
				: "ok";
		return new NfmValidationResult(runId, baseConfig.strategyId(), strategyVersion(baseConfig), runType,
				List.of(baseConfig.symbol()), baseConfig.timeframe(), baseConfig.startDate(),
				baseConfig.endDate(), baseConfig.hash(), datasetVersion, eventDatasetVersion,
				derivativesDatasetVersion, "PASS", status, trades, wins, losses, netPnl, grossProfit,
				grossLoss, fees, null, winRate, expectancy, profitFactor, maxDd, returnPct, notes, windows);
	}

	private static String strategyVersion(BacktestConfig config) {
		return config.strategyParams() == null ? "NFM_FUTURES_V1" : config.strategyParams();
	}

	private static BigDecimal ratio(BigDecimal value, int count) {
		return value == null ? null
				: (count == 0 ? null : value.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP));
	}

	private static BigDecimal pctOf(BigDecimal value, BigDecimal base) {
		return value == null || base == null || base.signum() == 0 ? null
				: value.multiply(BigDecimal.valueOf(100)).divide(base, 4, RoundingMode.HALF_UP);
	}
}
