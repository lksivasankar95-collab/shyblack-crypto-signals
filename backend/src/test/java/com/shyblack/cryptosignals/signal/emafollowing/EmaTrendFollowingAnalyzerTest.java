package com.shyblack.cryptosignals.signal.emafollowing;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.dto.strategy.EMATrendFollowingConfig;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmaTrendFollowingAnalyzerTest {

    private static final long STEP = 900_000L;
    private static final long START = 1_699_999_200_000L;

    private static EMATrendFollowingConfig easy() {
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

    private static List<KlineResponse> risingHtf() {
        double[] closes = new double[30];
        for (int i = 0; i < closes.length; i++) closes[i] = 90 + i * 1.0;
        return series(closes, 0.3, 1000);
    }

    /** 40 flat candles then one up candle -> EMA-fast crosses above EMA-slow at the last candle. */
    private static double[] entryCloses() {
        double[] closes = new double[41];
        for (int i = 0; i < 40; i++) closes[i] = 100;
        closes[40] = 101;
        return closes;
    }

    private static List<KlineResponse> series(double[] closes, double spread, double volume) {
        List<KlineResponse> out = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) {
            double open = i == 0 ? closes[0] : closes[i - 1];
            double high = Math.max(open, closes[i]) + spread;
            double low = Math.min(open, closes[i]) - spread;
            long t = START + i * STEP;
            out.add(new KlineResponse(t, bd(open), bd(high), bd(low), bd(closes[i]), bd(volume), t + STEP - 1));
        }
        return out;
    }

    private static EmaTrendFollowingAssessment run(List<KlineResponse> htf, double[] entry, EMATrendFollowingConfig cfg) {
        return EmaTrendFollowingAnalyzer.analyze("TESTUSDT", htf, series(entry, 0.05, 100), cfg);
    }

    @Test
    void bullishTransition_isActionable() {
        EmaTrendFollowingAssessment a = run(risingHtf(), entryCloses(), easy());
        assertThat(a.actionable()).isTrue();
        assertThat(a.state()).isEqualTo(EmaTrendFollowingState.SIGNAL_READY);
        assertThat(a.reason()).isEqualTo(EmaTrendFollowingReason.NONE);
        assertThat(a.setupId()).startsWith("ETF:");
        assertThat(a.stopLoss()).isLessThan(a.entry());
        assertThat(a.tp1()).isGreaterThan(a.entry());
        assertThat(a.tp2()).isGreaterThan(a.tp1());
        assertThat(a.tp3()).isGreaterThan(a.tp2());
        assertThat(a.explanation()).contains("EMA_TREND_FOLLOWING");
    }

    @Test
    void bearishHtf_isNoTrend() {
        double[] htf = new double[30];
        for (int i = 0; i < htf.length; i++) htf[i] = 120 - i * 1.0;
        EmaTrendFollowingAssessment a = run(series(htf, 0.3, 1000), entryCloses(), easy());
        assertThat(a.reason()).isEqualTo(EmaTrendFollowingReason.NO_BULLISH_TREND);
    }

    @Test
    void noCrossover_isNoTransition() {
        double[] flat = new double[41];
        for (int i = 0; i < flat.length; i++) flat[i] = 100;
        EmaTrendFollowingAssessment a = run(risingHtf(), flat, easy());
        assertThat(a.reason()).isEqualTo(EmaTrendFollowingReason.NO_EMA_TRANSITION);
    }

    @Test
    void weakEmaSeparation_isRejected() {
        EMATrendFollowingConfig cfg = easy();
        cfg.setMinimumEmaSeparationPct(5.0);
        assertThat(run(risingHtf(), entryCloses(), cfg).reason())
                .isEqualTo(EmaTrendFollowingReason.WEAK_EMA_SEPARATION);
    }

    @Test
    void rsiOutOfRange_isRejected() {
        EMATrendFollowingConfig cfg = easy();
        cfg.setRsiFilterEnabled(true);
        cfg.setMinimumRsiForLong(0);
        cfg.setMaximumRsiForLong(1); // impossible -> reject
        assertThat(run(risingHtf(), entryCloses(), cfg).reason())
                .isEqualTo(EmaTrendFollowingReason.RSI_OUT_OF_RANGE);
    }

    @Test
    void volumeBelowThreshold_isRejected() {
        EMATrendFollowingConfig cfg = easy();
        cfg.setVolumeFilterEnabled(true);
        cfg.setMinimumVolumeRatio(100); // impossible
        assertThat(run(risingHtf(), entryCloses(), cfg).reason())
                .isEqualTo(EmaTrendFollowingReason.VOLUME_TOO_LOW);
    }

    @Test
    void atrBelowThreshold_isRejected() {
        EMATrendFollowingConfig cfg = easy();
        cfg.setAtrFilterEnabled(true);
        cfg.setMinimumAtrPct(100); // impossible
        assertThat(run(risingHtf(), entryCloses(), cfg).reason())
                .isEqualTo(EmaTrendFollowingReason.ATR_TOO_LOW);
    }

    @Test
    void score_isNormalized0To100_forWeightSums100_70_150() {
        int[][] sets = {
                {30, 20, 20, 15, 10, 5},   // 100
                {10, 10, 10, 10, 10, 10},  // 60
                {40, 30, 30, 20, 20, 10},  // 150
        };
        for (int[] w : sets) {
            EMATrendFollowingConfig cfg = easy();
            applyWeights(cfg, w);
            EmaTrendFollowingAssessment a = run(risingHtf(), entryCloses(), cfg);
            assertThat(a.score()).isBetween(0, 100);
            if (a.actionable()) {
                assertThat(a.breakdown().total()).isEqualTo(a.score());
            }
        }
    }

    @Test
    void disabledCriteria_excludedFromDenominator_notFreePoints() {
        EMATrendFollowingConfig cfg = easy();
        applyWeights(cfg, new int[]{0, 0, 0, 0, 10, 0}); // only volume carries weight
        cfg.setVolumeFilterEnabled(false);
        assertThat(run(risingHtf(), entryCloses(), cfg).reason())
                .isEqualTo(EmaTrendFollowingReason.SCORE_NOT_CONFIGURED);
    }

    @Test
    void allCriteriaZero_failsSafely() {
        EMATrendFollowingConfig cfg = easy();
        applyWeights(cfg, new int[]{0, 0, 0, 0, 0, 0});
        assertThat(run(risingHtf(), entryCloses(), cfg).reason())
                .isEqualTo(EmaTrendFollowingReason.SCORE_NOT_CONFIGURED);
    }

    @Test
    void rsiDisabled_contributesNoPoints() {
        EMATrendFollowingConfig cfg = easy();
        cfg.setRsiFilterEnabled(false);
        EmaTrendFollowingAssessment a = run(risingHtf(), entryCloses(), cfg);
        assertThat(a.actionable()).isTrue();
        assertThat(a.breakdown().momentum()).isZero();
    }

    @Test
    void setupId_isDeterministicAndDistinctPerTransition() {
        EmaTrendFollowingAssessment a1 = run(risingHtf(), entryCloses(), easy());
        EmaTrendFollowingAssessment a2 = run(risingHtf(), entryCloses(), easy());
        assertThat(a1.setupId()).isEqualTo(a2.setupId());

        double[] shifted = new double[42];
        for (int i = 0; i < 41; i++) shifted[i] = 100;
        shifted[41] = 101; // transition one candle later
        EmaTrendFollowingAssessment a3 = run(risingHtf(), shifted, easy());
        assertThat(a3.setupId()).isNotEqualTo(a1.setupId());
    }

    @Test
    void insufficientData_isReported() {
        EmaTrendFollowingAssessment a = run(
                series(new double[]{100, 101, 102}, 0.3, 1000),
                new double[]{100, 100, 100}, easy());
        assertThat(a.state()).isEqualTo(EmaTrendFollowingState.INSUFFICIENT_DATA);
    }

    private static void applyWeights(EMATrendFollowingConfig c, int[] w) {
        c.setWeightTrendAlignment(w[0]);
        c.setWeightEmaTransition(w[1]);
        c.setWeightPriceConfirmation(w[2]);
        c.setWeightMomentum(w[3]);
        c.setWeightVolume(w[4]);
        c.setWeightVolatility(w[5]);
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }
}
