package com.shyblack.cryptosignals.service.backtest.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.enums.BacktestExecutionModel;
import com.shyblack.cryptosignals.entity.enums.BacktestSameCandlePolicy;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.service.backtest.engine.BacktestConfig;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.service.backtest.strategy.BacktestStrategy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ResearchRunnerTest {

	private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");

	/** Emits a LONG at index 2 with a tight TP; rising candles -> TP hit. */
	private static final class FixedStrategy implements BacktestStrategy {
		@Override public String id() { return "fixture"; }
		@Override public String version() { return "v1"; }
		@Override public int warmup() { return 0; }

		@Override
		public Optional<Signal> evaluate(List<HistoricalCandle> history, int currentIndex) {
			if (currentIndex != 2 || history.get(2) == null) {
				return Optional.empty();
			}
			double c = history.get(2).close().doubleValue();
			return Optional.of(new Signal(PositionSide.LONG,
					BigDecimal.valueOf(c).setScale(8, RoundingMode.HALF_UP),
					BigDecimal.valueOf(c * 0.995).setScale(8, RoundingMode.HALF_UP),
					BigDecimal.valueOf(c * 1.005).setScale(8, RoundingMode.HALF_UP), "fixture"));
		}
	}

	private static List<HistoricalCandle> candles(int count, long hoursPerCandle) {
		List<HistoricalCandle> list = new ArrayList<>();
		double price = 100.0;
		for (int i = 0; i < count; i++) {
			price *= 1.001; // +0.1% per candle -> 0.5% TP reached quickly
			Instant open = START.plus((long) i * hoursPerCandle, ChronoUnit.HOURS);
			BigDecimal p = BigDecimal.valueOf(price).setScale(8, RoundingMode.HALF_UP);
			list.add(new HistoricalCandle(open, p, p, p, p, BigDecimal.ONE,
					open.plus(hoursPerCandle, ChronoUnit.HOURS)));
		}
		return list;
	}

	private static BacktestConfig base(Instant end) {
		return new BacktestConfig("fixture", "BTCUSDT", "1h", TradingMode.FUTURES, START, end,
				new BigDecimal("1000"), new BigDecimal("2"), new BigDecimal("0.1"),
				new BigDecimal("0.05"), 1, BacktestExecutionModel.NEXT_CANDLE_OPEN,
				BacktestSameCandlePolicy.SL_FIRST, null);
	}

	@Test
	void walkForward_rollsChronologicalWindows_withoutLeakage() {
		List<HistoricalCandle> candles = candles(240, 1); // 10 days hourly
		Instant end = START.plus(10, ChronoUnit.DAYS);
		List<ResearchWindowResult> windows = WalkForwardEngine.run(
				base(end), FixedStrategy::new, candles, List.of(), 3, 3);

		assertThat(windows).isNotEmpty();
		// Windows must be strictly chronological and non-overlapping in start order.
		for (int i = 1; i < windows.size(); i++) {
			assertThat(windows.get(i).start()).isAfterOrEqualTo(windows.get(i - 1).start());
			assertThat(windows.get(i).start()).isAfterOrEqualTo(windows.get(i - 1).end());
		}
		assertThat(windows).allSatisfy(w -> assertThat(w.trades()).isGreaterThanOrEqualTo(1));
	}

	@Test
	void sensitivity_evaluatesEachVariantIndependently() {
		List<HistoricalCandle> candles = candles(240, 1);
		Instant end = START.plus(10, ChronoUnit.DAYS);
		List<ResearchWindowResult> results = SensitivityRunner.run(base(end),
				params -> new FixedStrategy(), candles, List.of(),
				List.of(new SensitivityRunner.Variant("A", "{\"minimumScore\":60}"),
						new SensitivityRunner.Variant("B", "{\"minimumScore\":70}")));

		assertThat(results).hasSize(2);
		assertThat(results).extracting(ResearchWindowResult::label).containsExactly("SENS_A", "SENS_B");
		assertThat(results).allSatisfy(r -> assertThat(r.trades()).isGreaterThanOrEqualTo(1));
	}

	@Test
	void outOfSample_runsSingleHeldOutWindowWithFrozenConfig() {
		List<HistoricalCandle> candles = candles(240, 1);
		Instant end = START.plus(10, ChronoUnit.DAYS);
		ResearchWindowResult oos = OutOfSampleRunner.run(base(end), new FixedStrategy(), candles, List.of());
		assertThat(oos.label()).isEqualTo("OOS");
		assertThat(oos.trades()).isGreaterThanOrEqualTo(1);
		assertThat(oos.start()).isEqualTo(START);
	}

	@Test
	void emptyCandles_producesEmptyResult_noException() {
		Instant end = START.plus(3, ChronoUnit.DAYS);
		assertThat(WalkForwardEngine.run(base(end), FixedStrategy::new, List.of(), List.of(), 1, 1))
				.isEmpty();
	}
}
