package com.shyblack.cryptosignals.service.backtest.strategy;

import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.service.backtest.historical.HistoricalCandle;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * A pluggable backtest strategy. Given the candles up to (and including) the
 * current candle, decide whether to emit a signal. The engine guarantees the
 * strategy never sees candles beyond the current index — this prevents
 * look-ahead by construction.
 *
 * Strategy authors MUST NOT introduce randomness or reach into out-of-band
 * data sources; the backtest result must be deterministic per config +
 * strategy version + engine version.
 */
public interface BacktestStrategy {

	/** Identifier stored on every persisted run + trade for reproducibility. */
	String id();

	/** Bump this whenever the strategy's decision logic materially changes. */
	String version();

	/**
	 * @param history      candles up to and INCLUDING the current candle
	 *                     (never further). Oldest first.
	 * @param currentIndex convenience — always {@code history.size() - 1}.
	 * @return an optional signal; empty when no trade condition is met.
	 */
	Optional<Signal> evaluate(List<HistoricalCandle> history, int currentIndex);

	/**
	 * Minimum bars of warmup the strategy requires before it can emit
	 * meaningful signals. The engine will skip signal emission until this
	 * threshold is reached, though it still processes candles for
	 * position-monitoring purposes.
	 */
	default int warmup() { return 50; }

	/**
	 * Emitted signal — entry, side, SL, TP. `referencePrice` is the price
	 * the strategy would have observed when deciding (usually the current
	 * candle's close); the engine uses it only for record-keeping.
	 */
	record Signal(
			PositionSide side,
			BigDecimal referencePrice,
			BigDecimal stopLoss,
			BigDecimal takeProfit,
			String notes
	) {}
}
