package com.shyblack.cryptosignals.service.backtest;

import com.shyblack.cryptosignals.config.BacktestingProperties;
import com.shyblack.cryptosignals.entity.BacktestEquityPoint;
import com.shyblack.cryptosignals.entity.BacktestRun;
import com.shyblack.cryptosignals.entity.BacktestSignal;
import com.shyblack.cryptosignals.entity.BacktestTrade;
import com.shyblack.cryptosignals.entity.enums.BacktestStatus;
import com.shyblack.cryptosignals.repository.BacktestEquityPointRepository;
import com.shyblack.cryptosignals.repository.BacktestRunRepository;
import com.shyblack.cryptosignals.repository.BacktestSignalRepository;
import com.shyblack.cryptosignals.repository.BacktestTradeRepository;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestConfig;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestEngine;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestMetricsCalculator;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalMarketDataProvider;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategy;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategyRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes backtest runs on a bounded thread pool. A single instance
 * tracks in-flight jobs by run id so callers can request cancellation.
 */
@Service
@RequiredArgsConstructor
public class BacktestJobRunner {

	private static final Logger log = LoggerFactory.getLogger(BacktestJobRunner.class);

	private final BacktestingProperties props;
	private final BacktestRunRepository runRepo;
	private final BacktestTradeRepository tradeRepo;
	private final BacktestSignalRepository signalRepo;
	private final BacktestEquityPointRepository equityRepo;
	private final HistoricalMarketDataProvider historicalDataProvider;
	private final BacktestStrategyRegistry strategyRegistry;

	private ExecutorService executor;
	private final ConcurrentHashMap<UUID, AtomicBoolean> cancellation = new ConcurrentHashMap<>();

	@PostConstruct
	void init() {
		this.executor = Executors.newFixedThreadPool(props.maxConcurrentRuns(),
				r -> {
					Thread t = new Thread(r, "backtest-worker");
					t.setDaemon(true);
					return t;
				});
	}

	@PreDestroy
	void shutdown() {
		if (executor == null) return;
		executor.shutdown();
		try {
			if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow();
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	public void enqueue(UUID runId, BacktestConfig config) {
		AtomicBoolean cancel = new AtomicBoolean(false);
		cancellation.put(runId, cancel);
		executor.submit(() -> {
			try {
				run(runId, config, cancel);
			} catch (Throwable t) {
				log.error("[Backtest] run={} crashed", runId, t);
				markFailed(runId, t.getMessage());
			} finally {
				cancellation.remove(runId);
			}
		});
	}

	public boolean requestCancel(UUID runId) {
		AtomicBoolean flag = cancellation.get(runId);
		if (flag == null) return false;
		flag.set(true);
		return true;
	}

	// ------------------------------------------------------------------

	private void run(UUID runId, BacktestConfig config, AtomicBoolean cancel) {
		BacktestRun run = markStarted(runId);
		if (run == null) return;

		BacktestStrategy strategy = strategyRegistry.require(config.strategyId());
		List<HistoricalCandle> candles = historicalDataProvider.load(
				config.symbol(), config.timeframe(), config.startDate(), config.endDate());
		if (candles.isEmpty()) {
			markFailed(runId, "No historical candles for the requested range");
			return;
		}
		if (candles.size() > props.maxCandles()) {
			markFailed(runId, "Requested range exceeds " + props.maxCandles() + " candles");
			return;
		}
		updateTotals(runId, candles.size());

		BacktestEngine.Result result = BacktestEngine.run(run, config, strategy, candles, cancel);
		persistResult(runId, config, candles.get(candles.size() - 1).closeTime(), result, cancel.get());
	}

	@Transactional
	protected BacktestRun markStarted(UUID runId) {
		return runRepo.findByIdForUpdate(runId).map(run -> {
			run.setStatus(BacktestStatus.RUNNING);
			run.setStartedAt(Instant.now());
			return runRepo.save(run);
		}).orElse(null);
	}

	@Transactional
	protected void updateTotals(UUID runId, int total) {
		runRepo.findByIdForUpdate(runId).ifPresent(r -> {
			r.setTotalCandles(total);
			runRepo.save(r);
		});
	}

	@Transactional
	protected void markFailed(UUID runId, String reason) {
		runRepo.findByIdForUpdate(runId).ifPresent(r -> {
			r.setStatus(BacktestStatus.FAILED);
			r.setFailureReason(reason == null ? "unknown error"
					: reason.length() > 480 ? reason.substring(0, 480) : reason);
			r.setCompletedAt(Instant.now());
			runRepo.save(r);
		});
	}

	@Transactional
	protected void persistResult(UUID runId, BacktestConfig config,
			Instant lastCandleTime, BacktestEngine.Result result, boolean cancelled) {
		BacktestRun run = runRepo.findByIdForUpdate(runId).orElseThrow();
		if (!result.signals().isEmpty()) signalRepo.saveAll(result.signals());
		if (!result.trades().isEmpty()) tradeRepo.saveAll(result.trades());
		if (!result.equity().isEmpty()) equityRepo.saveAll(result.equity());

		BacktestMetricsCalculator.Metrics m = BacktestMetricsCalculator.compute(
				result.trades(), result.equity(), config.initialCapital());
		run.setProcessedCandles(result.processedCandles());
		if (cancelled) {
			run.setStatus(BacktestStatus.CANCELLED);
			run.setFailureReason(result.cancelReason());
		} else {
			run.setStatus(BacktestStatus.COMPLETED);
		}
		run.setCompletedAt(Instant.now());
		run.setFinalEquity(m.totalNetPnl() == null ? null
				: config.initialCapital().add(m.totalNetPnl()).setScale(8, RoundingMode.HALF_UP));
		run.setTotalNetPnl(m.totalNetPnl());
		run.setTotalReturnPct(m.returnPct());
		run.setMaxDrawdown(m.maxDrawdown());
		run.setMaxDrawdownPct(m.maxDrawdownPct());
		run.setWinRatePct(m.winRatePct());
		run.setProfitFactor(m.profitFactor());
		run.setSharpeRatio(m.sharpeRatio());
		run.setSortinoRatio(m.sortinoRatio());
		run.setTotalFees(m.totalFees());
		run.setGrossProfit(m.grossProfit());
		run.setGrossLoss(m.grossLoss());
		run.setTotalTrades(m.totalTrades());
		run.setWinningTrades(m.winningTrades());
		run.setLosingTrades(m.losingTrades());
		run.setLiquidations(m.liquidations());
		run.setAverageWin(m.averageWin());
		run.setAverageLoss(m.averageLoss());
		run.setLargestWin(m.largestWin());
		run.setLargestLoss(m.largestLoss());
		run.setExpectancy(m.expectancy());
		runRepo.save(run);
		log.info("[Backtest] run={} status={} trades={} candles={} pnl={}",
				runId, run.getStatus(), m.totalTrades(),
				result.processedCandles(), m.totalNetPnl());
	}

	/** Test seam so pure unit tests can exercise the engine synchronously. */
	public static BacktestEngine.Result runSync(BacktestRun run, BacktestConfig config,
			BacktestStrategy strategy, List<HistoricalCandle> candles) {
		return BacktestEngine.run(run, config, strategy, candles, null);
	}

	// Suppress: fields retained for future symbolic use.
	@SuppressWarnings("unused")
	private BacktestTrade reservedTrade() { return null; }
	@SuppressWarnings("unused")
	private BacktestSignal reservedSignal() { return null; }
	@SuppressWarnings("unused")
	private BacktestEquityPoint reservedEquity() { return null; }
}
