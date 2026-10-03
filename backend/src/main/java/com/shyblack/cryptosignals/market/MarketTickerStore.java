package com.shyblack.cryptosignals.market;

import com.shyblack.cryptosignals.entity.enums.TradingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Live quotes for a single market.
 *
 * <p>The store is bound to one {@link TradingMode} at construction. Tickers are keyed by exchange
 * symbol, which is unambiguous <em>within</em> a store but would collide across the spot and
 * futures stores for symbols like {@code BTCUSDT}. Fixing the market on the store is what makes a
 * symbol-keyed cache safe: there are two stores, each fed only by its own market's feeds, and
 * {@link MarketBook#tickers(TradingMode)} hands out the right one.
 *
 * <p>Batch listeners are invoked with the store's own market, so a consumer reacting to a price
 * change learns which market moved without having to infer it.
 */
public class MarketTickerStore {

	private final TradingMode mode;
	private final ConcurrentHashMap<String, MarketTicker> tickers = new ConcurrentHashMap<>();
	private final CopyOnWriteArrayList<MarketBatchListener> batchListeners = new CopyOnWriteArrayList<>();

	public MarketTickerStore(TradingMode mode) {
		if (mode == null) {
			throw new IllegalArgumentException("A ticker store must know which market it holds");
		}
		if (mode == TradingMode.OPTIONS) {
			throw new IllegalArgumentException("OPTIONS has no ticker store");
		}
		this.mode = mode;
	}

	public TradingMode mode() {
		return mode;
	}

	public void upsert(MarketTicker ticker) {
		upsertAll(List.of(ticker));
	}

	/**
	 * Writes tickers and notifies listeners only for symbols whose quoted fields changed.
	 */
	public List<MarketTicker> upsertAll(Collection<MarketTicker> incoming) {
		if (incoming == null || incoming.isEmpty()) {
			return List.of();
		}
		List<MarketTicker> changed = new ArrayList<>();
		for (MarketTicker ticker : incoming) {
			if (ticker == null || ticker.symbol() == null) {
				continue;
			}
			MarketTicker previous = tickers.put(ticker.symbol(), ticker);
			if (previous == null || quotesDiffer(previous, ticker)) {
				changed.add(ticker);
			}
		}
		if (!changed.isEmpty()) {
			List<MarketTicker> immutable = List.copyOf(changed);
			batchListeners.forEach(listener -> listener.onBatch(mode, immutable));
		}
		return changed;
	}

	public void retainOnly(Set<String> allowedSymbols) {
		tickers.keySet().removeIf(symbol -> !allowedSymbols.contains(symbol));
	}

	public void clear() {
		tickers.clear();
	}

	public List<MarketTicker> snapshot() {
		return tickers.values().stream()
				.sorted(Comparator.comparing(MarketTicker::symbol))
				.toList();
	}

	public Optional<MarketTicker> get(String symbol) {
		return Optional.ofNullable(tickers.get(normalize(symbol)));
	}

	public List<MarketTicker> gainers(int limit) {
		return tickers.values().stream()
				.sorted(Comparator.comparing(MarketTicker::changePercent24h).reversed())
				.limit(limit)
				.toList();
	}

	public List<MarketTicker> losers(int limit) {
		return tickers.values().stream()
				.sorted(Comparator.comparing(MarketTicker::changePercent24h))
				.limit(limit)
				.toList();
	}

	public void addBatchListener(MarketBatchListener listener) {
		batchListeners.add(listener);
	}

	public static String normalize(String symbol) {
		return symbol == null ? "" : symbol.trim().toUpperCase();
	}

	private static boolean quotesDiffer(MarketTicker previous, MarketTicker next) {
		return previous.price().compareTo(next.price()) != 0
				|| previous.changePercent24h().compareTo(next.changePercent24h()) != 0
				|| previous.volume24h().compareTo(next.volume24h()) != 0
				|| previous.change24h().compareTo(next.change24h()) != 0
				|| previous.high24h().compareTo(next.high24h()) != 0
				|| previous.low24h().compareTo(next.low24h()) != 0;
	}
}
