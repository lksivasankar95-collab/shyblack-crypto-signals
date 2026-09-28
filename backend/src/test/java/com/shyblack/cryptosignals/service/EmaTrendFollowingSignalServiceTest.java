package com.shyblack.cryptosignals.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.dto.strategy.EMATrendFollowingConfig;
import com.shyblack.cryptosignals.entity.Signal;
import com.shyblack.cryptosignals.entity.TradingStrategy;
import com.shyblack.cryptosignals.entity.enums.SignalStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.market.BinanceRestClient;
import com.shyblack.cryptosignals.market.MarketBook;
import com.shyblack.cryptosignals.market.MarketTicker;
import com.shyblack.cryptosignals.repository.SignalRepository;
import com.shyblack.cryptosignals.service.strategy.StrategyResolver;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** Live EMA Trend Following signal service: closed-candle, dedup, cooldown, event-once. */
class EmaTrendFollowingSignalServiceTest {

    private static final long STEP = 900_000L;
    private static final long START = 1_700_000_000_000L;

    private BinanceRestClient restClient;
    private MarketBook marketBook;
    private SignalRepository signalRepository;
    private StrategyResolver strategyResolver;
    private ApplicationEventPublisher eventPublisher;
    private EmaTrendFollowingSignalService service;
    private TradingStrategy strategy;

    @BeforeEach
    void setUp() {
        restClient = mock(BinanceRestClient.class);
        marketBook = new MarketBook();
        signalRepository = mock(SignalRepository.class);
        strategyResolver = mock(StrategyResolver.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        marketBook.spotTickers().upsert(new MarketTicker("TESTUSDT", "Test",
                bd(101), bd(0), bd(1.2), bd(10_000_000), bd(102), bd(99), Instant.now()));

        strategy = new TradingStrategy();
        strategy.setName("EMA Trend Following");
        strategy.setTradingMode(TradingMode.SPOT);
        strategy.setVersion(1);
        lenient().when(strategyResolver.parseEmaTrendFollowingConfig(any())).thenReturn(easyConfig());

        service = new EmaTrendFollowingSignalService(restClient, marketBook, signalRepository,
                strategyResolver, eventPublisher);
    }

    private void stubKlines(List<KlineResponse> htf, List<KlineResponse> entry) {
        when(restClient.klines(eq("TESTUSDT"), eq("1H"), eq(300))).thenReturn(htf);
        when(restClient.klines(eq("TESTUSDT"), eq("15M"), eq(200))).thenReturn(entry);
    }

    @Test
    void actionableSetup_persistsOnceAndPublishesEventOnce() {
        stubKlines(htfSeries(), entrySeries(false));
        when(signalRepository.existsBySymbolAndTradingModeAndSetupId(anyString(), any(), anyString()))
                .thenReturn(false);
        when(signalRepository.findBySymbolAndTradingModeAndStatusIn(anyString(), any(), anyList()))
                .thenReturn(List.of());
        when(signalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int generated = service.runCycle(strategy);

        assertThat(generated).isEqualTo(1);
        verify(signalRepository, times(1)).save(any(Signal.class));
        verify(eventPublisher, times(1)).publishEvent(any(SignalGeneratedEvent.class));
    }

    @Test
    void formingFinalCandle_isRemoved_andNoSignalIsProduced() {
        stubKlines(htfSeries(), entrySeries(true));
        when(signalRepository.findBySymbolAndTradingModeAndStatusIn(anyString(), any(), anyList()))
                .thenReturn(List.of());

        service.runCycle(strategy);

        verify(signalRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void duplicateSetupId_preventsPersistence() {
        stubKlines(htfSeries(), entrySeries(false));
        when(signalRepository.findBySymbolAndTradingModeAndStatusIn(anyString(), any(), anyList()))
                .thenReturn(List.of());
        when(signalRepository.existsBySymbolAndTradingModeAndSetupId(anyString(), any(), anyString()))
                .thenReturn(true);

        service.runCycle(strategy);

        verify(signalRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void cooldown_suppressesSignalWithinWindow() {
        Signal recent = new Signal();
        recent.setSymbol("TESTUSDT");
        recent.setStatus(SignalStatus.ACTIVE);
        recent.setCreatedAt(Instant.now());
        when(signalRepository.findBySymbolAndTradingModeAndStatusIn(anyString(), any(), anyList()))
                .thenReturn(List.of(recent));

        service.runCycle(strategy);

        verify(signalRepository, never()).save(any());
        verify(restClient, never()).klines(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void persistedSignalModeAndStrategy_areEmaTrendFollowing() {
        stubKlines(htfSeries(), entrySeries(false));
        when(signalRepository.existsBySymbolAndTradingModeAndSetupId(anyString(), any(), anyString()))
                .thenReturn(false);
        when(signalRepository.findBySymbolAndTradingModeAndStatusIn(anyString(), any(), anyList()))
                .thenReturn(List.of());
        when(signalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.runCycle(strategy);

        org.mockito.ArgumentCaptor<Signal> cap = org.mockito.ArgumentCaptor.forClass(Signal.class);
        verify(signalRepository).save(cap.capture());
        assertThat(cap.getValue().getTradingMode()).isEqualTo(TradingMode.SPOT);
        assertThat(cap.getValue().getStrategy()).isEqualTo("EMA Trend Following");
        assertThat(cap.getValue().getSetupId()).startsWith("ETF:");
    }

    // ── Fixtures ────────────────────────────────────────────────────────────

    private static EMATrendFollowingConfig easyConfig() {
        EMATrendFollowingConfig c = EMATrendFollowingConfig.defaults();
        c.setHtfFastEma(5);
        c.setHtfSlowEma(15);
        c.setTrendSlopeLookback(2);
        c.setEntryFastEma(5);
        c.setEntrySlowEma(10);
        c.setMinimumEmaSeparationPct(0.01);
        c.setRsiPeriod(5);
        c.setRsiFilterEnabled(false);
        c.setVolumeFilterEnabled(false);
        c.setAtrFilterEnabled(false);
        c.setVolumePeriod(5);
        c.setAtrPeriod(5);
        c.setMinimumScore(0);
        c.setCooldownCandles(4);
        c.setMinRR(0.5);
        c.setSlAtrBuffer(1.0);
        return c;
    }

    private static List<KlineResponse> htfSeries() {
        double[] closes = new double[40];
        for (int i = 0; i < closes.length; i++) closes[i] = 90 + i * 1.0;
        return series(closes, 0.3, false);
    }

    private static List<KlineResponse> entrySeries(boolean forming) {
        double[] closes = new double[41];
        for (int i = 0; i < 40; i++) closes[i] = 100;
        closes[40] = 101;
        return series(closes, 0.05, forming);
    }

    private static List<KlineResponse> series(double[] closes, double spread, boolean forming) {
        List<KlineResponse> out = new ArrayList<>();
        long now = Instant.now().toEpochMilli();
        for (int i = 0; i < closes.length; i++) {
            double open = i == 0 ? closes[0] : closes[i - 1];
            double high = Math.max(open, closes[i]) + spread;
            double low = Math.min(open, closes[i]) - spread;
            long t = START + i * STEP;
            long closeTime = t + STEP - 1;
            if (forming && i == closes.length - 1) closeTime = now + 3_600_000L;
            out.add(new KlineResponse(t, bd(open), bd(high), bd(low), bd(closes[i]), bd(100), closeTime));
        }
        return out;
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }
}
