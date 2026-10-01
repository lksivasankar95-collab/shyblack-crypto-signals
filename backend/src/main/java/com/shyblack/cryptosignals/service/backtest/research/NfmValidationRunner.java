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
import java.util.function.Function;
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

	/** Convenience overload for callers with no per-params strategy factory. */
	public static NfmValidationResult run(NfmValidationRunType runType, BacktestConfig baseConfig,
			Supplier<BacktestStrategy> strategyFactory, List<HistoricalCandle> candles,
			List<HistoricalEvent> events, String datasetVersion, String eventDatasetVersion,
			String derivativesDatasetVersion, int windowDays, int stepDays,
			List<SensitivityRunner.Variant> variants) {
		return run(runType, baseConfig, (String paramsJson) -> strategyFactory.get(), candles, events,
				datasetVersion, eventDatasetVersion, derivativesDatasetVersion, windowDays, stepDays, variants);
	}

	/**
	 * @param strategyFactory paramsJson -&gt; a FRESH strategy instance. The params
	 *                        JSON is the frozen {@code strategyParams} for
	 *                        BASELINE/WALK_FORWARD/OOS, and the explicit variant
	 *                        JSON for SENSITIVITY.
	 */
	public static NfmValidationResult run(NfmValidationRunType runType, BacktestConfig baseConfig,
			Function<String, BacktestStrategy> strategyFactory, List<HistoricalCandle> candles,
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
		String baseParams = baseConfig.strategyParams();

		switch (runType) {
			case BASELINE -> {
				PartialExitBacktestEngine.Result r = PartialExitBacktestEngine.run(baseConfig,
						strategyFactory.apply(baseParams), candles, events);
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
				windows = WalkForwardEngine.run(baseConfig,
						() -> strategyFactory.apply(baseParams), candles, events, windowDays, stepDays);
				if (windows.isEmpty()) {
					status = NfmValidationStatus.NOT_EXECUTED;
				} else {
					Aggregate agg = aggregate(windows);
					trades = agg.trades; wins = agg.wins; losses = agg.losses; netPnl = agg.netPnl;
					grossProfit = agg.grossProfit; grossLoss = agg.grossLoss; fees = agg.fees;
					winRate = agg.winRate; expectancy = agg.expectancy; profitFactor = agg.profitFactor;
					maxDd = agg.maxDd; returnPct = agg.returnPct;
				}
			}
			case OOS -> {
				windows = List.of(OutOfSampleRunner.run(baseConfig, strategyFactory.apply(baseParams),
						candles, events));
				Aggregate agg = aggregate(windows);
				trades = agg.trades; wins = agg.wins; losses = agg.losses; netPnl = agg.netPnl;
				grossProfit = agg.grossProfit; grossLoss = agg.grossLoss; fees = agg.fees;
				winRate = agg.winRate; expectancy = agg.expectancy; profitFactor = agg.profitFactor;
				maxDd = agg.maxDd; returnPct = agg.returnPct;
			}
			case SENSITIVITY -> {
				windows = SensitivityRunner.run(baseConfig, strategyFactory, candles, events, variants);
				Aggregate agg = aggregate(windows);
				trades = agg.trades; wins = agg.wins; losses = agg.losses; netPnl = agg.netPnl;
				grossProfit = agg.grossProfit; grossLoss = agg.grossLoss; fees = agg.fees;
				winRate = agg.winRate; expectancy = agg.expectancy; profitFactor = agg.profitFactor;
				maxDd = agg.maxDd; returnPct = agg.returnPct;
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

	private record Aggregate(int trades, int wins, int losses, BigDecimal winRate, BigDecimal netPnl,
			BigDecimal grossProfit, BigDecimal grossLoss, BigDecimal fees, BigDecimal expectancy,
			BigDecimal profitFactor, BigDecimal maxDd, BigDecimal returnPct) {}

	private static Aggregate aggregate(List<ResearchWindowResult> windows) {
		WindowMetricsAggregator.Aggregate a = WindowMetricsAggregator.aggregate(windows);
		return new Aggregate(a.trades(), a.wins(), a.losses(), a.winRatePct(), a.netPnl(),
				a.grossProfit(), a.grossLoss(), a.fees(), a.expectancy(), a.profitFactor(),
				a.worstDrawdownPct(), a.avgReturnPct());
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
