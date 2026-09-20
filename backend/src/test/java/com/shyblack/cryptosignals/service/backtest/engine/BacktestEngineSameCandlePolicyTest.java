package com.shyblack.cryptosignals.service.backtest.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.BacktestRun;
import com.shyblack.cryptosignals.entity.enums.BacktestExecutionModel;
import com.shyblack.cryptosignals.entity.enums.BacktestExitReason;
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
import org.junit.jupiter.api.Test;

/**
 * Phase-47 test: a candle whose OHLC touches BOTH SL and TP must resolve to
 * SL_FIRST under the CONSERVATIVE / SL_FIRST policies. Never automatically
 * choose the profitable outcome.
 */
class BacktestEngineSameCandlePolicyTest {

	private static HistoricalCandle candle(int i, double o, double h, double l, double c) {
		Instant t = Instant.parse("2024-01-01T00:00:00Z").plusSeconds(3600L * i);
		return new HistoricalCandle(t,
				BigDecimal.valueOf(o), BigDecimal.valueOf(h),
				BigDecimal.valueOf(l), BigDecimal.valueOf(c),
				BigDecimal.valueOf(100), t.plusSeconds(3600));
	}

	/** Strategy fires exactly once at candle index 0 (LONG). */
	static class OneShotStrategy implements BacktestStrategy {
		@Override public String id() { return "one-shot"; }
		@Override public String version() { return "v1"; }
		@Override public int warmup() { return 0; }
		@Override
		public Optional<Signal> evaluate(List<HistoricalCandle> history, int currentIndex) {
			if (currentIndex != 0) return Optional.empty();
			return Optional.of(new Signal(PositionSide.LONG,
					BigDecimal.valueOf(100),
					BigDecimal.valueOf(90),   // SL
					BigDecimal.valueOf(110),  // TP
					"one shot"));
		}
	}

	@Test
	void bothTouched_underSL_FIRST_producesStopLossExit() {
		List<HistoricalCandle> candles = new ArrayList<>();
		candles.add(candle(0, 100, 100, 100, 100));     // signal at close
		candles.add(candle(1, 100, 101, 99, 100));      // entry fills at open=100
		candles.add(candle(2, 100, 115, 85, 100));      // both SL and TP touched (post-entry)
		candles.add(candle(3, 100, 101, 99, 100));

		BacktestConfig cfg = new BacktestConfig(
				"one-shot", "BTCUSDT", "1h", TradingMode.SPOT,
				candles.get(0).openTime(),
				candles.get(candles.size() - 1).closeTime(),
				new BigDecimal("10000"), new BigDecimal("1"),
				BigDecimal.ZERO, BigDecimal.ZERO, 1,
				BacktestExecutionModel.NEXT_CANDLE_OPEN,
				BacktestSameCandlePolicy.SL_FIRST);

		BacktestEngine.Result result = BacktestEngine.run(
				new BacktestRun(), cfg, new OneShotStrategy(), candles, null);

		assertThat(result.trades()).hasSize(1);
		assertThat(result.trades().get(0).getExitReason()).isEqualTo(BacktestExitReason.STOP_LOSS);
	}

	@Test
	void tp_only_touched_producesTakeProfitExit() {
		List<HistoricalCandle> candles = new ArrayList<>();
		candles.add(candle(0, 100, 100, 100, 100));
		candles.add(candle(1, 100, 101, 99, 100));     // entry fill
		candles.add(candle(2, 100, 115, 99, 110));     // TP touched, SL not (post-entry)
		candles.add(candle(3, 100, 101, 99, 100));

		BacktestConfig cfg = new BacktestConfig(
				"one-shot", "BTCUSDT", "1h", TradingMode.SPOT,
				candles.get(0).openTime(),
				candles.get(candles.size() - 1).closeTime(),
				new BigDecimal("10000"), new BigDecimal("1"),
				BigDecimal.ZERO, BigDecimal.ZERO, 1,
				BacktestExecutionModel.NEXT_CANDLE_OPEN,
				BacktestSameCandlePolicy.SL_FIRST);

		BacktestEngine.Result result = BacktestEngine.run(
				new BacktestRun(), cfg, new OneShotStrategy(), candles, null);

		assertThat(result.trades()).hasSize(1);
		assertThat(result.trades().get(0).getExitReason()).isEqualTo(BacktestExitReason.TAKE_PROFIT);
	}

	@Test
	void end_of_test_closesOpenPosition() {
		// TP and SL never trigger; the engine must close at the last candle.
		List<HistoricalCandle> candles = new ArrayList<>();
		candles.add(candle(0, 100, 100, 100, 100));
		for (int i = 1; i < 4; i++) candles.add(candle(i, 100, 101, 99, 100));

		BacktestConfig cfg = new BacktestConfig(
				"one-shot", "BTCUSDT", "1h", TradingMode.SPOT,
				candles.get(0).openTime(),
				candles.get(candles.size() - 1).closeTime(),
				new BigDecimal("10000"), new BigDecimal("1"),
				BigDecimal.ZERO, BigDecimal.ZERO, 1,
				BacktestExecutionModel.NEXT_CANDLE_OPEN,
				BacktestSameCandlePolicy.SL_FIRST);

		BacktestEngine.Result result = BacktestEngine.run(
				new BacktestRun(), cfg, new OneShotStrategy(), candles, null);

		assertThat(result.trades()).hasSize(1);
		assertThat(result.trades().get(0).getExitReason()).isEqualTo(BacktestExitReason.END_OF_TEST);
	}
}
