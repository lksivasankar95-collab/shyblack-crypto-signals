package com.shyblack.cryptosignals.market;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe allow-list of USDT-quoted symbols plus display names from exchangeInfo.
 */
public class UsdtSymbolDirectory {

	private volatile Set<String> symbols = Set.of();
	private final ConcurrentHashMap<String, String> names = new ConcurrentHashMap<>();

	public boolean contains(String symbol) {
		return symbols.contains(MarketTickerStore.normalize(symbol));
	}

	public Set<String> snapshot() {
		return symbols;
	}

	public String name(String symbol) {
		String normalized = MarketTickerStore.normalize(symbol);
		String cached = names.get(normalized);
		if (cached != null) {
			return cached;
		}
		if (normalized.endsWith("USDT") && normalized.length() > 4) {
			return normalized.substring(0, normalized.length() - 4);
		}
		return normalized;
	}

	public void replace(Map<String, String> symbolToName) {
		Set<String> next = ConcurrentHashMap.newKeySet();
		next.addAll(symbolToName.keySet());
		this.symbols = Collections.unmodifiableSet(next);
		names.clear();
		names.putAll(symbolToName);
	}
}
