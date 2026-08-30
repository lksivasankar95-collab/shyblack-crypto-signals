package com.shyblack.cryptosignals.market;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class MarketTickerStore {

	private final ConcurrentHashMap<String, MarketTicker> tickers = new ConcurrentHashMap<>();
	private final CopyOnWriteArrayList<Consumer<MarketTicker>> listeners = new CopyOnWriteArrayList<>();

	public void upsert(MarketTicker ticker) {
		tickers.put(ticker.symbol(), ticker);
		listeners.forEach(listener -> listener.accept(ticker));
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

	public void addListener(Consumer<MarketTicker> listener) {
		listeners.add(listener);
	}

	public static String normalize(String symbol) {
		return symbol == null ? "" : symbol.trim().toUpperCase();
	}
}
