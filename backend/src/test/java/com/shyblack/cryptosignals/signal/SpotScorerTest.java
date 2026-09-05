package com.shyblack.cryptosignals.signal;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.entity.enums.MarketRegime;
import com.shyblack.cryptosignals.entity.enums.SignalGrade;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpotScorerTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Market regime scoring
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void scoreMarketRegime_bullish_returns10() {
        assertThat(SpotScorer.scoreMarketRegime(MarketRegime.BULLISH)).isEqualTo(10);
    }

    @Test
    void scoreMarketRegime_neutral_returns4() {
        assertThat(SpotScorer.scoreMarketRegime(MarketRegime.NEUTRAL)).isEqualTo(4);
    }

    @Test
    void scoreMarketRegime_bearish_returns0() {
        assertThat(SpotScorer.scoreMarketRegime(MarketRegime.BEARISH)).isEqualTo(0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Risk/reward scoring
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void scoreRiskReward_excellent_returns10() {
        assertThat(SpotScorer.scoreRiskReward(3.5)).isEqualTo(10);
    }

    @Test
    void scoreRiskReward_good_returns7() {
        assertThat(SpotScorer.scoreRiskReward(2.5)).isEqualTo(7);
    }

    @Test
    void scoreRiskReward_minimum_returns4() {
        assertThat(SpotScorer.scoreRiskReward(1.6)).isEqualTo(4);
    }

    @Test
    void scoreRiskReward_belowMin_returns0() {
        assertThat(SpotScorer.scoreRiskReward(1.2)).isEqualTo(0);
    }

    @Test
    void scoreRiskReward_exactExcellent_returns10() {
        assertThat(SpotScorer.scoreRiskReward(SignalConstants.EXCELLENT_RR)).isEqualTo(10);
    }

    @Test
    void scoreRiskReward_exactGood_returns7() {
        assertThat(SpotScorer.scoreRiskReward(SignalConstants.GOOD_RR)).isEqualTo(7);
    }

    @Test
    void scoreRiskReward_exactMin_returns4() {
        assertThat(SpotScorer.scoreRiskReward(SignalConstants.MIN_RR)).isEqualTo(4);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Volume scoring
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void scoreVolume_highVolume_returns10() {
        IndicatorEngine.Indicators ind = buildFlatIndicators(100, 1000, 1600); // ratio = 1.6x
        assertThat(SpotScorer.scoreVolume(ind, 5.0)).isEqualTo(10);
    }

    @Test
    void scoreVolume_moderateVolume_returns6() {
        IndicatorEngine.Indicators ind = buildFlatIndicators(100, 1000, 1250); // ratio = 1.25x
        assertThat(SpotScorer.scoreVolume(ind, 5.0)).isEqualTo(6);
    }

    @Test
    void scoreVolume_atMa_returns4() {
        IndicatorEngine.Indicators ind = buildFlatIndicators(100, 1000, 1000); // ratio = 1.0x
        assertThat(SpotScorer.scoreVolume(ind, 5.0)).isEqualTo(4);
    }

    @Test
    void scoreVolume_belowMa_returns0() {
        IndicatorEngine.Indicators ind = buildFlatIndicators(100, 1000, 800); // ratio = 0.8x
        assertThat(SpotScorer.scoreVolume(ind, 5.0)).isEqualTo(0);
    }

    @Test
    void scoreVolume_extremePumpReducesHighToFour() {
        IndicatorEngine.Indicators ind = buildFlatIndicators(100, 1000, 1600);
        assertThat(SpotScorer.scoreVolume(ind, 35.0)).isEqualTo(4);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ScoreCard grade thresholds
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void scoreCard_grade_strongBuy_above85() {
        ScoreCard card = new ScoreCard(10, 20, 20, 20, 10, 5, 0); // = 85
        assertThat(card.total()).isEqualTo(85);
        assertThat(card.grade()).isEqualTo(SignalGrade.STRONG_BUY);
    }

    @Test
    void scoreCard_grade_buy_between75And84() {
        ScoreCard card = new ScoreCard(10, 15, 14, 14, 10, 8, 4); // = 75
        assertThat(card.total()).isEqualTo(75);
        assertThat(card.grade()).isEqualTo(SignalGrade.BUY);
    }

    @Test
    void scoreCard_grade_watch_between65And74() {
        ScoreCard card = new ScoreCard(4, 15, 14, 14, 6, 8, 4); // = 65
        assertThat(card.total()).isEqualTo(65);
        assertThat(card.grade()).isEqualTo(SignalGrade.WATCH);
    }

    @Test
    void scoreCard_grade_noTrade_below65() {
        ScoreCard card = new ScoreCard(0, 8, 8, 8, 4, 3, 4); // = 35
        assertThat(card.grade()).isEqualTo(SignalGrade.NO_TRADE);
    }

    @Test
    void scoreCard_total_sumOfAllComponents() {
        ScoreCard card = new ScoreCard(10, 20, 20, 20, 10, 10, 10);
        assertThat(card.total()).isEqualTo(100);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Structure scoring
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void scoreStructure_higherLowsAndHighs_returns10() {
        StructureAnalyzer.StructureSummary structure = buildStructure(true, true, 100, 110);
        assertThat(SpotScorer.scoreStructure(structure, 105.0)).isEqualTo(10);
    }

    @Test
    void scoreStructure_higherLowsOnly_returns6() {
        StructureAnalyzer.StructureSummary structure = buildStructure(true, false, 100, 110);
        assertThat(SpotScorer.scoreStructure(structure, 105.0)).isEqualTo(6);
    }

    @Test
    void scoreStructure_noStructure_returns3() {
        StructureAnalyzer.StructureSummary structure = buildStructure(false, false, 100, 110);
        assertThat(SpotScorer.scoreStructure(structure, 105.0)).isEqualTo(3);
    }

    @Test
    void scoreStructure_priceAtResistance_returns1() {
        // nearestResistance = 105.5 (just 0.5% above 105)
        StructureAnalyzer.StructureSummary structure = buildStructure(true, true, 100, 105.5);
        assertThat(SpotScorer.scoreStructure(structure, 105.0)).isEqualTo(1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Trend 4H scoring with pump penalties
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void scoreTrend4h_extremePump_appliesPenalty() {
        // Build indicators: price > EMA200 > EMA50 > EMA20 → would score 20
        IndicatorEngine.Indicators ind = buildTrendingIndicators(200.0, 150.0, 120.0, 100.0);
        int score = SpotScorer.scoreTrend4h(ind, 35.0); // extreme pump
        assertThat(score).isEqualTo(12); // 20 - 8 = 12
    }

    @Test
    void scoreTrend4h_moderatePump_appliesSmallPenalty() {
        IndicatorEngine.Indicators ind = buildTrendingIndicators(200.0, 150.0, 120.0, 100.0);
        int score = SpotScorer.scoreTrend4h(ind, 18.0); // moderate pump
        assertThat(score).isEqualTo(16); // 20 - 4 = 16
    }

    @Test
    void scoreTrend4h_noPump_fullScore() {
        IndicatorEngine.Indicators ind = buildTrendingIndicators(200.0, 150.0, 120.0, 100.0);
        int score = SpotScorer.scoreTrend4h(ind, 5.0);
        assertThat(score).isEqualTo(20);
    }

    @Test
    void scoreTrend4h_priceBelowAll_returnsZero() {
        // price < EMA50 < EMA200
        IndicatorEngine.Indicators ind = buildTrendingIndicators(80.0, 120.0, 150.0, 200.0);
        int score = SpotScorer.scoreTrend4h(ind, 5.0);
        assertThat(score).isEqualTo(0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1H confirmation scoring
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void scoreConfirmation1h_allConditions_returns20() {
        // price > EMA20 > EMA50, RSI = 60, MACD hist positive
        IndicatorEngine.Indicators ind = buildConfirmationIndicators(100, 90, 80, 60, 5);
        assertThat(SpotScorer.scoreConfirmation1h(ind)).isEqualTo(20);
    }

    @Test
    void scoreConfirmation1h_extremeOverbought_penalized() {
        IndicatorEngine.Indicators ind = buildConfirmationIndicators(100, 90, 80, 82, 5);
        int score = SpotScorer.scoreConfirmation1h(ind);
        assertThat(score).isLessThan(20);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Build indicators where last volume = lastVol and volumeMA = vMa. */
    private static IndicatorEngine.Indicators buildFlatIndicators(int size, double vMa, double lastVol) {
        double[] close = new double[size];
        double[] high  = new double[size];
        double[] low   = new double[size];
        double[] vol   = new double[size];
        java.util.Arrays.fill(close, 100.0);
        java.util.Arrays.fill(high,  101.0);
        java.util.Arrays.fill(low,   99.0);
        java.util.Arrays.fill(vol,   vMa);   // first fill all with vMa (sets the MA)
        vol[size - 1] = lastVol;             // override last with the current volume

        double[] ema20    = IndicatorEngine.ema(close, 20);
        double[] ema50    = IndicatorEngine.ema(close, 50);
        double[] ema200   = IndicatorEngine.ema(close, 200);
        double[] rsi      = IndicatorEngine.rsi(close, 14);
        double[] atr      = IndicatorEngine.atr(high, low, close, 14);
        double[] adx      = IndicatorEngine.adx(high, low, close, atr, 14);
        double[] vma      = IndicatorEngine.sma(vol, 20);
        double[] macdLine = subtract(IndicatorEngine.ema(close, 12), IndicatorEngine.ema(close, 26));
        double[] macdSig  = IndicatorEngine.ema(macdLine, 9);
        double[] macdHist = subtract(macdLine, macdSig);

        return new IndicatorEngine.Indicators(close, high, low, vol, ema20, ema50, ema200,
                rsi, macdLine, macdSig, macdHist, atr, adx, vma, size);
    }

    private static double[] subtract(double[] a, double[] b) {
        double[] result = new double[a.length];
        for (int i = 0; i < a.length; i++) result[i] = a[i] - b[i];
        return result;
    }

    /** Build indicators where price=p, ema20=e20, ema50=e50, rsi=r, macdHist=mh. */
    private static IndicatorEngine.Indicators buildConfirmationIndicators(
            double price, double e20, double e50, double rsi, double macdHist) {
        int n = 50;
        double[] close     = fill(n, price);
        double[] high      = fill(n, price + 1);
        double[] low       = fill(n, price - 1);
        double[] vol       = fill(n, 1000);
        double[] ema20     = fill(n, e20);
        double[] ema50     = fill(n, e50);
        double[] ema200    = fill(n, e50 - 10);
        double[] rsiArr    = fill(n, rsi);
        double[] mLine     = fill(n, macdHist + 1);
        double[] mSig      = fill(n, 1);
        double[] mHist     = fill(n, macdHist);
        double[] atr       = fill(n, 2);
        double[] adx       = fill(n, 25);
        double[] vma       = fill(n, 1000);
        return new IndicatorEngine.Indicators(close, high, low, vol, ema20, ema50, ema200,
                rsiArr, mLine, mSig, mHist, atr, adx, vma, n);
    }

    /** Build indicators where price=p, ema20=e20, ema50=e50, ema200=e200. */
    private static IndicatorEngine.Indicators buildTrendingIndicators(
            double price, double e20, double e50, double e200) {
        int n = 50;
        double[] close  = fill(n, price);
        double[] high   = fill(n, price + 1);
        double[] low    = fill(n, price - 1);
        double[] vol    = fill(n, 1000);
        double[] ema20  = fill(n, e20);
        double[] ema50  = fill(n, e50);
        double[] ema200 = fill(n, e200);
        double[] rsi    = fill(n, 55);
        double[] mLine  = fill(n, 1);
        double[] mSig   = fill(n, 0.5);
        double[] mHist  = fill(n, 0.5);
        double[] atr    = fill(n, 2);
        double[] adx    = fill(n, 25);
        double[] vma    = fill(n, 1000);
        return new IndicatorEngine.Indicators(close, high, low, vol, ema20, ema50, ema200,
                rsi, mLine, mSig, mHist, atr, adx, vma, n);
    }

    private static double[] fill(int n, double v) {
        double[] arr = new double[n];
        java.util.Arrays.fill(arr, v);
        return arr;
    }

    /** Build a StructureSummary with fixed support and resistance. */
    private static StructureAnalyzer.StructureSummary buildStructure(
            boolean higherLows, boolean higherHighs, double support, double resistance) {
        List<StructureAnalyzer.StructureLevel> supports =
                List.of(new StructureAnalyzer.StructureLevel(support, false));
        List<StructureAnalyzer.StructureLevel> resistances =
                List.of(new StructureAnalyzer.StructureLevel(resistance, true));
        return new StructureAnalyzer.StructureSummary(supports, resistances, higherLows, higherHighs);
    }
}
