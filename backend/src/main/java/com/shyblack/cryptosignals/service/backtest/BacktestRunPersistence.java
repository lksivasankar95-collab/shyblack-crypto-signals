package com.shyblack.cryptosignals.service.backtest;

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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * All transactional writes for a backtest run.
 *
 * <p>These live in their own bean on purpose. {@code @Transactional} is
 * proxy-based, so a self-invocation inside {@link BacktestJobRunner} would
 * never open a transaction: every save would commit on its own, the
 * {@code PESSIMISTIC_WRITE} lock would be taken and released inside Spring
 * Data's per-call transaction and therefore protect nothing, and
 * {@link #persistResult} could leave a run with its signals and trades saved
 * but no matching metrics row if it failed midway.
 */
@Component
@RequiredArgsConstructor
public class BacktestRunPersistence {

	private static final int MAX_FAILURE_REASON = 480;

	private final BacktestRunRepository runRepo;
	private final BacktestTradeRepository tradeRepo;
	private final BacktestSignalRepository signalRepo;
	private final BacktestEquityPointRepository equityRepo;

	/**
	 * Moves a queued run to RUNNING, unless it already reached a terminal
	 * state. A run cancelled (or deleted) while still queued must stay that
	 * way — overwriting it with RUNNING makes the row flicker back to a running
	 * state after the user cancelled it.
	 *
	 * @return the run, or empty when it no longer exists or is already terminal.
	 */
	@Transactional
	public Optional<BacktestRun> markStarted(UUID runId) {
		return runRepo.findByIdForUpdate(runId)
				.filter(run -> run.getStatus() == BacktestStatus.QUEUED)
				.map(run -> {
					run.setStatus(BacktestStatus.RUNNING);
					run.setStartedAt(Instant.now());
					return runRepo.save(run);
				});
	}

	@Transactional
	public void updateTotals(UUID runId, int total) {
		runRepo.findByIdForUpdate(runId).ifPresent(r -> {
			r.setTotalCandles(total);
			runRepo.save(r);
		});
	}

	/**
	 * Marks a run FAILED, unless it has already reached a terminal state. The
	 * catch-all in {@code BacktestJobRunner} calls this from a finally block, so
	 * without the guard a late throw could overwrite a run that had in fact
	 * completed — replacing real results with a failure.
	 */
	@Transactional
	public void markFailed(UUID runId, String reason) {
		runRepo.findByIdForUpdate(runId)
				.filter(run -> !isTerminal(run.getStatus()))
				.ifPresent(r -> {
					r.setStatus(BacktestStatus.FAILED);
					r.setFailureReason(truncate(reason == null ? "unknown error" : reason));
					r.setCompletedAt(Instant.now());
					runRepo.save(r);
				});
	}

	private static boolean isTerminal(BacktestStatus status) {
		return status == BacktestStatus.COMPLETED
				|| status == BacktestStatus.FAILED
				|| status == BacktestStatus.CANCELLED;
	}

	/**
	 * Persists signals, trades, equity points and the run's final metrics as a
	 * single atomic unit. Returns empty when the run was deleted mid-flight.
	 */
	@Transactional
	public Optional<BacktestRun> persistResult(UUID runId, BacktestConfig config,
			BacktestEngine.Result result, boolean cancelled) {
		Optional<BacktestRun> found = runRepo.findByIdForUpdate(runId);
		if (found.isEmpty()) return Optional.empty();
		BacktestRun run = found.get();

		List<BacktestSignal> signals = result.signals();
		List<BacktestTrade> trades = result.trades();
		List<BacktestEquityPoint> equity = result.equity();
		// Signals first: their primary keys are database-generated, so the trades
		// can only be linked to them once the insert has happened.
		if (!signals.isEmpty()) signalRepo.saveAll(signals);
		linkTradesToSignals(signals, trades);
		if (!trades.isEmpty()) tradeRepo.saveAll(trades);
		if (!equity.isEmpty()) equityRepo.saveAll(equity);

		BacktestMetricsCalculator.Metrics m = BacktestMetricsCalculator.compute(
				trades, equity, config.initialCapital());

		run.setProcessedCandles(result.processedCandles());
		if (cancelled) {
			run.setStatus(BacktestStatus.CANCELLED);
			run.setFailureReason(truncate(result.cancelReason()));
		} else {
			run.setStatus(BacktestStatus.COMPLETED);
		}
		run.setCompletedAt(Instant.now());
		// Taken from the equity curve rather than recomputed as
		// initialCapital + netPnl, so finalEquity and returnPct can never
		// disagree on the same run.
		run.setFinalEquity(finalEquity(equity));
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
		return Optional.of(runRepo.save(run));
	}

	private static String truncate(String reason) {
		return reason.length() > MAX_FAILURE_REASON
				? reason.substring(0, MAX_FAILURE_REASON) : reason;
	}

	private static BigDecimal finalEquity(List<BacktestEquityPoint> equity) {
		if (equity.isEmpty() || equity.get(equity.size() - 1).getEquity() == null) return null;
		return equity.get(equity.size() - 1).getEquity().setScale(8, RoundingMode.HALF_UP);
	}

	/**
	 * Replaces each trade's correlation ref with the real primary key of the
	 * signal that opened it. A trade whose signal did not materialise keeps a
	 * null reference rather than a dangling one.
	 */
	private static void linkTradesToSignals(List<BacktestSignal> signals,
			List<BacktestTrade> trades) {
		Map<UUID, UUID> idByRef = new HashMap<>();
		for (BacktestSignal s : signals) {
			if (s.getSignalRef() != null && s.getId() != null) {
				idByRef.put(s.getSignalRef(), s.getId());
			}
		}
		for (BacktestTrade t : trades) {
			UUID ref = t.getSignalId();
			if (ref != null) t.setSignalId(idByRef.get(ref));
		}
	}
}