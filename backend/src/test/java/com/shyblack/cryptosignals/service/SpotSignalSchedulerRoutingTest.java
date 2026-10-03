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
import com.shyblack.cryptosignals.signal.SpotSignalEngine;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * DEFECT 5 — the scheduler must route a TREND_PULLBACK strategy to the Trend
 * Pullback service and must not run the legacy Spot engine for it.
 */
@ExtendWith(MockitoExtension.class)
class SpotSignalSchedulerRoutingTest {

    @Mock SpotSignalEngine engine;
    @Mock SignalRepository signalRepository;
    @Mock MarketBook marketBook;
    @Mock SignalNotificationService notificationService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock StrategyResolver strategyResolver;
    @Mock TrendPullbackSignalService trendPullbackSignalService;
    @Mock EmaTrendFollowingSignalService emaTrendFollowingSignalService;

    private SpotSignalScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SpotSignalScheduler(engine, signalRepository, marketBook,
                notificationService, eventPublisher, strategyResolver,
                trendPullbackSignalService, emaTrendFollowingSignalService);
    }

    @Test
    void emaTrendFollowingStrategy_routesToService_andSkipsLegacyPath() {
        TradingStrategy strategy = new TradingStrategy();
        strategy.setName("EMA Trend Following");
        strategy.setTradingMode(TradingMode.SPOT);
        strategy.setEngineKey("EMA_TREND_FOLLOWING");
        when(strategyResolver.resolveActive(TradingMode.SPOT)).thenReturn(Optional.of(strategy));

        scheduler.runSignalCycle();

        verify(emaTrendFollowingSignalService).runCycle(strategy);
        verify(trendPullbackSignalService, never()).runCycle(any());
        verifyNoInteractions(engine, marketBook, signalRepository);
    }

    @Test
    void trendPullbackStrategy_routesToService_andSkipsLegacyPath() {
        TradingStrategy strategy = new TradingStrategy();
        strategy.setName("Trend Pullback");
        strategy.setTradingMode(TradingMode.SPOT);
        strategy.setEngineKey("TREND_PULLBACK");
        when(strategyResolver.resolveActive(TradingMode.SPOT)).thenReturn(Optional.of(strategy));

        scheduler.runSignalCycle();

        verify(trendPullbackSignalService).runCycle(strategy);
        verifyNoInteractions(engine, marketBook, signalRepository);
    }

    @Test
    void legacyStrategy_usesLegacyPath_andNeverRoutes() {
        TradingStrategy strategy = new TradingStrategy();
        strategy.setName("EMA + RSI");
        strategy.setTradingMode(TradingMode.SPOT); // engineKey null
        when(strategyResolver.resolveActive(TradingMode.SPOT)).thenReturn(Optional.of(strategy));
        when(engine.detectMarketRegime()).thenReturn(MarketRegime.BULLISH);
        when(marketBook.spotTickers()).thenReturn(new MarketTickerStore(TradingMode.SPOT));

        scheduler.runSignalCycle();

        verify(trendPullbackSignalService, never()).runCycle(any());
        verify(engine).detectMarketRegime();
    }
}
