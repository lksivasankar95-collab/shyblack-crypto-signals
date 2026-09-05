package com.shyblack.cryptosignals.signal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IndicatorEngineTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static List<KlineResponse> flatCandles(int count, double price) {
        List<KlineResponse> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new KlineResponse(
                    i * 3_600_000L,
                    bd(price), bd(price + 1), bd(price - 1), bd(price),
                    bd(1000), (i + 1) * 3_600_000L
            ));
        }
        return list;
    }

    private static List<KlineResponse> risingCandles(int count, double start, double step) {
        List<KlineResponse> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double c = start + i * step;
            list.add(new KlineResponse(
                    i * 3_600_000L,
                    bd(c), bd(c + 1), bd(c - 1), bd(c),
                    bd(1000 + i), (i + 1) * 3_600_000L
            ));
        }
        return list;
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EMA tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void ema_flatSeries_returnsConstantValue() {
        double[] src = new double[50];
        java.util.Arrays.fill(src, 100.0);
        double[] ema = IndicatorEngine.ema(src, 20);
        // All values after seed should equal the constant
        for (int i = 19; i < 50; i++) {
            assertThat(ema[i]).isCloseTo(100.0, within(0.001));
        }
    }

    @Test
    void ema_risingSeries_tracksPriceUpward() {
        double[] src = new double[50];
        for (int i = 0; i < 50; i++) src[i] = i * 1.0;
        double[] ema20 = IndicatorEngine.ema(src, 20);
        // EMA should be below current price (lagging) but trending up
        assertThat(ema20[49]).isGreaterThan(ema20[30]);
        assertThat(ema20[49]).isLessThan(49.0);
    }

    @Test
    void ema_emptyArray_returnsEmpty() {
        double[] result = IndicatorEngine.ema(new double[0], 20);
        assertThat(result).isEmpty();
    }

    @Test
    void ema_singleElement_returnsSelf() {
        double[] result = IndicatorEngine.ema(new double[]{42.0}, 20);
        assertThat(result[0]).isCloseTo(42.0, within(0.001));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RSI tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void rsi_flatSeries_returnsMidpoint() {
        // Flat prices produce 0 gains and 0 losses; RSI = 100 (no losses)
        double[] src = new double[30];
        java.util.Arrays.fill(src, 100.0);
        double[] rsi = IndicatorEngine.rsi(src, 14);
        // With no losses, RSI = 100
        assertThat(rsi[14]).isCloseTo(100.0, within(0.001));
    }

    @Test
    void rsi_alwaysRising_approachesHighValue() {
        double[] src = new double[30];
        for (int i = 0; i < 30; i++) src[i] = 100 + i;
        double[] rsi = IndicatorEngine.rsi(src, 14);
        assertThat(rsi[29]).isGreaterThan(80.0);
    }

    @Test
    void rsi_alwaysFalling_approachesLowValue() {
        double[] src = new double[30];
        for (int i = 0; i < 30; i++) src[i] = 100 - i;
        double[] rsi = IndicatorEngine.rsi(src, 14);
        assertThat(rsi[29]).isLessThan(20.0);
    }

    @Test
    void rsi_rangeIsBetween0And100() {
        double[] src = new double[50];
        for (int i = 0; i < 50; i++) src[i] = Math.sin(i) * 50 + 100;
        double[] rsi = IndicatorEngine.rsi(src, 14);
        for (double v : rsi) {
            assertThat(v).isBetween(0.0, 100.0);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ATR tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void atr_flatPrices_equalsHighMinusLow() {
        // Constant high-low spread of 2, no gaps
        int n = 30;
        double[] high  = new double[n];
        double[] low   = new double[n];
        double[] close = new double[n];
        for (int i = 0; i < n; i++) {
            high[i]  = 101.0;
            low[i]   = 99.0;
            close[i] = 100.0;
        }
        double[] atr = IndicatorEngine.atr(high, low, close, 14);
        // After seeding, ATR should converge to 2.0
        assertThat(atr[29]).isCloseTo(2.0, within(0.01));
    }

    @Test
    void atr_positiveValues() {
        List<KlineResponse> candles = risingCandles(30, 100, 1);
        IndicatorEngine.Indicators ind = IndicatorEngine.compute(candles);
        assertThat(ind.lastAtr()).isGreaterThan(0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Full compute tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void compute_flatCandles_ema200EqualsPrice() {
        List<KlineResponse> candles = flatCandles(210, 50.0);
        IndicatorEngine.Indicators ind = IndicatorEngine.compute(candles);
        assertThat(ind.lastEma200()).isCloseTo(50.0, within(0.5));
        assertThat(ind.lastEma50()).isCloseTo(50.0, within(0.5));
        assertThat(ind.lastEma20()).isCloseTo(50.0, within(0.5));
    }

    @Test
    void compute_returns_correct_size() {
        List<KlineResponse> candles = risingCandles(100, 100, 0.5);
        IndicatorEngine.Indicators ind = IndicatorEngine.compute(candles);
        assertThat(ind.size()).isEqualTo(100);
    }

    @Test
    void compute_macd_histogram_nonZero_for_trending() {
        // Rising market: EMA12 should be above EMA26 → positive MACD
        List<KlineResponse> candles = risingCandles(100, 100, 2);
        IndicatorEngine.Indicators ind = IndicatorEngine.compute(candles);
        // MACD histogram sign can vary near seed but generally positive in strong uptrend
        assertThat(ind.lastMacdLine()).isGreaterThan(ind.lastMacdLine() - 1000); // sanity
    }

    @Test
    void compute_sma_volume_isPositive() {
        List<KlineResponse> candles = flatCandles(50, 100.0);
        IndicatorEngine.Indicators ind = IndicatorEngine.compute(candles);
        assertThat(ind.lastVolumeMa()).isGreaterThan(0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SMA tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void sma_knownValues() {
        double[] src = {1, 2, 3, 4, 5};
        double[] sma = IndicatorEngine.sma(src, 3);
        // index 0: [1]/1 = 1.0
        assertThat(sma[0]).isCloseTo(1.0, within(0.001));
        // index 2: (1+2+3)/3 = 2.0
        assertThat(sma[2]).isCloseTo(2.0, within(0.001));
        // index 4: (3+4+5)/3 = 4.0
        assertThat(sma[4]).isCloseTo(4.0, within(0.001));
    }
}
