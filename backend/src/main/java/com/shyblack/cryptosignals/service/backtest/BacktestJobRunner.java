package com.shyblack.cryptosignals.service.backtest;

import com.shyblack.cryptosignals.config.BacktestingProperties;
import com.shyblack.cryptosignals.entity.BacktestRun;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestConfig;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestEngine;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalEvent;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalEventProvider;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalMarketDataProvider;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategy;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategyRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Executes backtest runs on a bounded thread pool. A single instance
 * tracks in-flight jobs by run id so callers can request cancellation.
 *
 * <p>All database writes are delegated to {@link BacktestRunPersistence} —
 * see that class for why they cannot live here.</p>
 */
@Service
@RequiredArgsConstructor
public class BacktestJobRunner {

	private static final Logger log = LoggerFactory.getLogger(BacktestJobRunner.class);

	private final BacktestingProperties props;
	private final BacktestLimits limits;
	private final BacktestRunPersistence persistence;
	private final HistoricalMarketDataProvider historicalDataProvider;
	private final HistoricalEventProvider historicalEventProvider;
	private final BacktestStrategyRegistry strategyRegistry;

	private ExecutorService executor;
	private final ConcurrentHashMap<UUID, AtomicBoolean> cancellation = new ConcurrentHashMap<>();

	@PostConstruct
	void init() {
		// Bounded pool AND bounded queue: a fixed pool alone caps concurrency but
		// leaves an unbounded LinkedBlockingQueue behind it, so a client looping
		// POST /api/v1/backtests would park work (and its row + cancel flag)
		// in memory indefinitely. Rejection is surfaced as an error instead.
		ThreadPoolExecutor pool = new ThreadPoolExecutor(
				limits.maxConcurrentRuns(), limits.maxConcurrentRuns(),
				0L, TimeUnit.MILLISECONDS,
				new ArrayBlockingQueue<>(limits.maxQueueDepth()),
				r -> {
					Thread t = new Thread(r, "backtest-worker");
					t.setDaemon(true);
					return t;
				},
				new ThreadPoolExecutor.AbortPolicy());
		this.executor = pool;
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

	/**
	 * Submits a run, deferring until the caller's transaction has committed.
	 *
	 * <p>{@code startBacktest} is transactional and saves the run row in it. If
	 * the worker started before that transaction committed, its first lookup
	 * would not see the row, mark it started would find nothing, and the run
	 * would sit in QUEUED forever with no result and no failure reason.
	 * Registering after-commit closes that window; when there is no transaction
	 * in progress we submit straight away.
	 */
	public void enqueue(UUID runId, BacktestConfig config) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override public void afterCommit() { submit(runId, config); }
			});
		} else {
			submit(runId, config);
		}
	}

	private void submit(UUID runId, BacktestConfig config) {
		AtomicBoolean cancel = new AtomicBoolean(false);
		cancellation.put(runId, cancel);
		try {
			executor.submit(() -> {
				try {
					run(runId, config, cancel);
				} catch (Throwable t) {
					log.error("[Backtest] run={} crashed", runId, t);
					persistence.markFailed(runId, t.getMessage());
				} finally {
					cancellation.remove(runId);
				}
			});
		} catch (RejectedExecutionException rejected) {
			cancellation.remove(runId);
			log.warn("[Backtest] run={} rejected: queue at capacity ({})",
					runId, limits.maxQueueDepth());
			persistence.markFailed(runId, "Backtest queue is full — try again shortly");
		}
	}

	public boolean requestCancel(UUID runId) {
		AtomicBoolean flag = cancellation.get(runId);
		if (flag == null) return false;
		flag.set(true);
		return true;
	}

	// ------------------------------------------------------------------

	private void run(UUID runId, BacktestConfig config, AtomicBoolean cancel) {
		// Empty here means the run was deleted, or already cancelled while
		// queued — both are terminal and must not be treated as work to do.
		var started = persistence.markStarted(runId);
		if (started.isEmpty()) {
			log.info("[Backtest] run={} not started (deleted, cancelled, or already terminal", runId);
			return;
		}
		BacktestRun run = started.get();

		BacktestStrategy strategy = strategyRegistry.create(config.strategyId(), config.strategyParams());
		List<HistoricalCandle> candles = historicalDataProvider.load(
				config.symbol(), config.timeframe(), config.startDate(), config.endDate());
		if (candles.isEmpty()) {
			persistence.markFailed(runId, "No historical candles for the requested range");
			return;
		}
		if (candles.size() > limits.maxCandles()) {
			persistence.markFailed(runId, "Requested range exceeds " + limits.maxCandles() + " candles");
			return;
		}
		persistence.updateTotals(runId, candles.size());

		List<HistoricalEvent> events = historicalEventProvider.load(
				config.symbol(), config.startDate(), config.endDate());
		BacktestEngine.Result result = BacktestEngine.run(run, config, strategy, candles, events, cancel);
		persistence.persistResult(runId, config, result, cancel.get())
				.ifPresentOrElse(
						persisted -> log.info("[Backtest] run={} status={} trades={} candles={} finalEquity={}",
								runId, persisted.getStatus(), persisted.getTotalTrades(),
								result.processedCandles(), persisted.getFinalEquity()),
						() -> log.warn("[Backtest] run={} vanished before results could be persisted", runId));
	}

	/**
	 * Test seam so pure unit tests can exercise the engine synchronously.
	 * Events are forwarded: dropping them here would make an event-aware
	 * strategy silently see an empty stream under test while seeing real events
	 * in production.
	 */
	public static BacktestEngine.Result runSync(BacktestRun run, BacktestConfig config,
			BacktestStrategy strategy, List<HistoricalCandle> candles) {
		return runSync(run, config, strategy, candles, List.of());
	}

	/** Test seam variant that forwards the historical event stream. */
	public static BacktestEngine.Result runSync(BacktestRun run, BacktestConfig config,
			BacktestStrategy strategy, List<HistoricalCandle> candles,
			List<HistoricalEvent> events) {
		return BacktestEngine.run(run, config, strategy, candles, events, null);
	}
}