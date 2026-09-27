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
import com.shyblack.cryptosignals.dto.strategy.TrendPullbackConfig;
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

/**
 * DEFECT 5 — live Trend Pullback signal service: closed-candle enforcement,
 * duplicate-setup suppression, cooldown, and exactly-once event publication.
 */
class TrendPullbackSignalServiceTest {

    private static final long STEP = 900_000L;
    private static final long START = 1_700_000_000_000L;

    private BinanceRestClient restClient;
    private MarketBook marketBook;
    private SignalRepository signalRepository;
    private StrategyResolver strategyResolver;
    private ApplicationEventPublisher eventPublisher;
    private TrendPullbackSignalService service;
    private TradingStrategy strategy;

    @BeforeEach
    void setUp() {
        restClient = mock(BinanceRestClient.class);
        marketBook = new MarketBook();
        signalRepository = mock(SignalRepository.class);
        strategyResolver = mock(StrategyResolver.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        marketBook.spotTickers().upsert(new MarketTicker("TESTUSDT", "Test",
                bd(106.3), bd(0), bd(1.2), bd(10_000_000), bd(108), bd(100), Instant.now()));

        strategy = new TradingStrategy();
        strategy.setName("Trend Pullback");
        strategy.setTradingMode(TradingMode.SPOT);
        strategy.setVersion(1);
        lenient().when(strategyResolver.parseTrendPullbackConfig(any())).thenReturn(easyConfig());

        service = new TrendPullbackSignalService(restClient, marketBook, signalRepository,
                strategyResolver, eventPublisher);
    }

    private void stubKlines(List<KlineResponse> htf, List<KlineResponse> entry) {
        when(restClient.klines(eq("TESTUSDT"), eq("1H"), eq(300))).thenReturn(htf);
        when(restClient.klines(eq("TESTUSDT"), eq("15M"), eq(200))).thenReturn(entry);
    }

    @Test
    void actionableClosedSetup_persistsOnceAndPublishesEventOnce() {
        stubKlines(htfSeries(false), entrySeries(false));
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
        // The breakout candle is still forming (closeTime in the future) → dropped.
        stubKlines(htfSeries(false), entrySeries(true));
        when(signalRepository.findBySymbolAndTradingModeAndStatusIn(anyString(), any(), anyList()))
                .thenReturn(List.of());

        service.runCycle(strategy);

        verify(signalRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void duplicateSetupId_preventsPersistence() {
        stubKlines(htfSeries(false), entrySeries(false));
        when(signalRepository.findBySymbolAndTradingModeAndStatusIn(anyString(), any(), anyList()))
                .thenReturn(List.of());
        when(signalRepository.existsBySymbolAndTradingModeAndSetupId(anyString(), any(), anyString()))
                .thenReturn(true);

        service.runCycle(strategy);

        verify(signalRepository, never()).save(any());
    }

    @Test
    void cooldown_suppressesSignalWithinWindow() {
        // A recent ACTIVE signal for the symbol → inside the cooldown window.
        Signal recent = new Signal();
        recent.setSymbol("TESTUSDT");
        recent.setStatus(SignalStatus.ACTIVE);
        recent.setCreatedAt(Instant.now());
        when(signalRepository.findBySymbolAndTradingModeAndStatusIn(anyString(), any(), anyList()))
                .thenReturn(List.of(recent));

        service.runCycle(strategy);

        verify(signalRepository, never()).save(any());
        // Cooldown short-circuits before any kline fetch.
        verify(restClient, never()).klines(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void closedCandles_dropsUnclosedAndKeepsClosed() {
        List<KlineResponse> candles = new ArrayList<>(entrySeries(false));
        Instant now = Instant.now();
        // last candle is still forming
        KlineResponse last = candles.get(candles.size() - 1);
        candles.set(candles.size() - 1, new KlineResponse(last.openTime(), last.open(), last.high(),
                last.low(), last.close(), last.volume(), now.plusSeconds(3600).toEpochMilli()));

        List<KlineResponse> closed = TrendPullbackSignalService.closedCandles(candles, now);
        assertThat(closed).hasSize(candles.size() - 1);

        List<KlineResponse> already = TrendPullbackSignalService.closedCandles(
                entrySeries(false), now);
        assertThat(already).hasSize(49);
    }

    // ── Fixtures ────────────────────────────────────────────────────────────

    private static TrendPullbackConfig easyConfig() {
        TrendPullbackConfig c = TrendPullbackConfig.defaults();
        c.setEmaFastHtf(5);
        c.setEmaSlowHtf(15);
        c.setAdxPeriod(5);
        c.setMinAdx(1.0);
        c.setRequirePositiveSlope(false);
        c.setPullbackEma(5);
        c.setEntryEma(10);
        c.setZoneMode("EMA20");
        c.setMaxPullbackDistanceAtr(10.0);
        c.setRsiPeriod(5);
        c.setRsiMin(0);
        c.setRsiMax(100);
        c.setRequireRecovery(false);
        c.setVolumeFilterEnabled(false);
        c.setVolumeSmaPeriod(5);
        c.setAtrPeriod(5);
        c.setSlAtrBuffer(0.2);
        c.setMaxSlAtr(20.0);
        c.setMinRR(0.5);
        c.setMaxSetupCandles(12);
        c.setSwingLookback(2);
        c.setCandleConfirmationEnabled(false);
        c.setMinimumScore(0);
        return c;
    }

    private static List<KlineResponse> htfSeries(boolean forming) {
        double[] closes = new double[40];
        for (int i = 0; i < closes.length; i++) closes[i] = 100 + i * 0.3;
        return series(closes, 0.15, forming);
    }

    private static List<KlineResponse> entrySeries(boolean forming) {
        double[] closes = new double[49];
        for (int i = 0; i <= 39; i++) closes[i] = 100 + i * 0.15;
        for (int i = 40; i <= 47; i++) closes[i] = closes[39] - (i - 39) * 0.2;
        closes[48] = 106.3;
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
