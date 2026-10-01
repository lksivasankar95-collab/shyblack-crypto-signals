package com.shyblack.cryptosignals.service.backtest.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.BacktestRun;
import com.shyblack.cryptosignals.entity.enums.BacktestExecutionModel;
import com.shyblack.cryptosignals.entity.enums.BacktestSameCandlePolicy;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalEvent;
import com.shyblack.cryptosignals.service.backtest.strategy.EventAwareBacktestStrategy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * Proves the event stream is filtered by candle close time: the strategy is
 * never handed an event dated after the candle being evaluated.
 */
class BacktestEngineEventNoLookAheadTest {

	private static final long MIN = 60_000L;

	/** Records how many events were visible at each candle. */
	private static final class CapturingStrategy implements EventAwareBacktestStrategy {
		final List<Integer> visibleCounts = new ArrayList<>();

		@Override public String id() { return "capture"; }
		@Override public String version() { return "v1"; }
		@Override public int warmup() { return 0; }

		@Override
		public Optional<Signal> evaluate(List<HistoricalCandle> history, int currentIndex,
				List<HistoricalEvent> eventsUpToNow) {
			visibleCounts.add(eventsUpToNow.size());
			return Optional.empty();
		}
	}

	private static HistoricalCandle candle(int index) {
		Instant open = Instant.ofEpochMilli(1_700_000_000_000L + (long) index * MIN);
		BigDecimal p = BigDecimal.valueOf(100 + index).setScale(8, RoundingMode.HALF_UP);
		return new HistoricalCandle(open, p, p, p, p, BigDecimal.ONE, open.plusMillis(MIN));
	}

	private static HistoricalEvent event(Instant time) {
		return new HistoricalEvent(java.util.UUID.randomUUID(), time, "BTCUSDT", null, null, null,
				null, null, null, null, null, null, null);
	}

	@Test
	void neverExposesFutureEvents() {
		List<HistoricalCandle> candles = new ArrayList<>();
		for (int i = 0; i < 5; i++) candles.add(candle(i));

		List<HistoricalEvent> events = List.of(
				event(candles.get(0).closeTime()),
				event(candles.get(2).closeTime()),
				event(candles.get(4).closeTime()));

		BacktestConfig config = new BacktestConfig("capture", "BTCUSDT", "1m", TradingMode.FUTURES,
				candles.get(0).openTime(), candles.get(4).closeTime(),
				new BigDecimal("1000"), new BigDecimal("2"), new BigDecimal("0.1"),
				new BigDecimal("0.05"), 1, BacktestExecutionModel.NEXT_CANDLE_OPEN,
				BacktestSameCandlePolicy.SL_FIRST, null);

		CapturingStrategy strategy = new CapturingStrategy();
		BacktestEngine.run(new BacktestRun(), config, strategy, candles, events, new AtomicBoolean(false));

		// Candle 0 → 1 event; 1 → 1; 2 → 2; 3 → 2; 4 → 3.
		assertThat(strategy.visibleCounts).containsExactly(1, 1, 2, 2, 3);
	}

	@Test
	void eventExactlyAtClose_isVisible_eventImmediatelyAfterClose_isNot() {
		List<HistoricalCandle> candles = new ArrayList<>();
		for (int i = 0; i < 3; i++) candles.add(candle(i));

		// Exactly at candle 0 close (included) and 1ms after candle 1 close (excluded at candle 1).
		List<HistoricalEvent> events = List.of(
				event(candles.get(0).closeTime()),
				event(candles.get(1).closeTime().plusMillis(1)));

		BacktestConfig config = new BacktestConfig("capture", "BTCUSDT", "1m", TradingMode.FUTURES,
				candles.get(0).openTime(), candles.get(2).closeTime(),
				new BigDecimal("1000"), new BigDecimal("2"), new BigDecimal("0.1"),
				new BigDecimal("0.05"), 1, BacktestExecutionModel.NEXT_CANDLE_OPEN,
				BacktestSameCandlePolicy.SL_FIRST, null);

		CapturingStrategy strategy = new CapturingStrategy();
		BacktestEngine.run(new BacktestRun(), config, strategy, candles, events, new AtomicBoolean(false));

		// candle0: the at-close event only (1). candle1: still excludes the +1ms event (1).
		// candle2: both events now visible (2).
		assertThat(strategy.visibleCounts).containsExactly(1, 1, 2);
	}
}
