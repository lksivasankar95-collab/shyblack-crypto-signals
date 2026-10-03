package com.shyblack.cryptosignals.market;

import com.shyblack.cryptosignals.entity.enums.TradingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe allow-list of one market's tradable instruments, together with the authoritative
 * identity Binance reports for each of them.
 *
 * <p>The directory is bound to exactly one {@link TradingMode}. That binding is the point: a
 * caller that asks this directory about {@code BTCUSDT} gets the spot pair, and the futures
 * directory returns the perpetual. Because the market is fixed at construction, a symbol-only
 * lookup inside a directory is unambiguous â€” the ambiguity only ever appears when the market is
 * chosen at read time, which is what this type exists to prevent.
 */
public class UsdtSymbolDirectory {

	private static final String DEFAULT_QUOTE_ASSET = "USDT";

	private final TradingMode mode;
	private final String quoteAsset;

	private volatile Set<String> symbols = Set.of();
	private final ConcurrentHashMap<String, MarketInstrument> instruments = new ConcurrentHashMap<>();

	public UsdtSymbolDirectory(TradingMode mode) {
		this(mode, DEFAULT_QUOTE_ASSET);
	}

	public UsdtSymbolDirectory(TradingMode mode, String quoteAsset) {
		if (mode == null) {
			throw new IllegalArgumentException("A symbol directory must know which market it describes");
		}
		if (mode == TradingMode.OPTIONS) {
			throw new IllegalArgumentException("OPTIONS has no USDT symbol directory");
		}
		this.mode = mode;
		this.quoteAsset = quoteAsset == null || quoteAsset.isBlank()
				? DEFAULT_QUOTE_ASSET
				: quoteAsset.trim().toUpperCase();
	}

	public TradingMode mode() {
		return mode;
	}

	public String quoteAsset() {
		return quoteAsset;
	}

	public boolean contains(String symbol) {
		return symbols.contains(MarketTickerStore.normalize(symbol));
	}

	public Set<String> snapshot() {
		return symbols;
	}

	/** The full identity of one instrument on this market, if the market lists it. */
	public Optional<MarketInstrument> instrument(String symbol) {
		return Optional.ofNullable(instruments.get(MarketTickerStore.normalize(symbol)));
	}

	public Collection<MarketInstrument> instrumentSnapshot() {
		return List.copyOf(instruments.values());
	}

	public String name(String symbol) {
		return instrument(symbol)
				.map(MarketInstrument::nameOrBase)
				.orElseGet(() -> fallbackName(symbol));
	}

	/** Label to show in a user-facing list, distinguishing a perpetual from the spot pair. */
	public String displaySymbol(String symbol) {
		return instrument(symbol)
				.map(MarketInstrument::displaySymbol)
				.orElseGet(() -> fallbackName(symbol));
	}

	/** Replaces the universe with instruments whose identity came from Binance {@code exchangeInfo}. */
	public void replaceInstruments(Collection<MarketInstrument> next) {
		ConcurrentHashMap<String, MarketInstrument> bySymbol = new ConcurrentHashMap<>();
		Set<String> allowed = ConcurrentHashMap.newKeySet();
		for (MarketInstrument instrument : next) {
			if (instrument == null) {
				continue;
			}
			if (instrument.marketType() != mode) {
				throw new IllegalArgumentException(
						"Refusing to install " + instrument.marketType() + " instrument "
								+ instrument.exchangeSymbol() + " into the " + mode + " directory");
			}
			bySymbol.put(instrument.exchangeSymbol(), instrument);
			allowed.add(instrument.exchangeSymbol());
		}
		this.symbols = Collections.unmodifiableSet(allowed);
		this.instruments.clear();
		this.instruments.putAll(bySymbol);
	}

	/**
	 * Replaces the universe from a bare symbol-to-name mapping.
	 *
	 * <p>Retained for callers that only know a display name. The market and quote asset are filled
	 * in from this directory's own binding rather than guessed from the symbol text.
	 */
	public void replace(Map<String, String> symbolToName) {
		List<MarketInstrument> next = new ArrayList<>(symbolToName.size());
		for (Map.Entry<String, String> entry : symbolToName.entrySet()) {
			String symbol = MarketTickerStore.normalize(entry.getKey());
			String base = entry.getValue() == null || entry.getValue().isBlank() ? symbol : entry.getValue();
			next.add(new MarketInstrument(mode, symbol, base, quoteAsset, null, null));
		}
		replaceInstruments(next);
	}

	private static String fallbackName(String symbol) {
		String normalized = MarketTickerStore.normalize(symbol);
		if (normalized.endsWith(DEFAULT_QUOTE_ASSET) && normalized.length() > 4) {
			return normalized.substring(0, normalized.length() - 4);
		}
		return normalized;
	}
}
