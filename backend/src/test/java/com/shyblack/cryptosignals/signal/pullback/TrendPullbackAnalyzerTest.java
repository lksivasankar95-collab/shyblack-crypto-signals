package com.shyblack.cryptosignals.signal.pullback;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.dto.strategy.TrendPullbackConfig;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrendPullbackAnalyzerTest {

    private static final long STEP = 900_000L;
    private static final long START = 1_700_000_000_000L;

    // ── Fixtures ────────────────────────────────────────────────────────────

    /** Small-period config so tests need few candles but exercise all logic. */
    private static TrendPullbackConfig easy() {
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

    private static double[] risingHtf() {
        double[] c = new double[40];
        for (int i = 0; i < c.length; i++) c[i] = 100 + i * 0.3;
        return c;
    }

    /** Uptrend → pullback into EMA → breakout. Baseline for positive tests. */
    private static double[] pullbackEntry() {
        double[] c = new double[49];
        for (int i = 0; i <= 39; i++) c[i] = 100 + i * 0.15;
        for (int i = 40; i <= 47; i++) c[i] = c[39] - (i - 39) * 0.2;
        c[48] = 106.3;
        return c;
    }

    private static double[][] ohlcv(double[] closes, double spread, double volume) {
        int n = closes.length;
        double[] o = new double[n];
        double[] h = new double[n];
        double[] l = new double[n];
        double[] v = new double[n];
        for (int i = 0; i < n; i++) {
            o[i] = i == 0 ? closes[0] : closes[i - 1];
            h[i] = Math.max(o[i], closes[i]) + spread;
            l[i] = Math.min(o[i], closes[i]) - spread;
            v[i] = volume;
        }
        return new double[][]{o, h, l, closes, v};
    }

    private static List<KlineResponse> series(double[] o, double[] h, double[] l,
            double[] c, double[] v) {
        List<KlineResponse> out = new ArrayList<>();
        for (int i = 0; i < c.length; i++) {
            long t = START + i * STEP;
            out.add(new KlineResponse(t, bd(o[i]), bd(h[i]), bd(l[i]), bd(c[i]), bd(v[i]),
                    t + STEP - 1));
        }
        return out;
    }

    private static List<KlineResponse> series(double[] closes, double spread, double volume) {
        double[][] r = ohlcv(closes, spread, volume);
        return series(r[0], r[1], r[2], r[3], r[4]);
    }

    private static List<KlineResponse> htf(double[] closes, double spread) {
        return series(closes, spread, 1000);
    }

    private static TrendPullbackAssessment run(double[] htfCloses, double[] entryCloses,
            double entrySpread, TrendPullbackConfig cfg) {
        return TrendPullbackAnalyzer.analyze("TESTUSDT", htf(htfCloses, 0.15),
                series(entryCloses, entrySpread, 100), cfg);
    }

    // ── Positive path ───────────────────────────────────────────────────────

    @Test
    void bullishTrendPullbackBreakout_isActionable() {
        TrendPullbackAssessment a = run(risingHtf(), pullbackEntry(), 0.05, easy());

        assertThat(a.actionable()).isTrue();
        assertThat(a.state()).isEqualTo(TrendPullbackState.SIGNAL_READY);
        assertThat(a.rejectReason()).isEqualTo(TrendRejectReason.NONE);
        assertThat(a.setupId()).isNotBlank();
        assertThat(a.entry()).isNotNull();
        assertThat(a.stopLoss()).isLessThan(a.entry());
        assertThat(a.tp1()).isGreaterThan(a.entry());
        assertThat(a.tp2()).isGreaterThan(a.tp1());
        assertThat(a.tp3()).isGreaterThan(a.tp2());
        assertThat(a.riskReward()).isGreaterThanOrEqualTo(easy().getMinRR());
        assertThat(a.explanation()).contains("TREND_PULLBACK").contains("Score");
    }

    // ── Trend tests ─────────────────────────────────────────────────────────

    @Test
    void nonBullishHtF_isNoTrend() {
        double[] htf = new double[40];
        for (int i = 0; i < htf.length; i++) htf[i] = 120 - i * 0.3;
        TrendPullbackAssessment a = run(htf, pullbackEntry(), 0.05, easy());
        assertThat(a.actionable()).isFalse();
        assertThat(a.state()).isEqualTo(TrendPullbackState.NO_TREND);
        assertThat(a.rejectReason()).isEqualTo(TrendRejectReason.NO_BULLISH_TREND);
    }

    @Test
    void adxBelowMinimum_isNoTrend() {
        TrendPullbackConfig cfg = easy();
        cfg.setMinAdx(1000);
        TrendPullbackAssessment a = run(risingHtf(), pullbackEntry(), 0.05, cfg);
        assertThat(a.rejectReason()).isEqualTo(TrendRejectReason.ADX_BELOW_MINIMUM);
    }

    @Test
    void negativeSlope_isNoTrend() {
        TrendPullbackConfig cfg = easy();
        cfg.setRequirePositiveSlope(true);
        cfg.setSlopeLookback(1);
        double[] htf = new double[40];
        for (int i = 0; i <= 38; i++) htf[i] = 50 + i * 2; // steep rise to 126
        htf[39] = 118;                                     // final drop
        TrendPullbackAssessment a = run(htf, pullbackEntry(), 0.05, cfg);
        assertThat(a.rejectReason()).isEqualTo(TrendRejectReason.SLOPE_NOT_POSITIVE);
    }

    // ── Pullback tests ──────────────────────────────────────────────────────

    @Test
    void priceFarAboveEma_noPullback() {
        double[] entry = new double[49];
        for (int i = 0; i < entry.length; i++) entry[i] = 100 + i * 0.3;
        TrendPullbackAssessment a = run(risingHtf(), entry, 0.05, easy());
        assertThat(a.state()).isEqualTo(TrendPullbackState.TREND_CONFIRMED);
        assertThat(a.rejectReason()).isEqualTo(TrendRejectReason.NO_PULLBACK);
    }

    @Test
    void deepPullback_exceedsMaxDistance_isInvalidated() {
        TrendPullbackConfig cfg = easy();
        cfg.setMaxPullbackDistanceAtr(0.5);
        double[] entry = new double[49];
        for (int i = 0; i <= 39; i++) entry[i] = 100 + i * 0.15;
        for (int i = 40; i < 49; i++) entry[i] = entry[39] - (i - 39) * 1.2;
        TrendPullbackAssessment a = run(risingHtf(), entry, 0.05, cfg);
        assertThat(a.state()).isEqualTo(TrendPullbackState.INVALIDATED);
        assertThat(a.rejectReason()).isEqualTo(TrendRejectReason.PULLBACK_TOO_DEEP);
    }

    @Test
    void bearishBreakdownCandle_isInvalidated() {
        TrendPullbackConfig cfg = easy();
        cfg.setInvalidationBodyAtr(0.3);
        cfg.setInvalidationVolumeMult(0.5);
        double[] closes = pullbackEntry();
        double[][] r = ohlcv(closes, 0.05, 100);
        // index 47: big bearish candle below the entry EMA on huge volume
        r[0][47] = 104.0;   // open
        r[1][47] = 104.2;   // high
        r[2][47] = 99.9;    // low
        r[3][47] = 100.0;   // close
        r[4][47] = 1000;
        r[3][48] = 100.5;
        List<KlineResponse> entry = series(r[0], r[1], r[2], r[3], r[4]);
        TrendPullbackAssessment a = TrendPullbackAnalyzer.analyze(
                "TESTUSDT", htf(risingHtf(), 0.15), entry, cfg);
        assertThat(a.state()).isEqualTo(TrendPullbackState.INVALIDATED);
        assertThat(a.rejectReason()).isEqualTo(TrendRejectReason.BEARISH_BREAKDOWN_CANDLE);
    }

    // ── RSI tests ───────────────────────────────────────────────────────────

    @Test
    void rsiOutsideConfiguredZone_isRejected() {
        TrendPullbackConfig cfg = easy();
        cfg.setRsiMin(99);
        cfg.setRsiMax(100);
        TrendPullbackAssessment a = run(risingHtf(), pullbackEntry(), 0.05, cfg);
        assertThat(a.rejectReason()).isEqualTo(TrendRejectReason.RSI_OUT_OF_RANGE);
    }

    @Test
    void rsiNotRecovering_isRejected() {
        TrendPullbackConfig cfg = easy();
        cfg.setRequireRecovery(true);
        double[] closes = pullbackEntry();
        closes[48] = closes[47]; // flat candle → RSI stalls
        TrendPullbackAssessment a = run(risingHtf(), closes, 0.05, cfg);
        assertThat(a.rejectReason()).isEqualTo(TrendRejectReason.RSI_NOT_RECOVERING);
    }

    @Test
    void rsiRecovering_isAccepted() {
        TrendPullbackConfig cfg = easy();
        cfg.setRequireRecovery(true);
        TrendPullbackAssessment a = run(risingHtf(), pullbackEntry(), 0.05, cfg);
        assertThat(a.actionable()).isTrue();
    }

    // ── Volume tests ────────────────────────────────────────────────────────

    @Test
    void volumeBelowThreshold_isRejected() {
        TrendPullbackConfig cfg = easy();
        cfg.setVolumeFilterEnabled(true);
        cfg.setMinVolumeMultiplier(1.2);
        double[] closes = pullbackEntry();
        double[][] r = ohlcv(closes, 0.05, 100);
        r[4][48] = 50; // breakout candle has weak volume
        List<KlineResponse> entry = series(r[0], r[1], r[2], r[3], r[4]);
        TrendPullbackAssessment a = TrendPullbackAnalyzer.analyze(
                "TESTUSDT", htf(risingHtf(), 0.15), entry, cfg);
        assertThat(a.rejectReason()).isEqualTo(TrendRejectReason.VOLUME_TOO_LOW);
    }

    @Test
    void volumeFilterDisabled_ignoresVolume() {
        TrendPullbackConfig cfg = easy();
        cfg.setVolumeFilterEnabled(false);
        double[] closes = pullbackEntry();
        double[][] r = ohlcv(closes, 0.05, 100);
        r[4][48] = 1; // extremely weak volume
        List<KlineResponse> entry = series(r[0], r[1], r[2], r[3], r[4]);
        TrendPullbackAssessment a = TrendPullbackAnalyzer.analyze(
                "TESTUSDT", htf(risingHtf(), 0.15), entry, cfg);
        assertThat(a.actionable()).isTrue();
    }

    // ── Confirmation tests ──────────────────────────────────────────────────

    @Test
    void noStructureBreakout_waits() {
        double[] closes = pullbackEntry();
        closes[48] = 105.0; // stays below the pre-pullback swing high
        TrendPullbackAssessment a = run(risingHtf(), closes, 0.05, easy());
        assertThat(a.state()).isEqualTo(TrendPullbackState.WAITING_CONFIRMATION);
        assertThat(a.rejectReason()).isEqualTo(TrendRejectReason.NO_STRUCTURE_BREAKOUT);
    }

    @Test
    void candleConfirmationRequired_butPrevHighNotReclaimed_isRejected() {
        TrendPullbackConfig cfg = easy();
        cfg.setCandleConfirmationEnabled(true);
        double[] closes = pullbackEntry();
        double[][] r = ohlcv(closes, 0.05, 100);
        r[1][47] = 108.0; // tall wick on the candle before the breakout
        List<KlineResponse> entry = series(r[0], r[1], r[2], r[3], r[4]);
        TrendPullbackAssessment a = TrendPullbackAnalyzer.analyze(
                "TESTUSDT", htf(risingHtf(), 0.15), entry, cfg);
        assertThat(a.rejectReason()).isEqualTo(TrendRejectReason.CANDLE_NOT_CONFIRMED);
    }

    // ── Risk tests ──────────────────────────────────────────────────────────

    @Test
    void rrBelowMinimum_isRejected() {
        TrendPullbackConfig cfg = easy();
        cfg.setMinRR(100);
        TrendPullbackAssessment a = run(risingHtf(), pullbackEntry(), 0.05, cfg);
        assertThat(a.rejectReason()).isEqualTo(TrendRejectReason.INSUFFICIENT_RR);
    }

    @Test
    void stopTooWide_isRejected() {
        TrendPullbackConfig cfg = easy();
        cfg.setMaxSlAtr(0.01);
        TrendPullbackAssessment a = run(risingHtf(), pullbackEntry(), 0.05, cfg);
        assertThat(a.rejectReason()).isEqualTo(TrendRejectReason.STOP_TOO_WIDE);
    }

    @Test
    void scoreBelowMinimum_isRejected() {
        TrendPullbackConfig cfg = easy();
        cfg.setMinimumScore(101); // unreachable
        TrendPullbackAssessment a = run(risingHtf(), pullbackEntry(), 0.05, cfg);
        assertThat(a.rejectReason()).isEqualTo(TrendRejectReason.SCORE_BELOW_MINIMUM);
    }

    // ── Lifecycle tests ─────────────────────────────────────────────────────

    @Test
    void setupExpires_afterMaxSetupCandles() {
        TrendPullbackConfig cfg = easy();
        cfg.setMaxSetupCandles(1);
        double[] closes = new double[49];
        for (int i = 0; i <= 39; i++) closes[i] = 100 + i * 0.15;
        double peak = closes[39];
        for (int i = 40; i <= 44; i++) closes[i] = peak - (i - 39) * 0.57; // pullback to ~103
        closes[45] = 106.0;
        closes[46] = 106.5;
        closes[47] = 107.0;
        closes[48] = 107.5; // recovered well above the EMA → pullback ended 3 candles ago
        TrendPullbackAssessment a = run(risingHtf(), closes, 0.05, cfg);
        assertThat(a.state()).isEqualTo(TrendPullbackState.EXPIRED);
        assertThat(a.rejectReason()).isEqualTo(TrendRejectReason.SETUP_EXPIRED);
    }

    @Test
    void trendInvalidation_resetsSetup() {
        TrendPullbackConfig cfg = easy();
        double[] htf = new double[40];
        for (int i = 0; i < htf.length; i++) htf[i] = 120 - i * 0.3;
        TrendPullbackAssessment a = run(htf, pullbackEntry(), 0.05, cfg);
        assertThat(a.state()).isEqualTo(TrendPullbackState.NO_TREND);
    }

    @Test
    void setupId_isDeterministicAndDistinctPerPullback() {
        TrendPullbackConfig cfg = easy();
        TrendPullbackAssessment a1 = run(risingHtf(), pullbackEntry(), 0.05, cfg);
        TrendPullbackAssessment a2 = run(risingHtf(), pullbackEntry(), 0.05, cfg);
        assertThat(a1).isEqualTo(a2);
        assertThat(a1.setupId()).isEqualTo(a2.setupId());

        // Shift the pullback by one candle → different setup identity.
        double[] shifted = new double[49];
        for (int i = 0; i <= 38; i++) shifted[i] = 100 + i * 0.15;
        for (int i = 39; i <= 46; i++) shifted[i] = shifted[38] - (i - 38) * 0.2;
        shifted[47] = 105.9;
        shifted[48] = 106.3;
        TrendPullbackAssessment a3 = run(risingHtf(), shifted, 0.05, cfg);
        assertThat(a3.setupId()).isNotEqualTo(a1.setupId());
    }

    @Test
    void insufficientData_isReported() {
        TrendPullbackConfig cfg = easy();
        double[] tiny = {100, 101, 102};
        TrendPullbackAssessment a = run(risingHtf(), tiny, 0.05, cfg);
        assertThat(a.state()).isEqualTo(TrendPullbackState.INSUFFICIENT_DATA);
        assertThat(a.rejectReason()).isEqualTo(TrendRejectReason.INSUFFICIENT_DATA);
    }

    // ── Score contract (Defect 2) ───────────────────────────────────────────

    @Test
    void score_isNormalizedTo100_forWeightSums100_70_150() {
        int[][] weightSets = {
                {20, 15, 20, 10, 10, 15, 10}, // 100
                {10, 10, 10, 10, 10, 10, 10}, //  70
                {30, 20, 30, 20, 20, 20, 10}, // 150
        };
        for (int[] w : weightSets) {
            TrendPullbackConfig cfg = easy();
            applyWeights(cfg, w);
            TrendPullbackAssessment a = run(risingHtf(), pullbackEntry(), 0.05, cfg);
            assertThat(a.score()).as("score for weights %s", java.util.Arrays.toString(w))
                    .isBetween(0, 100);
            if (a.actionable()) {
                assertThat(a.breakdown().total()).isEqualTo(a.score());
            }
        }
    }

    @Test
    void score_isScaleInvariant_whenAllWeightsAreScaled() {
        TrendPullbackConfig base = easy();
        applyWeights(base, new int[]{20, 15, 20, 10, 10, 15, 10});
        TrendPullbackConfig doubled = easy();
        applyWeights(doubled, new int[]{40, 30, 40, 20, 20, 30, 20});

        TrendPullbackAssessment a = run(risingHtf(), pullbackEntry(), 0.05, base);
        TrendPullbackAssessment b = run(risingHtf(), pullbackEntry(), 0.05, doubled);

        assertThat(a.actionable()).isTrue();
        assertThat(b.actionable()).isTrue();
        assertThat(a.score()).isEqualTo(b.score());
    }

    @Test
    void score_allActiveWeightsZero_failsSafely() {
        TrendPullbackConfig cfg = easy();
        applyWeights(cfg, new int[]{0, 0, 0, 0, 0, 0, 0});
        TrendPullbackAssessment a = run(risingHtf(), pullbackEntry(), 0.05, cfg);
        assertThat(a.actionable()).isFalse();
        assertThat(a.rejectReason()).isEqualTo(TrendRejectReason.SCORE_NOT_CONFIGURED);
        assertThat(a.score()).isNotEqualTo(100);
    }

    // ── Disabled optional criteria (Defect 3) ───────────────────────────────

    @Test
    void volumeDisabled_isExcludedFromNormalization_notGivenFullPoints() {
        TrendPullbackConfig cfg = easy();
        applyWeights(cfg, new int[]{0, 0, 0, 0, 10, 0, 0}); // only volume carries weight
        cfg.setVolumeFilterEnabled(false);
        TrendPullbackAssessment a = run(risingHtf(), pullbackEntry(), 0.05, cfg);
        assertThat(a.rejectReason()).isEqualTo(TrendRejectReason.SCORE_NOT_CONFIGURED);
    }

    @Test
    void recoveryDisabled_isExcludedFromNormalization_notGivenFullPoints() {
        TrendPullbackConfig cfg = easy();
        applyWeights(cfg, new int[]{0, 0, 0, 10, 0, 0, 0}); // only RSI carries weight
        cfg.setRequireRecovery(false);
        TrendPullbackAssessment a = run(risingHtf(), pullbackEntry(), 0.05, cfg);
        assertThat(a.rejectReason()).isEqualTo(TrendRejectReason.SCORE_NOT_CONFIGURED);
    }

    @Test
    void disabledCriteria_contributeZeroComponentPoints() {
        TrendPullbackConfig cfg = easy(); // volume + recovery disabled
        applyWeights(cfg, new int[]{20, 15, 20, 10, 10, 15, 10});
        TrendPullbackAssessment a = run(risingHtf(), pullbackEntry(), 0.05, cfg);
        assertThat(a.actionable()).isTrue();
        assertThat(a.breakdown().volume()).isZero();
        assertThat(a.breakdown().rsi()).isZero();
    }

    @Test
    void volumeEnabledWithStrongVolume_usesOnlyActiveWeight() {
        TrendPullbackConfig cfg = easy();
        applyWeights(cfg, new int[]{0, 0, 0, 0, 10, 0, 0}); // only volume carries weight
        cfg.setVolumeFilterEnabled(true);
        double[] closes = pullbackEntry();
        double[][] r = ohlcv(closes, 0.05, 100);
        r[4][48] = 200; // strong breakout volume
        List<KlineResponse> entry = series(r[0], r[1], r[2], r[3], r[4]);
        TrendPullbackAssessment a = TrendPullbackAnalyzer.analyze(
                "TESTUSDT", htf(risingHtf(), 0.15), entry, cfg);
        assertThat(a.actionable()).isTrue();
        assertThat(a.score()).isBetween(0, 100);
        assertThat(a.score()).isEqualTo(a.breakdown().volume());
    }

    @Test
    void defaultsConfig_smokeDeterministic() {
        TrendPullbackConfig cfg = TrendPullbackConfig.defaults();
        double[] htf = new double[260];
        for (int i = 0; i < htf.length; i++) htf[i] = 50 + i * 0.2;
        double[] entry = new double[900];
        for (int i = 0; i < entry.length; i++) entry[i] = 80 + i * 0.02;
        TrendPullbackAssessment a1 = run(htf, entry, 0.3, cfg);
        TrendPullbackAssessment a2 = run(htf, entry, 0.3, cfg);
        assertThat(a1).isEqualTo(a2);
    }

    private static void applyWeights(TrendPullbackConfig c, int[] w) {
        c.setWeightTrend(w[0]);
        c.setWeightAdx(w[1]);
        c.setWeightPullback(w[2]);
        c.setWeightRsi(w[3]);
        c.setWeightVolume(w[4]);
        c.setWeightStructure(w[5]);
        c.setWeightRiskReward(w[6]);
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }
}
