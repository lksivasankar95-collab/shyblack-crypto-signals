package com.shyblack.cryptosignals.service.backtest.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.BacktestRun;
import com.shyblack.cryptosignals.entity.enums.BacktestExitReason;
import com.shyblack.cryptosignals.entity.enums.BacktestExecutionModel;
import com.shyblack.cryptosignals.entity.enums.BacktestSameCandlePolicy;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for defects that made a backtest report numbers which did
 * not describe the trades it actually made.
 */
class BacktestEngineCorrectnessTest {

	private static final Instant T0 = Instant.parse("2024-01-01T00:00:00Z");

	private static HistoricalCandle candle(int i, String o, String h, String l, String c) {
		Instant open = T0.plusSeconds(i * 3600L);
		return new HistoricalCandle(open,
				new BigDecimal(o), new BigDecimal(h), new BigDecimal(l), new BigDecimal(c),
				new BigDecimal("1000"), open.plusSeconds(3600));
	}

	private static BacktestConfig config(String capital, String riskPct,
			TradingMode mode, int leverage) {
		return new BacktestConfig("test", "BTCUSDT", "1h", mode,
				T0, T0.plusSeconds(86400 * 30),
				new BigDecimal(capital), new BigDecimal(riskPct),
				BigDecimal.ZERO, BigDecimal.ZERO, leverage,
				BacktestExecutionModel.NEXT_CANDLE_OPEN, BacktestSameCandlePolicy.SL_FIRST);
	}

	/** Emits a LONG on every candle where it is asked, stop 1% below a fixed entry. */
	private static BacktestStrategy longEveryCandle(String entryPrice, String stopPct) {
		return new BacktestStrategy() {
			@Override public String id() { return "long-every"; }
			@Override public String version() { return "v1"; }
			@Override public int warmup() { return 1; }
			@Override
			public Optional<Signal> evaluate(List<HistoricalCandle> history, int currentIndex) {
				BigDecimal p = new BigDecimal(entryPrice);
				BigDecimal stop = p.multiply(BigDecimal.ONE.subtract(new BigDecimal(stopPct)));
				return Optional.of(new Signal(PositionSide.LONG, p, stop,
						p.multiply(new BigDecimal("1.5")), "always"));
			}
		};
	}

	/**
	 * Flat candles punctuated by a dip that reaches the 1% stop, on a strict
	 * 3-candle period. Only one position can be open at a time, so a run needs
	 * the stop to actually fire between signals for more than one trade to
	 * happen at all: signal → fill → stop out → signal → fill …
	 *
	 * @param n total candles
	 */
	private static List<HistoricalCandle> oscillatingCandles(int n) {
		List<HistoricalCandle> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			out.add(i % 3 == 2
					? candle(i, "100", "101", "98", "100")   // reaches the 99 stop
					: candle(i, "100", "101", "99.5", "100"));
		}
		return out;
	}

	// ── Defect: risk budget exceeded available balance ──────────

	/**
	 * Regression: risk-based sizing is a risk budget, not an affordability
	 * check. With capital 1000, risk 2% and a 1% stop it budgeted a 2000
	 * notional — twice the balance. The portfolio debited the full notional
	 * (SPOT), drove availableBalance to -1000, and every later sizing call
	 * short-circuited on a non-positive balance, so the run silently stopped
	 * trading even though its stop-outs kept freeing cash again.
	 */
	@Test
	void spot_riskBudgetLargerThanBalance_doesNotStrandTheRun() {
		List<HistoricalCandle> candles = oscillatingCandles(40);
		BacktestConfig cfg = config("1000", "2", TradingMode.SPOT, 1);

		BacktestEngine.Result result = BacktestEngine.run(
				new BacktestRun(), cfg, longEveryCandle("100", "0.01"), candles, null, null);

		// 40 candles on a 3-candle cycle => 13 stop-outs, and the position still
		// open on the final candle is force-closed, so 14 rows in total.
		assertThat(result.trades()).hasSize(14);
		assertThat(result.trades())
				.filteredOn(t -> t.getExitReason() == BacktestExitReason.STOP_LOSS)
				.as("every stop-out must be followed by another fillable signal")
				.hasSize(13);
	}

	@Test
	void spot_availableBalance_neverGoesNegative() {
		BacktestConfig cfg = config("1000", "2", TradingMode.SPOT, 1);
		BacktestEngine.Result result = BacktestEngine.run(
				new BacktestRun(), cfg, longEveryCandle("100", "0.01"),
				oscillatingCandles(40), null, null);

		assertThat(result.equity()).isNotEmpty();
		assertThat(result.equity())
				.allSatisfy(p -> assertThat(p.getAvailableBalance())
						.as("available balance must never be over-committed")
						.isGreaterThanOrEqualTo(BigDecimal.ZERO));
		assertThat(result.equity())
				.allSatisfy(p -> assertThat(p.getEquity())
						.as("equity must stay non-negative")
						.isGreaterThanOrEqualTo(BigDecimal.ZERO));
	}

	@Test
	void spot_position_isClampedToTheAffordableNotional() {
		// 2% risk with a 1% stop on a 1000 balance budgets 20 units = 2000
		// notional; only 1000 is fundable, so the fill must be clamped to 10.
		BacktestConfig cfg = config("1000", "2", TradingMode.SPOT, 1);
		BacktestEngine.Result result = BacktestEngine.run(
				new BacktestRun(), cfg, longEveryCandle("100", "0.01"),
				oscillatingCandles(7), null, null);

		assertThat(result.trades()).isNotEmpty();
		assertThat(result.trades().get(0).getQuantity())
				.isEqualByComparingTo("10");
		assertThat(result.trades().get(0).getQuantity().multiply(new BigDecimal("100")))
				.as("notional must fit the balance")
				.isLessThanOrEqualTo(new BigDecimal("1000"));
	}

	@Test
	void futures_leverageStillAllowsTheFullRiskBudget() {
		// 10x leverage makes the 2000 notional affordable on a 1000 balance,
		// so the clamp must NOT shrink the position below the risk budget.
		BacktestConfig cfg = config("1000", "2", TradingMode.FUTURES, 10);
		BacktestEngine.Result result = BacktestEngine.run(
				new BacktestRun(), cfg, longEveryCandle("100", "0.01"),
				oscillatingCandles(40), null, null);

		// Risk budget: 1000 × 2% = 20 risked over a 1.00 stop distance => 20 units.
		// Clamped affordable at 10x: 1000 × 10 / 100 = 100 — so the budget wins.
		assertThat(result.trades()).isNotEmpty();
		assertThat(result.trades().get(0).getQuantity())
				.as("leverage must not shrink a budget the margin can cover")
				.isEqualByComparingTo("20");
	}

	// ── Defect: equity curve ended on a mark-to-market value ────

	/**
	 * Regression: when a position was still open on the final candle the curve
	 * stopped at the mark-to-market snapshot taken BEFORE the end-of-test
	 * close. returnPct (derived from the last equity point) therefore disagreed
	 * with finalEquity (= initialCapital + realized net P&L) on the same run.
	 */
	@Test
	void equityCurve_endsFlat_soReturnPctAndFinalEquityAgree() {
		List<HistoricalCandle> candles = List.of(
				candle(0, "100", "101", "99", "100"),
				candle(1, "100", "105", "99", "104"));

		// Signal on candle 0 only, then flat: forces the end-of-test close.
		BacktestStrategy onceOnly = new BacktestStrategy() {
			@Override public String id() { return "once"; }
			@Override public String version() { return "v1"; }
			@Override public int warmup() { return 1; }
			@Override
			public Optional<Signal> evaluate(List<HistoricalCandle> history, int i) {
				if (i != 0) return Optional.empty();
				return Optional.of(new Signal(PositionSide.LONG, new BigDecimal("100"),
						new BigDecimal("95"), new BigDecimal("120"), "once"));
			}
		};

		BacktestConfig cfg = config("1000", "1", TradingMode.SPOT, 1);
		BacktestEngine.Result result = BacktestEngine.run(
				new BacktestRun(), cfg, onceOnly, candles, null, null);

		assertThat(result.trades())
				.singleElement()
				.satisfies(t -> assertThat(t.getExitReason()).isEqualTo(BacktestExitReason.END_OF_TEST));

		BacktestMetricsCalculator.Metrics m = BacktestMetricsCalculator.compute(
				result.trades(), result.equity(), cfg.initialCapital());
		BigDecimal finalEquity = result.equity().get(result.equity().size() - 1).getEquity();

		// The two headline numbers on a run card must reconcile.
		assertThat(finalEquity).isEqualByComparingTo(
				cfg.initialCapital().add(m.totalNetPnl()));
		assertThat(m.returnPct()).isEqualByComparingTo(
				finalEquity.subtract(cfg.initialCapital())
						.multiply(BigDecimal.valueOf(100))
						.divide(cfg.initialCapital(), 4, java.math.RoundingMode.HALF_UP));
	}

	@Test
	void equityCurve_unchangedWhenNoPositionIsOpenAtTheEnd() {
		// No signal at all -> the curve must stay exactly one point per candle.
		List<HistoricalCandle> candles = List.of(
				candle(0, "100", "101", "99", "100"),
				candle(1, "100", "101", "99", "100"));

		BacktestStrategy silent = new BacktestStrategy() {
			@Override public String id() { return "silent"; }
			@Override public String version() { return "v1"; }
			@Override public int warmup() { return 1; }
			@Override public Optional<Signal> evaluate(List<HistoricalCandle> h, int i) {
				return Optional.empty();
			}
		};

		BacktestEngine.Result result = BacktestEngine.run(
				new BacktestRun(), config("1000", "1", TradingMode.SPOT, 1),
				silent, candles, null, null);

		assertThat(result.trades()).isEmpty();
		assertThat(result.equity()).hasSize(candles.size());
	}

	// ── Defect: end-of-test close trusted a skipped candle ──────

	@Test
	void endOfTestClose_ignoresInvalidTrailingCandle() {
		// Candle 2 is structurally invalid (high below the open) so the validity
		// gate skips it. The force-close must use candle 1's close, not the
		// malformed trailing price of 999.
		HistoricalCandle first = candle(0, "100", "101", "99", "100");
		HistoricalCandle second = candle(1, "100", "101", "99", "100");
		HistoricalCandle invalid = new HistoricalCandle(
				T0.plusSeconds(2 * 3600),
				new BigDecimal("100"), new BigDecimal("1"), new BigDecimal("99"),
				new BigDecimal("999"), new BigDecimal("1000"), T0.plusSeconds(3 * 3600));
		List<HistoricalCandle> candles = List.of(first, second, invalid);

		BacktestStrategy onceOnly = new BacktestStrategy() {
			@Override public String id() { return "once"; }
			@Override public String version() { return "v1"; }
			@Override public int warmup() { return 1; }
			@Override
			public Optional<Signal> evaluate(List<HistoricalCandle> history, int i) {
				if (i != 0) return Optional.empty();
				return Optional.of(new Signal(PositionSide.LONG, new BigDecimal("100"),
						new BigDecimal("95"), new BigDecimal("120"), "once"));
			}
		};

		BacktestEngine.Result result = BacktestEngine.run(
				new BacktestRun(), config("1000", "1", TradingMode.SPOT, 1),
				onceOnly, candles, null, null);

		assertThat(result.processedCandles()).isEqualTo(2);
		assertThat(result.trades()).singleElement().satisfies(t -> {
			assertThat(t.getExitReason()).isEqualTo(BacktestExitReason.END_OF_TEST);
			assertThat(t.getExitPrice())
					.as("must close at the last VALID candle's close, not the malformed one")
					.isEqualByComparingTo("100");
		});
	}

	// ── Defect: warmup() was declared but never enforced ───────

	@Test
	void strategy_isNotAskedForSignalsBeforeItsWarmup() {
		AtomicInteger asked = new AtomicInteger();
		BacktestStrategy counting = new BacktestStrategy() {
			@Override public String id() { return "counting"; }
			@Override public String version() { return "v1"; }
			@Override public int warmup() { return 5; }
			@Override
			public Optional<Signal> evaluate(List<HistoricalCandle> history, int i) {
				asked.incrementAndGet();
				return Optional.empty();
			}
		};

		List<HistoricalCandle> candles = new ArrayList<>();
		for (int i = 0; i < 12; i++) candles.add(candle(i, "100", "101", "99", "100"));

		BacktestEngine.Result result = BacktestEngine.run(
				new BacktestRun(), config("1000", "1", TradingMode.SPOT, 1),
				counting, candles, null, null);

		assertThat(asked.get())
				.as("12 candles minus the 4 suppressed warmup candles")
				.isEqualTo(8);
		assertThat(result.processedCandles())
				.as("warmup suppresses signals only — candles are still processed")
				.isEqualTo(candles.size());
		assertThat(result.equity()).hasSize(candles.size());
	}

	@Test
	void warmup_exceedingSeriesLength_suppressesEverySignal() {
		AtomicInteger asked = new AtomicInteger();
		BacktestStrategy cold = new BacktestStrategy() {
			@Override public String id() { return "cold"; }
			@Override public String version() { return "v1"; }
			@Override public int warmup() { return 1_000; }
			@Override
			public Optional<Signal> evaluate(List<HistoricalCandle> history, int i) {
				asked.incrementAndGet();
				return Optional.of(new Signal(PositionSide.LONG, new BigDecimal("100"),
						new BigDecimal("95"), new BigDecimal("120"), "x"));
			}
		};

		List<HistoricalCandle> candles = List.of(
				candle(0, "100", "101", "99", "100"),
				candle(1, "100", "101", "99", "100"));

		BacktestEngine.Result result = BacktestEngine.run(
				new BacktestRun(), config("1000", "1", TradingMode.SPOT, 1),
				cold, candles, null, null);

		assertThat(asked.get()).isZero();
		assertThat(result.signals()).isEmpty();
		assertThat(result.trades()).isEmpty();
	}
}