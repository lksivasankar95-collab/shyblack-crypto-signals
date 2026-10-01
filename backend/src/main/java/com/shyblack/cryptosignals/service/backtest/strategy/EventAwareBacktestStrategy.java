package com.shyblack.cryptosignals.service.backtest.strategy;

import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalEvent;
import java.util.List;
import java.util.Optional;

/**
 * A {@link BacktestStrategy} that also consumes an event stream. The engine
 * passes only events whose timestamp is at or before the current candle's
 * close time — the no-look-ahead guarantee holds for events exactly as it does
 * for candles.
 */
public interface EventAwareBacktestStrategy extends BacktestStrategy {

	/**
	 * @param history       candles up to and including the current candle.
	 * @param currentIndex  always {@code history.size() - 1}.
	 * @param eventsUpToNow events with {@code time <= history.get(currentIndex).closeTime()},
	 *                      oldest first. Callers must not mutate this list.
	 */
	Optional<Signal> evaluate(List<HistoricalCandle> history, int currentIndex,
			List<HistoricalEvent> eventsUpToNow);

	@Override
	default Optional<Signal> evaluate(List<HistoricalCandle> history, int currentIndex) {
		return evaluate(history, currentIndex, List.of());
	}
}
