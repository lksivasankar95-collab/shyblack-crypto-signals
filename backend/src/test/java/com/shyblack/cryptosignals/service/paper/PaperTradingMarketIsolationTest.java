package com.shyblack.cryptosignals.service.paper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.config.PaperTradingProperties;
import com.shyblack.cryptosignals.entity.Position;
import com.shyblack.cryptosignals.entity.Portfolio;
import com.shyblack.cryptosignals.entity.enums.AccountType;
import com.shyblack.cryptosignals.entity.enums.PositionSide;
import com.shyblack.cryptosignals.entity.enums.PositionStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.market.MarketBook;
import com.shyblack.cryptosignals.market.MarketTicker;
import com.shyblack.cryptosignals.repository.PortfolioRepository;
import com.shyblack.cryptosignals.repository.PositionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A spot position and a futures position can be open on {@code BTCUSDT} at the same time.
 *
 * <p>Before this fix, paper trading could not tell them apart. The tick handler was registered
 * against both markets' stores without knowing which market produced the tick, position lookup
 * matched on symbol alone, and price resolution probed spot before futures. The combined result was
 * that a futures tick evaluated a spot position against the futures price and could close it on a
 * stop-loss or take-profit that the spot market never reached.
 *
 * <p>The synthetic prices below are chosen so a cross-market read is detectable rather than
 * coincidentally equal.
 */
class PaperTradingMarketIsolationTest {

	private static final String SPOT_PRICE = "100000";
	private static final String FUTURES_PRICE = "100500";

	private MarketBook marketBook;
	private PositionRepository positionRepository;
	private PaperTradingExecutionService executionService;
	private PaperTradingQueryService queryService;
	private PaperTradingEngineService engine;

	@BeforeEach
	void setUp() {
		marketBook = new MarketBook();
		positionRepository = mock(PositionRepository.class);
		PaperTradingAccountService accountService = mock(PaperTradingAccountService.class);
		executionService = mock(PaperTradingExecutionService.class);
		PaperTradingProperties props =
				new PaperTradingProperties(null, null, new BigDecimal("100"), 10, null, null);
		engine = new PaperTradingEngineService(
				marketBook, positionRepository, accountService, executionService, props);
		queryService = new PaperTradingQueryService(
				mock(PortfolioRepository.class),
				positionRepository,
				accountService,
				new PaperTradingPnLService(props),
				marketBook);
	}

	@Test
	@DisplayName("A spot position is priced from spot even when a futures quote exists for the symbol")
	void spotPositionUsesSpotPrice() {
		marketBook.spotTickers().upsert(ticker("BTCUSDT", SPOT_PRICE));
		marketBook.futuresTickers().upsert(ticker("BTCUSDT", FUTURES_PRICE));

		Position spot = position(TradingMode.SPOT);

		assertThat(queryService.currentPrice(spot)).isEqualByComparingTo(SPOT_PRICE);
	}

	@Test
	@DisplayName("A futures position is priced from futures even when a spot quote exists for the symbol")
	void futuresPositionUsesFuturesPrice() {
		marketBook.spotTickers().upsert(ticker("BTCUSDT", SPOT_PRICE));
		marketBook.futuresTickers().upsert(ticker("BTCUSDT", FUTURES_PRICE));

		Position futures = position(TradingMode.FUTURES);

		assertThat(queryService.currentPrice(futures)).isEqualByComparingTo(FUTURES_PRICE);
	}

	@Test
	@DisplayName("The two positions on one symbol are priced from two different markets")
	void sameSymbolPositionsPriceIndependently() {
		marketBook.spotTickers().upsert(ticker("BTCUSDT", SPOT_PRICE));
		marketBook.futuresTickers().upsert(ticker("BTCUSDT", FUTURES_PRICE));

		Position spot = position(TradingMode.SPOT);
		Position futures = position(TradingMode.FUTURES);

		assertThat(queryService.currentPrice(spot))
				.isNotEqualByComparingTo(queryService.currentPrice(futures));
	}

	@Test
	@DisplayName("A futures tick closes only the futures position, never the spot one on the same symbol")
	void futuresTickDoesNotCloseSpotPosition() {
		Position spot = position(TradingMode.SPOT);
		Position futures = position(TradingMode.FUTURES);
		when(positionRepository.findByStatusAndPortfolio_AccountType(
				any(PositionStatus.class), any(AccountType.class)))
				.thenReturn(List.of(spot, futures));

		// 90000 crosses both positions' stop loss, so the market decides which one is affected.
		engine.onTickBatch(TradingMode.FUTURES, List.of(ticker("BTCUSDT", "90000")));

		verify(executionService, never()).close(eq(spot.getId()), any(), any());
		verify(executionService).close(eq(futures.getId()), any(), any());
	}

	@Test
	@DisplayName("A spot tick closes only the spot position, never the futures one on the same symbol")
	void spotTickDoesNotCloseFuturesPosition() {
		Position spot = position(TradingMode.SPOT);
		Position futures = position(TradingMode.FUTURES);
		when(positionRepository.findByStatusAndPortfolio_AccountType(
				any(PositionStatus.class), any(AccountType.class)))
				.thenReturn(List.of(spot, futures));

		engine.onTickBatch(TradingMode.SPOT, List.of(ticker("BTCUSDT", "90000")));

		verify(executionService, never()).close(eq(futures.getId()), any(), any());
		verify(executionService).close(eq(spot.getId()), any(), any());
	}

	@Test
	@DisplayName("A tick on the position's own market does reach it")
	void ownMarketTickStillEvaluates() {
		Position futures = position(TradingMode.FUTURES);
		when(positionRepository.findByStatusAndPortfolio_AccountType(
				any(PositionStatus.class), any(AccountType.class)))
				.thenReturn(List.of(futures));

		// Futures position LONG with a stop at 90000, ticked down to 90000 on futures.
		engine.onTickBatch(TradingMode.FUTURES, List.of(ticker("BTCUSDT", "90000")));

		verify(executionService).close(any(), any(), any());
	}

	@Test
	@DisplayName("A position predating the market column still resolves to a price")
	void legacyPositionWithoutMarketStillPrices() {
		marketBook.spotTickers().upsert(ticker("BTCUSDT", SPOT_PRICE));
		Position legacy = position(null);

		// Legacy rows carry no market; they default to spot rather than being unpriceable.
		assertThat(legacy.effectiveTradingMode()).isEqualTo(TradingMode.SPOT);
		assertThat(queryService.currentPrice(legacy)).isEqualByComparingTo(SPOT_PRICE);
	}

	private static Position position(TradingMode mode) {
		Position p = new Position();
		p.setId(UUID.randomUUID());
		p.setSymbol("BTCUSDT");
		p.setTradingMode(mode);
		p.setSide(PositionSide.LONG);
		p.setSize(new BigDecimal("1"));
		p.setEntryPrice(new BigDecimal("100000"));
		p.setCurrentPrice(new BigDecimal("100000"));
		p.setStopLoss(new BigDecimal("90000"));
		p.setStatus(PositionStatus.OPEN);
		p.setPortfolio(new Portfolio());
		return p;
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