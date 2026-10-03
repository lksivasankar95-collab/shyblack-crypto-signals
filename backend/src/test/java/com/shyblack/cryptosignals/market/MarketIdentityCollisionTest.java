package com.shyblack.cryptosignals.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.enums.TradingMode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The end-to-end proof that one symbol can no longer address two markets.
 *
 * <p>{@code BTCUSDT} is simultaneously a live spot pair and a live USDT-M futures perpetual. Every
 * failure this suite exists to catch has the same shape: some layer takes the symbol, drops the
 * market, and resolves the price from whichever market it happened to check first. The synthetic
 * prices make that visible — if spot and futures prices are equal, a collision is invisible.
 */
class MarketIdentityCollisionTest {

	/** Distinct synthetic prices so a cross-market read is detected rather than coincidentally matching. */
	private static final String SPOT_PRICE = "100000";
	private static final String FUTURES_PRICE = "100500";

	@Test
	@DisplayName("MarketBook resolves the same symbol to different prices per market")
	void marketBookResolvesPerMarket() {
		MarketBook book = new MarketBook();
		book.spotTickers().upsert(ticker("BTCUSDT", SPOT_PRICE));
		book.futuresTickers().upsert(ticker("BTCUSDT", FUTURES_PRICE));

		assertThat(book.ticker(TradingMode.SPOT, "BTCUSDT").orElseThrow().price())
				.isEqualByComparingTo(SPOT_PRICE);
		assertThat(book.ticker(TradingMode.FUTURES, "BTCUSDT").orElseThrow().price())
				.isEqualByComparingTo(FUTURES_PRICE);
	}

	@Test
	@DisplayName("A futures tick never disturbs the spot price for the same symbol")
	void futuresTickDoesNotOverwriteSpot() {
		MarketBook book = new MarketBook();
		book.spotTickers().upsert(ticker("BTCUSDT", SPOT_PRICE));
		book.futuresTickers().upsert(ticker("BTCUSDT", FUTURES_PRICE));

		// A second futures quote on the same symbol.
		book.futuresTickers().upsert(ticker("BTCUSDT", "100900"));

		assertThat(book.ticker(TradingMode.SPOT, "BTCUSDT").orElseThrow().price())
				.isEqualByComparingTo(SPOT_PRICE);
		assertThat(book.ticker(TradingMode.FUTURES, "BTCUSDT").orElseThrow().price())
				.isEqualByComparingTo("100900");
	}

	@Test
	@DisplayName("Spot and futures listeners each receive only their own market")
	void listenersAreMarketScoped() {
		MarketBook book = new MarketBook();
		List<TradingMode> modes = new ArrayList<>();
		List<String> symbols = new ArrayList<>();
		book.spotTickers().addBatchListener((mode, changed) -> {
			modes.add(mode);
			changed.forEach(t -> symbols.add(mode + ":" + t.symbol()));
		});
		book.futuresTickers().addBatchListener((mode, changed) -> {
			modes.add(mode);
			changed.forEach(t -> symbols.add(mode + ":" + t.symbol()));
		});

		book.spotTickers().upsert(ticker("BTCUSDT", SPOT_PRICE));
		book.futuresTickers().upsert(ticker("BTCUSDT", FUTURES_PRICE));

		assertThat(modes).containsExactly(TradingMode.SPOT, TradingMode.FUTURES);
		assertThat(symbols).containsExactly("SPOT:BTCUSDT", "FUTURES:BTCUSDT");
	}

	@Test
	@DisplayName("Each symbol directory reports its own market and refuses foreign instruments")
	void directoriesAreBoundToOneMarket() {
		MarketBook book = new MarketBook();
		book.spotSymbols().replaceInstruments(List.of(
				new MarketInstrument(TradingMode.SPOT, "BTCUSDT", "BTC", "USDT", null, null)));
		book.futuresSymbols().replaceInstruments(List.of(
				new MarketInstrument(TradingMode.FUTURES, "BTCUSDT", "BTC", "USDT", "PERPETUAL", null)));

		assertThat(book.spotSymbols().mode()).isEqualTo(TradingMode.SPOT);
		assertThat(book.futuresSymbols().mode()).isEqualTo(TradingMode.FUTURES);
		assertThat(book.spotSymbols().contains("BTCUSDT")).isTrue();
		assertThat(book.futuresSymbols().contains("BTCUSDT")).isTrue();

		// A futures instrument may not be installed into the spot directory, and vice versa.
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> book.spotSymbols().replaceInstruments(List.of(
						new MarketInstrument(TradingMode.FUTURES, "BTCUSDT", "BTC", "USDT", "PERPETUAL", null))));
	}

	@Test
	@DisplayName("Each directory resolves the shared symbol to its own instrument identity")
	void directoriesResolveToDistinctIdentities() {
		MarketBook book = new MarketBook();
		book.spotSymbols().replaceInstruments(List.of(
				new MarketInstrument(TradingMode.SPOT, "BTCUSDT", "BTC", "USDT", null, null)));
		book.futuresSymbols().replaceInstruments(List.of(
				new MarketInstrument(TradingMode.FUTURES, "BTCUSDT", "BTC", "USDT", "PERPETUAL", null)));

		MarketInstrument spot = book.symbols(TradingMode.SPOT).instrument("BTCUSDT").orElseThrow();
		MarketInstrument perp = book.symbols(TradingMode.FUTURES).instrument("BTCUSDT").orElseThrow();

		// Both share the exchange symbol; identity is what tells them apart.
		assertThat(spot.exchangeSymbol()).isEqualTo(perp.exchangeSymbol());
		assertThat(spot.marketType()).isNotEqualTo(perp.marketType());
		// A spot pair has no contract type; the perpetual does.
		assertThat(spot.contractType()).isNull();
		assertThat(perp.contractType()).isEqualTo("PERPETUAL");
	}

	@Test
	@DisplayName("Display labels keep the perpetual distinguishable from the spot pair")
	void displayLabelsAreDistinct() {
		MarketInstrument spot = new MarketInstrument(TradingMode.SPOT, "BTCUSDT", "BTC", "USDT", null, null);
		MarketInstrument perp = new MarketInstrument(TradingMode.FUTURES, "BTCUSDT", "BTC", "USDT", "PERPETUAL", null);

		// Same exchange symbol, different labels.
		assertThat(spot.exchangeSymbol()).isEqualTo(perp.exchangeSymbol());
		assertThat(spot.displaySymbol()).isEqualTo("BTC/USDT");
		assertThat(perp.displaySymbol()).isEqualTo("BTCUSDT Perpetual");
		assertThat(spot.displaySymbol()).isNotEqualTo(perp.displaySymbol());
	}

	/**
	 * Binance does not omit a perpetual's settlement date; it sends a sentinel (0 for USDⓈ-M, a
	 * year-2100 value for dated contracts). Taken at face value that renders a perpetual as
	 * "BTCUSDT Perpetual 2100-12-25", which is a fabricated expiry on a contract that has none.
	 */
	@Test
	@DisplayName("A perpetual never carries an expiry date")
	void perpetualHasNoExpiry() {
		MarketInstrument sentinelZero = new MarketInstrument(
				TradingMode.FUTURES, "BTCUSDT", "BTC", "USDT", "PERPETUAL", LocalDate.of(1970, 1, 1));
		MarketInstrument sentinelFarFuture = new MarketInstrument(
				TradingMode.FUTURES, "BTCUSDT", "BTC", "USDT", "PERPETUAL", LocalDate.of(2100, 12, 31));

		assertThat(sentinelZero.contractExpiry()).isNull();
		assertThat(sentinelFarFuture.contractExpiry()).isNull();
		assertThat(sentinelZero.displaySymbol()).isEqualTo("BTCUSDT Perpetual");
		assertThat(sentinelFarFuture.displaySymbol()).isEqualTo("BTCUSDT Perpetual");
	}

	/** A dated contract keeps its real settlement date, which is what tells it from a perpetual. */
	@Test
	@DisplayName("A dated contract keeps its settlement date")
	void datedContractKeepsExpiry() {
		MarketInstrument quarter = new MarketInstrument(
				TradingMode.FUTURES, "BTCUSDT", "BTC", "USDT", "CURRENT_QUARTER", LocalDate.of(2026, 12, 25));

		assertThat(quarter.contractExpiry()).isEqualTo(LocalDate.of(2026, 12, 25));
		assertThat(quarter.displaySymbol()).isEqualTo("BTCUSDT Quarterly 2026-12-25");
	}

	private static MarketTicker ticker(String symbol, String price) {
		return new MarketTicker(
				symbol,
				symbol,
				new BigDecimal(price),
				BigDecimal.ONE,
				BigDecimal.ZERO,
				new BigDecimal("1000"),
				new BigDecimal(price),
				new BigDecimal(price),
				Instant.parse("2026-08-30T12:00:00Z")
		);
	}
}