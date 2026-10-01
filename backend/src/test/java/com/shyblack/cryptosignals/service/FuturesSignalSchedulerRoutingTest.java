package com.shyblack.cryptosignals.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.entity.TradingStrategy;
import com.shyblack.cryptosignals.entity.enums.MarketRegime;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.market.MarketBook;
import com.shyblack.cryptosignals.market.MarketTickerStore;
import com.shyblack.cryptosignals.repository.SignalRepository;
import com.shyblack.cryptosignals.service.strategy.StrategyResolver;
import com.shyblack.cryptosignals.signal.FuturesSignalEngine;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * The Futures scheduler must route an NFM_FUTURES strategy to the NFM service
 * and must not run the legacy momentum engine for it.
 */
@ExtendWith(MockitoExtension.class)
class FuturesSignalSchedulerRoutingTest {

	@Mock FuturesSignalEngine engine;
	@Mock SignalRepository signalRepository;
	@Mock MarketBook marketBook;
	@Mock SignalNotificationService notificationService;
	@Mock ApplicationEventPublisher eventPublisher;
	@Mock StrategyResolver strategyResolver;
	@Mock NfmFuturesSignalService nfmFuturesSignalService;

	private FuturesSignalScheduler scheduler;

	@BeforeEach
	void setUp() {
		scheduler = new FuturesSignalScheduler(engine, signalRepository, marketBook,
				notificationService, eventPublisher, strategyResolver, nfmFuturesSignalService);
	}

	@Test
	void nfmStrategy_routesToNfmService_andSkipsLegacyPath() {
		TradingStrategy strategy = new TradingStrategy();
		strategy.setName("News Flow Momentum Futures");
		strategy.setTradingMode(TradingMode.FUTURES);
		strategy.setEngineKey("NFM_FUTURES");
		when(strategyResolver.resolveActive(TradingMode.FUTURES)).thenReturn(Optional.of(strategy));

		scheduler.runSignalCycle();

		verify(nfmFuturesSignalService).runCycle(strategy);
		verifyNoInteractions(engine, marketBook, signalRepository);
	}

	@Test
	void legacyFuturesStrategy_usesLegacyPath_andNeverRoutes() {
		TradingStrategy strategy = new TradingStrategy();
		strategy.setName("EMA + RSI Futures");
		strategy.setTradingMode(TradingMode.FUTURES); // engineKey null
		when(strategyResolver.resolveActive(TradingMode.FUTURES)).thenReturn(Optional.of(strategy));
		when(engine.detectMarketRegime()).thenReturn(MarketRegime.BULLISH);
		when(marketBook.futuresTickers()).thenReturn(new MarketTickerStore());

		scheduler.runSignalCycle();

		verify(nfmFuturesSignalService, never()).runCycle(any());
		verify(engine).detectMarketRegime();
	}
}
