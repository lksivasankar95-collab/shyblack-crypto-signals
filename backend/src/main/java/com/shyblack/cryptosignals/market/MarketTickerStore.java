package com.shyblack.cryptosignals.market;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class MarketTickerStore {

	private final ConcurrentHashMap<String, MarketTicker> tickers = new ConcurrentHashMap<>();
	private final CopyOnWriteArrayList<Consumer<List<MarketTicker>>> batchListeners = new CopyOnWriteArrayList<>();

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
			batchListeners.forEach(listener -> listener.accept(immutable));
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

	public void addBatchListener(Consumer<List<MarketTicker>> listener) {
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
