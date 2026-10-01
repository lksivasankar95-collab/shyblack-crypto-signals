package com.shyblack.cryptosignals.service.backtest.historical;

import java.time.Instant;
import java.util.List;

/**
 * Source of historical NFM events for a backtest run. Implementations must
 * return events ordered by time ascending and only within {@code [start, end)}.
 */
public interface HistoricalEventProvider {

	List<HistoricalEvent> load(String symbol, Instant start, Instant end);
}
