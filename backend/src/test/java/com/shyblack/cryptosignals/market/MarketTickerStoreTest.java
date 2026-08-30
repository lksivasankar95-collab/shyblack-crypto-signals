package com.shyblack.cryptosignals.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MarketTickerStoreTest {

	@Test
	void notifiesOnlyWhenQuotesChange() {
		MarketTickerStore store = new MarketTickerStore();
		AtomicInteger batches = new AtomicInteger();
		store.addBatchListener(changed -> batches.incrementAndGet());

		store.upsert(ticker("BTCUSDT", "65000", "1.0"));
		store.upsert(ticker("BTCUSDT", "65000", "1.0"));
		store.upsert(ticker("BTCUSDT", "65100", "1.1"));

		assertEquals(2, batches.get());
		assertEquals(new BigDecimal("65100"), store.get("BTCUSDT").orElseThrow().price());
	}

	@Test
	void upsertAllReturnsOnlyChangedSymbols() {
		MarketTickerStore store = new MarketTickerStore();
		store.upsert(ticker("BTCUSDT", "65000", "1.0"));
		List<MarketTicker> changed = store.upsertAll(List.of(
				ticker("BTCUSDT", "65000", "1.0"),
				ticker("ETHUSDT", "3400", "-1.0")
		));
		assertEquals(1, changed.size());
		assertEquals("ETHUSDT", changed.get(0).symbol());
	}

	@Test
	void retainOnlyDropsDelistedSymbols() {
		MarketTickerStore store = new MarketTickerStore();
		store.upsert(ticker("BTCUSDT", "1", "0"));
		store.upsert(ticker("OLDUSDT", "1", "0"));
		store.retainOnly(java.util.Set.of("BTCUSDT"));
		assertTrue(store.get("OLDUSDT").isEmpty());
		assertTrue(store.get("BTCUSDT").isPresent());
		assertEquals(1, store.snapshot().size());
	}

	@Test
	void emptyUpsertDoesNotNotify() {
		MarketTickerStore store = new MarketTickerStore();
		List<List<MarketTicker>> seen = new ArrayList<>();
		store.addBatchListener(seen::add);
		store.upsertAll(List.of());
		assertTrue(seen.isEmpty());
	}

	private static MarketTicker ticker(String symbol, String price, String changePct) {
		return new MarketTicker(
				symbol,
				symbol,
				new BigDecimal(price),
				BigDecimal.ONE,
				new BigDecimal(changePct),
				new BigDecimal("1000"),
				new BigDecimal(price).add(BigDecimal.TEN),
				new BigDecimal(price).subtract(BigDecimal.TEN),
				Instant.parse("2026-08-30T12:00:00Z")
		);
	}
}
