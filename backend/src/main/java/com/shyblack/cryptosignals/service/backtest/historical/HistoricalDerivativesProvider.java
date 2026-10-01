package com.shyblack.cryptosignals.service.backtest.historical;

import com.shyblack.cryptosignals.market.DerivativesSnapshot;
import java.time.Instant;

/**
 * As-of derivatives lookup for backtests. Implementations MUST only use
 * observations with {@code ts <= time} — never a future OI/funding/liquidation
 * value. Missing data is reported as unavailable/UNKNOWN, never as zero.
 */
public interface HistoricalDerivativesProvider {

	DerivativesSnapshot asOf(String symbol, Instant time);
}
