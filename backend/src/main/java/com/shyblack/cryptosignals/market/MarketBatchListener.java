package com.shyblack.cryptosignals.market;

import com.shyblack.cryptosignals.entity.enums.TradingMode;
import java.util.List;

/**
 * Notified when quotes for one market change.
 *
 * <p>The market is part of the callback instead of being captured by the listener, because the
 * whole failure this interface exists to prevent is a consumer that cannot tell which market a
 * price belongs to. A listener receives {@code SPOT} updates and {@code FUTURES} updates through
 * the same method, so it is structurally impossible to treat them as one stream.
 */
@FunctionalInterface
public interface MarketBatchListener {

	/**
	 * @param mode the market every ticker in {@code tickers} belongs to; never {@code null}
	 * @param tickers only the symbols whose quoted fields changed, already normalised to this market
	 */
	void onBatch(TradingMode mode, List<MarketTicker> tickers);
}