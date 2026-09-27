package com.shyblack.cryptosignals.service.backtest.historical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.exception.MarketUpstreamException;
import com.shyblack.cryptosignals.market.BinanceRestClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pagination contract for {@link BinanceHistoricalDataProvider}. The upstream
 * REST client is mocked, so no network is used.
 */
class BinanceHistoricalDataProviderTest {

    private static final String SYM = "BTCUSDT";
    private static final String TF = "15m";
    private static final long STEP = 900_000L;
    private static final long T0 = 1_699_999_200_000L; // 15m-aligned

    private BinanceRestClient client;
    private BinanceHistoricalDataProvider provider;

    @BeforeEach
    void setUp() {
        client = mock(BinanceRestClient.class);
        provider = new BinanceHistoricalDataProvider(client);
    }

    /** Well-behaved upstream: returns up to `limit` candles within [from,end]. */
    private void stubBinance() {
        when(client.klines(anyString(), anyString(), any(), any(), anyInt()))
                .thenAnswer(inv -> {
                    long from = inv.getArgument(2);
                    long end = inv.getArgument(3);
                    int limit = inv.getArgument(4);
                    List<KlineResponse> page = new ArrayList<>();
                    long t = ((from + STEP - 1) / STEP) * STEP; // ceil to the interval grid
                    while (t <= end && page.size() < limit) {
                        page.add(k(t));
                        t += STEP;
                    }
                    return page;
                });
    }

    private List<HistoricalCandle> load(long fromMs, long toMs) {
        return provider.load(SYM, TF, Instant.ofEpochMilli(fromMs), Instant.ofEpochMilli(toMs));
    }

    // ── Happy paths ─────────────────────────────────────────────────────────

    @Test
    void singlePage_returnsAllCandles() {
        stubBinance();
        List<HistoricalCandle> out = load(T0, T0 + 100 * STEP);
        assertThat(out).hasSize(100);
        verify(client, times(1)).klines(eq(SYM), eq(TF), eq(T0), any(), eq(1000));
    }

    @Test
    void exactly1000Candles_isOnePage() {
        stubBinance();
        List<HistoricalCandle> out = load(T0, T0 + 1000 * STEP);
        assertThat(out).hasSize(1000);
        verify(client, times(1)).klines(anyString(), anyString(), any(), any(), anyInt());
    }

    @Test
    void incompleteFinalPage_1001Candles() {
        stubBinance();
        List<HistoricalCandle> out = load(T0, T0 + 1001 * STEP);
        assertThat(out).hasSize(1001);
        verify(client, times(2)).klines(anyString(), anyString(), any(), any(), anyInt());
    }

    @Test
    void multiplePages_2500Candles() {
        stubBinance();
        List<HistoricalCandle> out = load(T0, T0 + 2500 * STEP);
        assertThat(out).hasSize(2500);
        verify(client, times(3)).klines(anyString(), anyString(), any(), any(), anyInt());
    }

    // ── Ordering / dedup / filtering ────────────────────────────────────────

    @Test
    void boundaryOverlap_isDeduplicated() {
        // page1 = T0..T0+999; page2 repeats the boundary candle then continues.
        when(client.klines(anyString(), anyString(), any(), any(), anyInt()))
                .thenAnswer(inv -> {
                    long from = inv.getArgument(2);
                    if (from <= T0) return candles(T0, 1000);
                    return candles(T0 + 999 * STEP, 600); // repeats index 999
                });
        List<HistoricalCandle> out = load(T0, T0 + 2000 * STEP);

        assertThat(out).hasSize(1599);
        assertThat(out.stream().map(HistoricalCandle::openTime).distinct()).hasSize(1599);
        assertAscendingAndInRange(out, T0, T0 + 2000 * STEP);
    }

    @Test
    void duplicateCandlesWithinPage_areRemoved() {
        when(client.klines(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(List.of(k(T0), k(T0), k(T0 + STEP), k(T0 + STEP), k(T0 + 2 * STEP)));
        List<HistoricalCandle> out = load(T0, T0 + 10 * STEP);
        assertThat(out).hasSize(3);
    }

    @Test
    void unsortedUpstream_isReturnedChronologically() {
        when(client.klines(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(List.of(k(T0 + 4 * STEP), k(T0 + 3 * STEP), k(T0 + 2 * STEP),
                        k(T0 + STEP), k(T0)));
        List<HistoricalCandle> out = load(T0, T0 + 10 * STEP);
        assertThat(out).hasSize(5);
        assertAscendingAndInRange(out, T0, T0 + 10 * STEP);
    }

    @Test
    void candlesOutsideRequestedRange_areExcluded() {
        // upstream ignores the window and returns T0..T0+19
        when(client.klines(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(candles(T0, 20));
        List<HistoricalCandle> out = load(T0 + 5 * STEP, T0 + 10 * STEP);
        assertThat(out).hasSize(5);
        assertAscendingAndInRange(out, T0 + 5 * STEP, T0 + 10 * STEP);
    }

    // ── Failure / termination ───────────────────────────────────────────────

    @Test
    void upstreamFailure_propagates_andNoPartialResultIsReturned() {
        when(client.klines(anyString(), anyString(), any(), any(), anyInt()))
                .thenAnswer(inv -> {
                    long from = inv.getArgument(2);
                    if (from <= T0) return candles(T0, 1000);
                    throw new MarketUpstreamException("upstream boom");
                });
        assertThatThrownBy(() -> load(T0, T0 + 2000 * STEP))
                .isInstanceOf(MarketUpstreamException.class);
    }

    @Test
    void nonAdvancingPage_abortsInsteadOfLoopingForever() {
        when(client.klines(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(candles(T0, 1000)); // same page every call
        assertThatThrownBy(() -> load(T0, T0 + 5000 * STEP))
                .isInstanceOf(MarketUpstreamException.class);
    }

    @Test
    void emptyFirstPage_returnsEmpty() {
        when(client.klines(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(List.of());
        assertThat(load(T0, T0 + 10 * STEP)).isEmpty();
    }

    @Test
    void invalidRange_returnsEmptyWithoutCallingUpstream() {
        assertThat(load(T0, T0)).isEmpty();
        assertThat(load(T0 + STEP, T0)).isEmpty();
        verifyNoInteractions(client);
    }

    // ── Interval parsing ────────────────────────────────────────────────────

    @Test
    void intervalMillis_parsesSupportedAndRejectsUnknown() {
        assertThat(BinanceHistoricalDataProvider.intervalMillis("15m")).isEqualTo(15 * 60_000L);
        assertThat(BinanceHistoricalDataProvider.intervalMillis("1h")).isEqualTo(3_600_000L);
        assertThat(BinanceHistoricalDataProvider.intervalMillis("1d")).isEqualTo(86_400_000L);
        assertThat(BinanceHistoricalDataProvider.intervalMillis("1w")).isEqualTo(604_800_000L);
        assertThatThrownBy(() -> BinanceHistoricalDataProvider.intervalMillis("1x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static void assertAscendingAndInRange(List<HistoricalCandle> out, long start, long end) {
        long prev = Long.MIN_VALUE;
        for (HistoricalCandle c : out) {
            long t = c.openTime().toEpochMilli();
            assertThat(t).isGreaterThanOrEqualTo(start).isLessThan(end);
            assertThat(t).isGreaterThan(prev);
            prev = t;
        }
    }

    private static List<KlineResponse> candles(long from, int count) {
        List<KlineResponse> out = new ArrayList<>();
        for (int i = 0; i < count; i++) out.add(k(from + (long) i * STEP));
        return out;
    }

    private static KlineResponse k(long openTime) {
        BigDecimal p = BigDecimal.valueOf(100);
        return new KlineResponse(openTime, p, p.add(BigDecimal.ONE), p.subtract(BigDecimal.ONE),
                p, BigDecimal.ONE, openTime + STEP - 1);
    }
}
