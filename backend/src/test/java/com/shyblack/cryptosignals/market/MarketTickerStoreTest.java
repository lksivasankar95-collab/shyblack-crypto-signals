package com.shyblack.cryptosignals.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shyblack.cryptosignals.entity.enums.TradingMode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MarketTickerStoreTest {

	@Test
	void notifiesOnlyWhenQuotesChange() {
		MarketTickerStore store = new MarketTickerStore(TradingMode.SPOT);
		AtomicInteger batches = new AtomicInteger();
		store.addBatchListener((mode, changed) -> batches.incrementAndGet());

		store.upsert(ticker("BTCUSDT", "65000", "1.0"));
		store.upsert(ticker("BTCUSDT", "65000", "1.0"));
		store.upsert(ticker("BTCUSDT", "65100", "1.1"));

		assertEquals(2, batches.get());
		assertEquals(new BigDecimal("65100"), store.get("BTCUSDT").orElseThrow().price());
	}

	@Test
	void upsertAllReturnsOnlyChangedSymbols() {
		MarketTickerStore store = new MarketTickerStore(TradingMode.SPOT);
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
		MarketTickerStore store = new MarketTickerStore(TradingMode.SPOT);
		store.upsert(ticker("BTCUSDT", "1", "0"));
		store.upsert(ticker("OLDUSDT", "1", "0"));
		store.retainOnly(java.util.Set.of("BTCUSDT"));
		assertTrue(store.get("OLDUSDT").isEmpty());
		assertTrue(store.get("BTCUSDT").isPresent());
		assertEquals(1, store.snapshot().size());
	}

	@Test
	void emptyUpsertDoesNotNotify() {
		MarketTickerStore store = new MarketTickerStore(TradingMode.SPOT);
		List<List<MarketTicker>> seen = new ArrayList<>();
		store.addBatchListener((mode, changed) -> seen.add(changed));
		store.upsertAll(List.of());
		assertTrue(seen.isEmpty());
	}

	/**
	 * A store is bound to one market, so it reports that market to its listeners rather than leaving
	 * them to infer it. A consumer that cannot tell which market moved is the defect this prevents.
	 */
	@Test
	void listenersReceiveTheMarketsOwnMode() {
		MarketTickerStore spot = new MarketTickerStore(TradingMode.SPOT);
		MarketTickerStore futures = new MarketTickerStore(TradingMode.FUTURES);
		List<TradingMode> seen = new ArrayList<>();
		spot.addBatchListener((mode, changed) -> seen.add(mode));
		futures.addBatchListener((mode, changed) -> seen.add(mode));

		spot.upsert(ticker("BTCUSDT", "100000", "0"));
		futures.upsert(ticker("BTCUSDT", "100500", "0"));

		assertEquals(List.of(TradingMode.SPOT, TradingMode.FUTURES), seen);
	}

	/**
	 * Two stores, same symbol, independent prices. The caches do not share a map, so an update on
	 * one market cannot disturb the other.
	 */
	@Test
	void sameSymbolOnTwoMarketsKeepsIndependentPrices() {
		MarketTickerStore spot = new MarketTickerStore(TradingMode.SPOT);
		MarketTickerStore futures = new MarketTickerStore(TradingMode.FUTURES);

		spot.upsert(ticker("BTCUSDT", "100000", "0"));
		futures.upsert(ticker("BTCUSDT", "100500", "0"));
		spot.upsert(ticker("BTCUSDT", "100010", "0"));

		assertEquals(new BigDecimal("100010"), spot.get("BTCUSDT").orElseThrow().price());
		assertEquals(new BigDecimal("100500"), futures.get("BTCUSDT").orElseThrow().price());
	}

	@Test
	void refusesToBeConstructedWithoutAMarket() {
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> new MarketTickerStore(null));
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