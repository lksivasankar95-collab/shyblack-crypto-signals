package com.shyblack.cryptosignals.service.backtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.entity.BacktestEquityPoint;
import com.shyblack.cryptosignals.entity.BacktestRun;
import com.shyblack.cryptosignals.entity.enums.BacktestStatus;
import com.shyblack.cryptosignals.repository.BacktestEquityPointRepository;
import com.shyblack.cryptosignals.repository.BacktestRunRepository;
import com.shyblack.cryptosignals.repository.BacktestSignalRepository;
import com.shyblack.cryptosignals.repository.BacktestTradeRepository;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestConfig;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestEngine;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Regression tests for run-state bookkeeping. These are pure Mockito unit
 * tests — the transaction boundaries themselves are covered by
 * {@link BacktestRunPersistenceTransactionTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BacktestRunPersistenceTest {

	@Mock private BacktestRunRepository runRepo;
	@Mock private BacktestTradeRepository tradeRepo;
	@Mock private BacktestSignalRepository signalRepo;
	@Mock private BacktestEquityPointRepository equityRepo;
	@InjectMocks private BacktestRunPersistence persistence;

	private static BacktestConfig config() {
		java.time.Instant t0 = java.time.Instant.parse("2024-01-01T00:00:00Z");
		return new BacktestConfig("ema-rsi", "BTCUSDT", "1h",
				com.shyblack.cryptosignals.entity.enums.TradingMode.SPOT,
				t0, t0.plusSeconds(86400 * 30),
				new BigDecimal("10000"), new BigDecimal("1"),
				BigDecimal.ZERO, BigDecimal.ZERO, 1,
				com.shyblack.cryptosignals.entity.enums.BacktestExecutionModel.NEXT_CANDLE_OPEN,
				com.shyblack.cryptosignals.entity.enums.BacktestSameCandlePolicy.SL_FIRST);
	}

	private static BacktestEngine.Result result(java.util.List<BacktestEquityPoint> equity) {
		return new BacktestEngine.Result(java.util.List.of(), java.util.List.of(), equity, 7, null);
	}

	private static BacktestRun run(BacktestStatus status) {
		BacktestRun run = new BacktestRun();
		run.setId(UUID.randomUUID());
		run.setStatus(status);
		run.setStrategyId("ema-rsi");
		run.setSymbol("BTCUSDT");
		run.setTimeframe("1h");
		run.setInitialCapital(new BigDecimal("10000"));
		return run;
	}

	/**
	 * Regression: cancelling a QUEUED run set it to CANCELLED, but the already
	 * submitted job still ran markStarted and unconditionally wrote RUNNING, so
	 * a cancelled run flickered back to "running" in the UI.
	 */
	@Test
	void markStarted_doesNotReviveACancelledRun() {
		BacktestRun cancelled = run(BacktestStatus.CANCELLED);
		when(runRepo.findByIdForUpdate(any())).thenReturn(Optional.of(cancelled));

		assertThat(persistence.markStarted(cancelled.getId())).isEmpty();
		assertThat(cancelled.getStatus()).isEqualTo(BacktestStatus.CANCELLED);
		verify(runRepo, never()).save(any());
	}

	@Test
	void markStarted_doesNotReviveAFailedRun() {
		BacktestRun failed = run(BacktestStatus.FAILED);
		when(runRepo.findByIdForUpdate(any())).thenReturn(Optional.of(failed));

		assertThat(persistence.markStarted(failed.getId())).isEmpty();
		verify(runRepo, never()).save(any());
	}

	@Test
	void markStarted_promotesAQueuedRun() {
		BacktestRun queued = run(BacktestStatus.QUEUED);
		when(runRepo.findByIdForUpdate(any())).thenReturn(Optional.of(queued));
		when(runRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		assertThat(persistence.markStarted(queued.getId())).isPresent();
		assertThat(queued.getStatus()).isEqualTo(BacktestStatus.RUNNING);
		assertThat(queued.getStartedAt()).isNotNull();
	}

	@Test
	void markStarted_reportsAMissingRunSoTheRunnerCanLogIt() {
		when(runRepo.findByIdForUpdate(any())).thenReturn(Optional.empty());

		assertThat(persistence.markStarted(UUID.randomUUID()))
				.as("an empty result is how the runner knows not to stay silent")
				.isEmpty();
	}

	@Test
	void persistResult_reportsARunDeletedMidFlight() {
		when(runRepo.findByIdForUpdate(any())).thenReturn(Optional.empty());

		assertThat(persistence.persistResult(
				UUID.randomUUID(), config(), result(java.util.List.of()), false))
				.isEmpty();
	}

	@Test
	void markFailed_truncatesOverlongReasons() {
		BacktestRun queued = run(BacktestStatus.QUEUED);
		when(runRepo.findByIdForUpdate(any())).thenReturn(Optional.of(queued));

		persistence.markFailed(queued.getId(), "x".repeat(2_000));

		assertThat(queued.getFailureReason()).hasSizeLessThanOrEqualTo(480);
		assertThat(queued.getStatus()).isEqualTo(BacktestStatus.FAILED);
	}

	@Test
	void markFailed_handlesNullReason() {
		BacktestRun queued = run(BacktestStatus.QUEUED);
		when(runRepo.findByIdForUpdate(any())).thenReturn(Optional.of(queued));

		persistence.markFailed(queued.getId(), null);

		assertThat(queued.getFailureReason()).isEqualTo("unknown error");
	}

	@Test
	void markFailed_doesNotResurrectACompletedRun() {
		BacktestRun done = run(BacktestStatus.COMPLETED);
		when(runRepo.findByIdForUpdate(any())).thenReturn(Optional.of(done));

		persistence.markFailed(done.getId(), "late error");

		assertThat(done.getStatus())
				.as("a crash in the finally block must not overwrite a finished run")
				.isEqualTo(BacktestStatus.COMPLETED);
	}

	@Test
	void updateTotals_isANoOpForAMissingRun() {
		when(runRepo.findByIdForUpdate(any())).thenReturn(Optional.empty());

		persistence.updateTotals(UUID.randomUUID(), 42);

		verify(runRepo, never()).save(any());
	}

	@Test
	void cancelledRun_recordsTheCancelReason() {
		BacktestRun running = run(BacktestStatus.RUNNING);
		when(runRepo.findByIdForUpdate(any())).thenReturn(Optional.of(running));
		when(runRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		BacktestEngine.Result cancelled = new BacktestEngine.Result(
				java.util.List.of(), java.util.List.of(), java.util.List.of(), 7, "CANCELLED_RUN");
		persistence.persistResult(running.getId(), config(), cancelled, true);

		assertThat(running.getStatus()).isEqualTo(BacktestStatus.CANCELLED);
		assertThat(running.getFailureReason()).isEqualTo("CANCELLED_RUN");
		assertThat(running.getProcessedCandles()).isEqualTo(7);
		assertThat(running.getCompletedAt()).isNotNull();
	}

	@Test
	void persistResult_derivesFinalEquityFromTheCurveNotFromRecomputedPnl() {
		BacktestRun running = run(BacktestStatus.RUNNING);
		when(runRepo.findByIdForUpdate(any())).thenReturn(Optional.of(running));
		when(runRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		BacktestEquityPoint point = new BacktestEquityPoint();
		point.setEquity(new BigDecimal("10123.45678901234"));
		persistence.persistResult(running.getId(), config(), result(java.util.List.of(point)), false);

		assertThat(running.getFinalEquity()).isEqualByComparingTo("10123.45678901");
		assertThat(running.getFinalEquity().scale()).isEqualTo(8);
		assertThat(running.getStatus()).isEqualTo(BacktestStatus.COMPLETED);
	}

	@Test
	void persistResult_leavesFinalEquityNullForAnEmptyCurve() {
		BacktestRun running = run(BacktestStatus.RUNNING);
		when(runRepo.findByIdForUpdate(any())).thenReturn(Optional.of(running));
		when(runRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		persistence.persistResult(running.getId(), config(), result(java.util.List.of()), false);

		assertThat(running.getFinalEquity()).isNull();
		assertThat(running.getStatus()).isEqualTo(BacktestStatus.COMPLETED);
	}

	@Test
	void aFreshRun_startsQueuedWithNoTerminalTimestamps() {
		BacktestRun fresh = new BacktestRun();
		assertThat(fresh.getStatus()).isEqualTo(BacktestStatus.QUEUED);
		assertThat(fresh.getStartedAt()).isNull();
		assertThat(fresh.getCompletedAt()).isNull();
		assertThat(fresh.getProcessedCandles()).isZero();
	}
}