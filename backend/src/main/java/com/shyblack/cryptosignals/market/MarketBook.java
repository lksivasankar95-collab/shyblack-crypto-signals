package com.shyblack.cryptosignals.market;

import com.shyblack.cryptosignals.config.MarketProperties;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Separate in-memory books for Spot and USDT-M Futures. Options has no store.
 *
 * <p>Every store and directory in here is bound to exactly one market at construction, and this
 * class is the only way to reach them. That is deliberate: {@code BTCUSDT} names a spot pair and a
 * futures perpetual, so any lookup that does not carry a market has to guess, and the two guesses
 * differ in price. Routing every access through {@link #tickers(TradingMode)} and
 * {@link #symbols(TradingMode)} means the market is always supplied by the caller and never
 * inferred from a symbol.
 */
@Component
public class MarketBook {

	private final MarketTickerStore spotTickers;
	private final MarketTickerStore futuresTickers;
	private final UsdtSymbolDirectory spotSymbols;
	private final UsdtSymbolDirectory futuresSymbols;

	@Autowired
	public MarketBook(MarketProperties properties) {
		String quoteAsset = properties.quoteAssetOrUsdt();
		this.spotTickers = new MarketTickerStore(TradingMode.SPOT);
		this.futuresTickers = new MarketTickerStore(TradingMode.FUTURES);
		this.spotSymbols = new UsdtSymbolDirectory(TradingMode.SPOT, quoteAsset);
		this.futuresSymbols = new UsdtSymbolDirectory(TradingMode.FUTURES, quoteAsset);
	}

	/** Default-quote constructor for tests that do not exercise a non-USDT quote asset. */
	public MarketBook() {
		this.spotTickers = new MarketTickerStore(TradingMode.SPOT);
		this.futuresTickers = new MarketTickerStore(TradingMode.FUTURES);
		this.spotSymbols = new UsdtSymbolDirectory(TradingMode.SPOT);
		this.futuresSymbols = new UsdtSymbolDirectory(TradingMode.FUTURES);
	}

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

	/**
	 * The price of {@code symbol} on {@code mode} and nothing else.
	 *
	 * <p>This is the lookup that must never be written as a symbol-only probe. A spot-first
	 * "find BTCUSDT anywhere" returns the spot price for a futures position and quietly marks it to
	 * the wrong market; a futures-first probe does the same in the other direction. Callers that
	 * hold a market and a symbol should use this, and callers that hold only a symbol do not have
	 * enough information to price anything.
	 */
	public Optional<MarketTicker> ticker(TradingMode mode, String symbol) {
		if (mode == null || mode == TradingMode.OPTIONS) {
			return Optional.empty();
		}
		return tickers(mode).get(symbol);
	}

	/** The authoritative identity of {@code symbol} on {@code mode}, for display and routing. */
	public Optional<MarketInstrument> instrument(TradingMode mode, String symbol) {
		if (mode == null || mode == TradingMode.OPTIONS) {
			return Optional.empty();
		}
		return symbols(mode).instrument(symbol);
	}
}