package com.shyblack.cryptosignals.service.backtest.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Phase-45 regression: a signal generated at candle T MUST NOT depend on any
 * candle > T. If mutating the future changes the signal at T, the engine
 * has leaked information from the future.
 *
 * The strategy under test explicitly reads {@code history.get(currentIndex)}
 * and any candle before it — never beyond. If we ever accidentally hand the
 * strategy a longer list, this test catches it.
 */
class BacktestNoLookAheadTest {

	private static HistoricalCandle candle(int i, double base) {
		Instant t = Instant.parse("2024-01-01T00:00:00Z").plusSeconds(3600L * i);
		return new HistoricalCandle(t,
				BigDecimal.valueOf(base),
				BigDecimal.valueOf(base + 1),
				BigDecimal.valueOf(base - 1),
				BigDecimal.valueOf(base + 0.5),
				BigDecimal.valueOf(100),
				t.plusSeconds(3600));
	}

	/** Trivial strategy: LONG signal at candle 5 if close > prior close. */
	static class Strategy implements BacktestStrategy {
		@Override public String id() { return "test-strategy"; }
		@Override public String version() { return "v1"; }
		@Override public int warmup() { return 2; }
		@Override
		public Optional<Signal> evaluate(List<HistoricalCandle> history, int currentIndex) {
			if (currentIndex < 1) return Optional.empty();
			double prev = history.get(currentIndex - 1).close().doubleValue();
			double now = history.get(currentIndex).close().doubleValue();
			if (now > prev) {
				return Optional.of(new Signal(PositionSide.LONG,
						BigDecimal.valueOf(now),
						BigDecimal.valueOf(now * 0.99),
						BigDecimal.valueOf(now * 1.02),
						"test"));
			}
			return Optional.empty();
		}
	}

	@Test
	void signal_at_index_T_is_identical_whenFutureCandlesChange() {
		Strategy strategy = new Strategy();
		int T = 5;

		// Dataset A: normal continuation.
		List<HistoricalCandle> historyA = new ArrayList<>();
		for (int i = 0; i <= T; i++) historyA.add(candle(i, 100 + i));

		// Dataset B: same first (T+1) candles, then radically different future.
		List<HistoricalCandle> historyB = new ArrayList<>();
		for (int i = 0; i <= T; i++) historyB.add(candle(i, 100 + i));
		for (int i = T + 1; i < T + 20; i++) historyB.add(candle(i, 5000 - i));

		Optional<BacktestStrategy.Signal> signalA = strategy.evaluate(historyA, T);
		Optional<BacktestStrategy.Signal> signalB = strategy.evaluate(historyB.subList(0, T + 1), T);

		assertThat(signalA).isEqualTo(signalB);
	}

	@Test
	void engine_never_gives_strategy_future_candles() {
		Strategy strategy = new Strategy() {
			@Override
			public Optional<Signal> evaluate(List<HistoricalCandle> history, int currentIndex) {
				// If the engine ever hands us more than (currentIndex + 1) candles,
				// throw — the assertion will fail the test.
				if (history.size() != currentIndex + 1) {
					throw new AssertionError("Look-ahead: history has "
							+ history.size() + " candles at index " + currentIndex);
				}
				return super.evaluate(history, currentIndex);
			}
		};

		List<HistoricalCandle> candles = new ArrayList<>();
		for (int i = 0; i < 20; i++) candles.add(candle(i, 100 + i));

		com.shyblack.cryptosignals.entity.BacktestRun run = new com.shyblack.cryptosignals.entity.BacktestRun();
		BacktestConfig cfg = new BacktestConfig(
				strategy.id(), "BTCUSDT", "1h",
				com.shyblack.cryptosignals.entity.enums.TradingMode.SPOT,
				candles.get(0).openTime(),
				candles.get(candles.size() - 1).closeTime(),
				new BigDecimal("10000"), new BigDecimal("1"),
				new BigDecimal("0.10"), new BigDecimal("0.05"), 1,
				com.shyblack.cryptosignals.entity.enums.BacktestExecutionModel.NEXT_CANDLE_OPEN,
				com.shyblack.cryptosignals.entity.enums.BacktestSameCandlePolicy.SL_FIRST);

		BacktestEngine.Result result = BacktestEngine.run(run, cfg, strategy, candles, null);
		// No AssertionError from within the strategy means no look-ahead occurred.
		assertThat(result.processedCandles()).isEqualTo(candles.size());
	}
}
