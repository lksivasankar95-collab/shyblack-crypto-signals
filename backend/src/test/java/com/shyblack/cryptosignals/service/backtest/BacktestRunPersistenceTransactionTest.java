package com.shyblack.cryptosignals.service.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.BacktestRun;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.entity.enums.BacktestStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.repository.BacktestRunRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestConfig;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestEngine;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategy;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Proves the two operational fixes that unit tests with mocks cannot see:
 *
 * <ol>
 *   <li>{@link BacktestRunPersistence} runs inside a real transaction. When
 *       these methods were {@code @Transactional} on {@code BacktestJobRunner}
 *       they were only ever self-invoked from its private {@code run()}, so
 *       Spring's proxy never engaged: no transaction wrapped the read-modify-
 *       write, the PESSIMISTIC_WRITE lock was taken and dropped inside Spring
 *       Data's own per-call transaction, and persistResult could half-apply.</li>
 *   <li>A job enqueued from inside {@code startBacktest}'s transaction only
 *       starts after that transaction commits, so the worker cannot look for a
 *       run row that is not visible yet and silently abandon it in QUEUED.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class BacktestRunPersistenceTransactionTest {

	@Autowired
	private BacktestRunPersistence persistence;

	@Autowired
	private BacktestRunRepository runRepo;

	@Autowired
	private com.shyblack.cryptosignals.repository.BacktestSignalRepository signalRepo;

	@Autowired
	private com.shyblack.cryptosignals.repository.BacktestTradeRepository tradeRepo;

	@Autowired
	private UserRepository userRepo;

	private static BacktestConfig config() {
		Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
		return new BacktestConfig("ema-rsi", "BTCUSDT", "1h", TradingMode.SPOT,
				t0, t0.plusSeconds(86400),
				new BigDecimal("10000"), new BigDecimal("1"),
				new BigDecimal("0.1"), new BigDecimal("0.05"), 1,
				com.shyblack.cryptosignals.entity.enums.BacktestExecutionModel.NEXT_CANDLE_OPEN,
				com.shyblack.cryptosignals.entity.enums.BacktestSameCandlePolicy.SL_FIRST);
	}

	private static BacktestConfig frictionlessConfig() {
		Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
		return new BacktestConfig("ema-rsi", "BTCUSDT", "1h", TradingMode.SPOT,
				t0, t0.plusSeconds(86400),
				new BigDecimal("10000"), new BigDecimal("1"),
				BigDecimal.ZERO, BigDecimal.ZERO, 1,
				com.shyblack.cryptosignals.entity.enums.BacktestExecutionModel.NEXT_CANDLE_OPEN,
				com.shyblack.cryptosignals.entity.enums.BacktestSameCandlePolicy.SL_FIRST);
	}

	private BacktestRun queuedRun() {
		User user = new User();
		user.setEmail("bt-tx-" + System.nanoTime() + "@test.local");
		user.setFullName("Backtest Harness");
		user.setPasswordHash("x");
		user.setEnabled(true);
		user = userRepo.saveAndFlush(user);

		BacktestRun run = new BacktestRun();
		run.setUser(user);
		run.setStatus(BacktestStatus.QUEUED);
		run.setStrategyId("ema-rsi");
		run.setStrategyVersion("v1");
		run.setEngineVersion("backtest-engine/v1");
		run.setTradingMode(TradingMode.SPOT);
		run.setSymbol("BTCUSDT");
		run.setTimeframe("1h");
		run.setStartDate(Instant.parse("2024-01-01T00:00:00Z"));
		run.setEndDate(Instant.parse("2024-01-02T00:00:00Z"));
		run.setInitialCapital(new BigDecimal("10000"));
		run.setRiskPerTradePct(new BigDecimal("1"));
		run.setFeePct(new BigDecimal("0.1"));
		run.setSlippagePct(new BigDecimal("0.05"));
		run.setLeverage(1);
		run.setConfigurationHash("deadbeef");
		run.setConfigurationJson("{}");
		return runRepo.saveAndFlush(run);
	}

	@Test
	void persistResult_commitsEveryChildCollectionTogetherWithTheMetrics() {
		BacktestRun run = queuedRun();

		// Signals / trades / equity all reference the run; a non-atomic writer
		// could leave them behind after a failure or, worse, save them under a
		// version the run row never reached.
		var signal = new com.shyblack.cryptosignals.entity.BacktestSignal();
		UUID signalRef = UUID.randomUUID();
		signal.setSignalRef(signalRef);
		signal.setRun(run);
		signal.setSymbol("BTCUSDT");
		signal.setSide(com.shyblack.cryptosignals.entity.enums.PositionSide.LONG);
		signal.setCandleTime(Instant.parse("2024-01-01T01:00:00Z"));
		signal.setReferencePrice(new BigDecimal("100"));
		signal.setEntryPrice(new BigDecimal("100"));
		signal.setStopLoss(new BigDecimal("95"));
		signal.setTakeProfit(new BigDecimal("110"));
		signal.setStrategyId("ema-rsi");
		signal.setStrategyVersion("v1");

		var trade = new com.shyblack.cryptosignals.entity.BacktestTrade();
		trade.setRun(run);
		trade.setSignalId(signalRef);
		trade.setSymbol("BTCUSDT");
		trade.setSide(com.shyblack.cryptosignals.entity.enums.PositionSide.LONG);
		trade.setQuantity(new BigDecimal("2"));
		trade.setEntryPrice(new BigDecimal("100"));
		trade.setExitPrice(new BigDecimal("110"));
		trade.setNotional(new BigDecimal("200"));
		trade.setStopLoss(new BigDecimal("95"));
		trade.setTakeProfit(new BigDecimal("110"));
		trade.setEntryFee(new BigDecimal("0.2"));
		trade.setExitFee(new BigDecimal("0.22"));
		trade.setGrossPnl(new BigDecimal("20"));
		trade.setNetPnl(new BigDecimal("19.58"));
		trade.setLeverage(1);
		trade.setExitReason(com.shyblack.cryptosignals.entity.enums.BacktestExitReason.TAKE_PROFIT);
		trade.setEntryTime(Instant.parse("2024-01-01T00:00:00Z"));
		trade.setExitTime(Instant.parse("2024-01-01T01:00:00Z"));

		var point = new com.shyblack.cryptosignals.entity.BacktestEquityPoint();
		point.setRun(run);
		point.setTime(Instant.parse("2024-01-01T02:00:00Z"));
		point.setEquity(new BigDecimal("10019.58"));
		point.setAvailableBalance(new BigDecimal("10019.58"));
		point.setUnrealizedPnl(BigDecimal.ZERO);
		point.setRealizedPnl(new BigDecimal("19.58"));
		point.setPeakEquity(new BigDecimal("10019.58"));
		point.setDrawdown(BigDecimal.ZERO);
		point.setDrawdownPct(BigDecimal.ZERO);

		BacktestEngine.Result result = new BacktestEngine.Result(
				List.of(signal), List.of(trade), List.of(point), 3, null);

		Optional<BacktestRun> saved = persistence.persistResult(run.getId(), config(), result, false);
		assertThat(saved).isPresent();

		BacktestRun reloaded = runRepo.findById(run.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(BacktestStatus.COMPLETED);
		assertThat(reloaded.getTotalTrades()).isEqualTo(1);
		assertThat(reloaded.getTotalNetPnl()).isEqualByComparingTo("19.58");
		assertThat(reloaded.getTotalFees()).isEqualByComparingTo("0.42");
		assertThat(reloaded.getFinalEquity()).isEqualByComparingTo("10019.58");
		// finalEquity and totalReturnPct must describe the same curve.
		assertThat(reloaded.getTotalReturnPct())
				.isEqualByComparingTo("0.1958");
		assertThat(reloaded.getProcessedCandles()).isEqualTo(3);

		// The trade must point at the signal's real primary key, not the
		// engine's correlation ref.
		var persistedSignal = signalRepo.findByRunOrderByCandleTimeAsc(reloaded);
		assertThat(persistedSignal).hasSize(1);
		var realId = persistedSignal.get(0).getId();
		assertThat(realId).isNotNull();
		assertThat(persistedSignal.get(0).getSignalRef()).isEqualTo(signalRef);
		assertThat(tradeRepo.findByRunOrderByEntryTimeAsc(reloaded))
				.singleElement()
				.satisfies(t -> assertThat(t.getSignalId())
						.as("trade.signalId must resolve to the generated signal key")
						.isEqualTo(realId));
	}

	@Test
	void markStarted_isTransactionalAndCommitsTheStatusChange() {
		BacktestRun run = queuedRun();

		persistence.markStarted(run.getId());

		assertThat(runRepo.findById(run.getId()).orElseThrow().getStatus())
				.isEqualTo(BacktestStatus.RUNNING);
	}

	@Test
	void markStarted_doesNotReviveACancelledRun() {
		BacktestRun run = queuedRun();
		run.setStatus(BacktestStatus.CANCELLED);
		runRepo.saveAndFlush(run);

		assertThat(persistence.markStarted(run.getId())).isEmpty();

		assertThat(runRepo.findById(run.getId()).orElseThrow().getStatus())
				.isEqualTo(BacktestStatus.CANCELLED);
	}

	@Test
	void markFailed_ignoresAnAlreadyCompletedRun() {
		BacktestRun run = queuedRun();
		persistence.markStarted(run.getId());
		persistence.persistResult(run.getId(), config(),
				new BacktestEngine.Result(List.of(), List.of(), List.of(), 2, null), false);

		persistence.markFailed(run.getId(), "late boom");

		BacktestRun reloaded = runRepo.findById(run.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(BacktestStatus.COMPLETED);
		assertThat(reloaded.getFailureReason()).isNull();
	}

	@Test
	void aCallerTransactionIsVisibleSoNestingIsHonoured() {
		// Sanity anchor for the proxy: these methods must NOT open their own
		// transaction when one is already active (default REQUIRED).
		BacktestRun run = queuedRun();
		assertThat(TransactionSynchronizationManager.isActualTransactionActive())
				.as("the test itself runs outside a transaction")
				.isFalse();
		persistence.markStarted(run.getId());
		assertThat(TransactionSynchronizationManager.isActualTransactionActive())
				.as("the transaction must be closed again on return")
				.isFalse();
	}

	@Test
	void theEngineAndPortfolioStayConsistentForASingleEntryAndExit() {
		// End-to-end numeric check through the real engine: entry at the next
		// candle's open, exit at the final close, curve ending flat. Fees and
		// slippage are zeroed so the arithmetic is exact.
		Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
		List<HistoricalCandle> candles = List.of(
				candle(t0, "100", "101", "99.5", "100"),
				candle(t0.plusSeconds(3600), "100", "106", "99.5", "105"));

		BacktestStrategy once = new BacktestStrategy() {
			@Override public String id() { return "once"; }
			@Override public String version() { return "v1"; }
			@Override public int warmup() { return 1; }
			@Override
			public java.util.Optional<BacktestStrategy.Signal> evaluate(
					List<HistoricalCandle> history, int i) {
				if (i != 0) return java.util.Optional.empty();
				return java.util.Optional.of(new BacktestStrategy.Signal(
						com.shyblack.cryptosignals.entity.enums.PositionSide.LONG,
						new BigDecimal("100"), new BigDecimal("95"), new BigDecimal("500"),
						"once"));
			}
		};

		BacktestConfig cfg = frictionlessConfig();
		BacktestEngine.Result result = BacktestEngine.run(
				new BacktestRun(), cfg, once, candles, new AtomicBoolean(false));

		assertThat(result.trades()).singleElement();
		var t = result.trades().get(0);
		// 1% risk of 10000 = 100 risked, stop 5 away => 20 units.
		assertThat(t.getQuantity()).isEqualByComparingTo("20");
		assertThat(t.getEntryPrice()).isEqualByComparingTo("100");
		// TP 500 is unreachable, so the position is force-closed at candle 1.
		assertThat(t.getExitPrice()).isEqualByComparingTo("105");
		assertThat(t.getExitReason())
				.isEqualTo(com.shyblack.cryptosignals.entity.enums.BacktestExitReason.END_OF_TEST);
		assertThat(t.getGrossPnl()).isEqualByComparingTo("100"); // (105-100) * 20
		assertThat(t.getNetPnl()).isEqualByComparingTo("100");

		// Curve: 2 candles + 1 terminal point, ending flat at realized equity.
		assertThat(result.equity()).hasSize(3);
		BigDecimal last = result.equity().get(2).getEquity();
		assertThat(last).isEqualByComparingTo(cfg.initialCapital().add(t.getNetPnl()));
		assertThat(result.equity().get(2).getUnrealizedPnl()).isEqualByComparingTo("0");
	}

	@Test
	void feesAndSlippageMoveTheFillNotTheSizingBudget() {
		// 0.05% adverse slippage on a LONG entry lifts the fill to 100.05, so
		// the stop distance becomes 5.05 and the risk-based size shrinks to
		// 100 / 5.05. The budget is fixed in currency terms, not in units.
		Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
		List<HistoricalCandle> candles = List.of(
				candle(t0, "100", "101", "99.5", "100"),
				candle(t0.plusSeconds(3600), "100", "106", "99.5", "105"));

		BacktestStrategy once = new BacktestStrategy() {
			@Override public String id() { return "once"; }
			@Override public String version() { return "v1"; }
			@Override public int warmup() { return 1; }
			@Override
			public java.util.Optional<BacktestStrategy.Signal> evaluate(
					List<HistoricalCandle> history, int i) {
				if (i != 0) return java.util.Optional.empty();
				return java.util.Optional.of(new BacktestStrategy.Signal(
						com.shyblack.cryptosignals.entity.enums.PositionSide.LONG,
						new BigDecimal("100"), new BigDecimal("95"), new BigDecimal("500"),
						"once"));
			}
		};

		BacktestEngine.Result result = BacktestEngine.run(
				new BacktestRun(), config(), once, candles, new AtomicBoolean(false));

		var t = result.trades().get(0);
		assertThat(t.getEntryPrice()).isEqualByComparingTo("100.05");
		assertThat(t.getQuantity())
				.as("100 risked over a 5.05 stop distance")
				.isEqualByComparingTo(new BigDecimal("100")
						.divide(new BigDecimal("5.05"), 8, java.math.RoundingMode.DOWN));
	}

	private static HistoricalCandle candle(Instant open, String o, String h, String l, String c) {
		return new HistoricalCandle(open,
				new BigDecimal(o), new BigDecimal(h), new BigDecimal(l), new BigDecimal(c),
				new BigDecimal("1000"), open.plusSeconds(3600));
	}
}