package com.shyblack.cryptosignals.market;

import com.shyblack.cryptosignals.entity.enums.TradingMode;
import org.springframework.stereotype.Component;

/**
 * Separate in-memory books for Spot and USDT-M Futures. Options has no store.
 */
@Component
public class MarketBook {

	private final MarketTickerStore spotTickers = new MarketTickerStore();
	private final MarketTickerStore futuresTickers = new MarketTickerStore();
	private final UsdtSymbolDirectory spotSymbols = new UsdtSymbolDirectory();
	private final UsdtSymbolDirectory futuresSymbols = new UsdtSymbolDirectory();

	public MarketTickerStore spotTickers() {
		return spotTickers;
	}

	public MarketTickerStore futuresTickers() {
		return futuresTickers;
	}

	public UsdtSymbolDirectory spotSymbols() {
		return spotSymbols;
	}

	public UsdtSymbolDirectory futuresSymbols() {
		return futuresSymbols;
	}

	public MarketTickerStore tickers(TradingMode mode) {
		return switch (mode) {
			case SPOT -> spotTickers;
			case FUTURES -> futuresTickers;
			case OPTIONS -> throw new IllegalArgumentException("OPTIONS has no ticker store");
		};
	}

	public UsdtSymbolDirectory symbols(TradingMode mode) {
		return switch (mode) {
			case SPOT -> spotSymbols;
			case FUTURES -> futuresSymbols;
			case OPTIONS -> throw new IllegalArgumentException("OPTIONS has no symbol directory");
		};
	}
}
