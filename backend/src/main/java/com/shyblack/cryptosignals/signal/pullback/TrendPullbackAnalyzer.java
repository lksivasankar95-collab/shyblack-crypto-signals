package com.shyblack.cryptosignals.signal.pullback;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.dto.strategy.TrendPullbackConfig;
import com.shyblack.cryptosignals.entity.enums.MarketRegime;
import com.shyblack.cryptosignals.signal.IndicatorEngine;
import com.shyblack.cryptosignals.signal.StructureAnalyzer;
import java.util.List;

/**
 * Deterministic TREND_PULLBACK evaluator.
 *
 * Works on CLOSED candles only. Given the closed higher-timeframe series and
 * the closed entry-timeframe series (newest candle last), it derives the
 * setup state causally and returns a fully-explained assessment. It performs
 * no I/O, holds no mutable state, and is the single implementation shared by
 * the live signal service and the backtest strategy — so their decisions can
 * never diverge.
 */
public final class TrendPullbackAnalyzer {

    private static final String ZONE_EMA20 = "EMA20";

    private TrendPullbackAnalyzer() {}

    public static TrendPullbackAssessment analyze(
            String symbol,
            List<KlineResponse> htfCandles,
            List<KlineResponse> entryCandles,
            TrendPullbackConfig cfg) {

        int htfMin = cfg.getEmaSlowHtf() + cfg.getSlopeLookback() + 5;
        int entryNeeded = Math.max(Math.max(cfg.getEntryEma(), cfg.getPullbackEma()),
                Math.max(cfg.getAtrPeriod(), cfg.getRsiPeriod()));
        int entryMin = entryNeeded + cfg.getSwingLookback() + cfg.getMaxSetupCandles() + 5;

        if (htfCandles == null || entryCandles == null
                || htfCandles.size() < htfMin || entryCandles.size() < entryMin) {
            return fail(null, TrendPullbackState.INSUFFICIENT_DATA,
                    TrendRejectReason.INSUFFICIENT_DATA, 0, MarketRegime.NEUTRAL,
                    "Insufficient closed-candle history", null, null, null, null, null, 0);
        }

        // ── Higher timeframe ────────────────────────────────────────────────
        IndicatorEngine.Params htfParams = new IndicatorEngine.Params(
                cfg.getEmaFastHtf(), cfg.getEmaFastHtf(), cfg.getEmaSlowHtf(),
                cfg.getRsiPeriod(), cfg.getAtrPeriod(), cfg.getAdxPeriod(), cfg.getVolumeSmaPeriod());
        IndicatorEngine.Indicators h = IndicatorEngine.compute(htfCandles, htfParams);
        int m = htfCandles.size();

        double hClose = h.lastClose();
        double htfEmaFast = h.lastFastEma();
        double htfEmaSlow = h.lastSlowEma();
        double adx = h.lastAdx();
        double slopePrev = h.fastEma()[Math.max(0, m - 1 - cfg.getSlopeLookback())];
        boolean slopeUp = htfEmaFast > slopePrev;

        MarketRegime regime;
        if (htfEmaFast > htfEmaSlow && hClose > htfEmaSlow) regime = MarketRegime.BULLISH;
        else if (htfEmaFast < htfEmaSlow && hClose < htfEmaSlow) regime = MarketRegime.BEARISH;
        else regime = MarketRegime.NEUTRAL;

        if (!(htfEmaFast > htfEmaSlow && hClose > htfEmaSlow)) {
            return fail(null, TrendPullbackState.NO_TREND, TrendRejectReason.NO_BULLISH_TREND,
                    0, regime, "HTF not bullish", adx, null, null, htfEmaFast, htfEmaSlow, 0);
        }
        if (adx < cfg.getMinAdx()) {
            return fail(null, TrendPullbackState.NO_TREND, TrendRejectReason.ADX_BELOW_MINIMUM,
                    0, regime, String.format("ADX %.1f < minimum %.1f", adx, cfg.getMinAdx()),
                    adx, null, null, htfEmaFast, htfEmaSlow, 0);
        }
        if (cfg.isRequirePositiveSlope() && !slopeUp) {
            return fail(null, TrendPullbackState.NO_TREND, TrendRejectReason.SLOPE_NOT_POSITIVE,
                    0, regime, "EMA-fast slope is not positive",
                    adx, null, null, htfEmaFast, htfEmaSlow, 0);
        }

        StructureAnalyzer.StructureSummary htfStructure =
                StructureAnalyzer.analyze(h.high(), h.low(), h.close(), 20);
        double resistance = htfStructure.nearestResistance(hClose);

        // ── Entry timeframe ─────────────────────────────────────────────────
        IndicatorEngine.Params entryParams = new IndicatorEngine.Params(
                cfg.getPullbackEma(), cfg.getEntryEma(), cfg.getEntryEma(),
                cfg.getRsiPeriod(), cfg.getAtrPeriod(), cfg.getAdxPeriod(), cfg.getVolumeSmaPeriod());
        IndicatorEngine.Indicators e = IndicatorEngine.compute(entryCandles, entryParams);
        int n = entryCandles.size();

        double[] close = e.close();
        double[] high = e.high();
        double[] low = e.low();
        double[] vol = e.volume();
        double[] ema20 = e.fastEma();
        double[] ema50 = e.midEma();
        double[] rsi = e.rsi();
        double[] atr = e.atr();
        double[] volMa = e.volumeMa20();

        double rsiNow = rsi[n - 1];
        double rsiPrev = rsi[n - 2];

        int window = cfg.getMaxSetupCandles() + cfg.getSwingLookback() + 2;
        int from = Math.max(0, n - window);

        int lastInZone = -1;
        for (int k = n - 1; k >= from; k--) {
            if (inZone(k, low, ema20, ema50, cfg)) {
                lastInZone = k;
                break;
            }
        }
        if (lastInZone < 0) {
            return fail(null, TrendPullbackState.TREND_CONFIRMED, TrendRejectReason.NO_PULLBACK,
                    0, regime, "No pullback into the configured EMA zone",
                    adx, rsiNow, rsiPrev, htfEmaFast, htfEmaSlow, 0);
        }

        int runStart = lastInZone;
        while (runStart - 1 >= from && inZone(runStart - 1, low, ema20, ema50, cfg)) {
            runStart--;
        }

        double pullbackLow = Double.POSITIVE_INFINITY;
        int pullbackLowIndex = runStart;
        for (int k = runStart; k < n; k++) {
            if (low[k] < pullbackLow) {
                pullbackLow = low[k];
                pullbackLowIndex = k;
            }
        }
        int candlesSince = (n - 1) - lastInZone;
        String setupId = "TP:" + entryCandles.get(runStart).openTime()
                + ":" + entryCandles.get(pullbackLowIndex).openTime();

        if (candlesSince > cfg.getMaxSetupCandles()) {
            return fail(setupId, TrendPullbackState.EXPIRED, TrendRejectReason.SETUP_EXPIRED,
                    0, regime, "Setup expired after " + candlesSince + " candles",
                    adx, rsiNow, rsiPrev, htfEmaFast, htfEmaSlow, candlesSince);
        }

        // ── Invalidation ────────────────────────────────────────────────────
        for (int k = runStart; k < n; k++) {
            double tol = atr[k] * cfg.getMaxPullbackDistanceAtr();
            if (close[k] < ema50[k] - tol) {
                return fail(setupId, TrendPullbackState.INVALIDATED,
                        TrendRejectReason.PULLBACK_TOO_DEEP, 0, regime,
                        "Pullback closed too far below the entry EMA",
                        adx, rsiNow, rsiPrev, htfEmaFast, htfEmaSlow, candlesSince);
            }
            double body = entryCandles.get(k).open().doubleValue() - close[k];
            if (body > cfg.getInvalidationBodyAtr() * atr[k]
                    && vol[k] > cfg.getInvalidationVolumeMult() * volMa[k]
                    && close[k] < ema50[k]) {
                return fail(setupId, TrendPullbackState.INVALIDATED,
                        TrendRejectReason.BEARISH_BREAKDOWN_CANDLE, 0, regime,
                        "Large bearish candle on high volume below the entry EMA",
                        adx, rsiNow, rsiPrev, htfEmaFast, htfEmaSlow, candlesSince);
            }
        }

        // ── Momentum ────────────────────────────────────────────────────────
        double rsiAtLow = rsi[pullbackLowIndex];
        if (rsiAtLow < cfg.getRsiMin() || rsiAtLow > cfg.getRsiMax()) {
            return fail(setupId, TrendPullbackState.PULLBACK_DETECTED,
                    TrendRejectReason.RSI_OUT_OF_RANGE, 0, regime,
                    String.format("RSI at pullback %.1f outside [%.0f,%.0f]",
                            rsiAtLow, cfg.getRsiMin(), cfg.getRsiMax()),
                    adx, rsiNow, rsiPrev, htfEmaFast, htfEmaSlow, candlesSince);
        }
        if (cfg.isRequireRecovery() && rsiNow <= rsiPrev) {
            return fail(setupId, TrendPullbackState.PULLBACK_DETECTED,
                    TrendRejectReason.RSI_NOT_RECOVERING, 0, regime,
                    String.format("RSI not recovering (%.1f <= %.1f)", rsiNow, rsiPrev),
                    adx, rsiNow, rsiPrev, htfEmaFast, htfEmaSlow, candlesSince);
        }

        // ── Structure ───────────────────────────────────────────────────────
        int preFrom = Math.max(0, runStart - cfg.getSwingLookback());
        double breakLevel = Double.NEGATIVE_INFINITY;
        for (int k = preFrom; k < runStart; k++) {
            breakLevel = Math.max(breakLevel, high[k]);
        }
        if (breakLevel == Double.NEGATIVE_INFINITY) breakLevel = ema20[runStart];

        boolean structureBreak = close[n - 1] > breakLevel && close[n - 2] <= breakLevel;

        // ── Candle confirmation ─────────────────────────────────────────────
        double openNow = entryCandles.get(n - 1).open().doubleValue();
        boolean bullishCandle = close[n - 1] > openNow;
        boolean reclaimPrevHigh = close[n - 1] > high[n - 2];
        double range = high[n - 1] - low[n - 1];
        double lowerWick = Math.min(openNow, close[n - 1]) - low[n - 1];
        boolean wickOk = cfg.getMinLowerWickRatio() <= 0
                || (range > 0 && lowerWick / range >= cfg.getMinLowerWickRatio());
        boolean candleConfirmed = bullishCandle && reclaimPrevHigh && wickOk;

        if (!structureBreak) {
            return fail(setupId, TrendPullbackState.WAITING_CONFIRMATION,
                    TrendRejectReason.NO_STRUCTURE_BREAKOUT, 0, regime,
                    "No fresh structural breakout of the pre-pullback swing high",
                    adx, rsiNow, rsiPrev, htfEmaFast, htfEmaSlow, candlesSince);
        }
        if (cfg.isCandleConfirmationEnabled() && !candleConfirmed) {
            return fail(setupId, TrendPullbackState.WAITING_CONFIRMATION,
                    TrendRejectReason.CANDLE_NOT_CONFIRMED, 0, regime,
                    "Confirmation candle is not bullish / does not reclaim the prior high",
                    adx, rsiNow, rsiPrev, htfEmaFast, htfEmaSlow, candlesSince);
        }

        // ── Volume ──────────────────────────────────────────────────────────
        if (cfg.isVolumeFilterEnabled()
                && vol[n - 1] < cfg.getMinVolumeMultiplier() * volMa[n - 1]) {
            return fail(setupId, TrendPullbackState.WAITING_CONFIRMATION,
                    TrendRejectReason.VOLUME_TOO_LOW, 0, regime,
                    String.format("Volume %.2fx below minimum %.2fx",
                            vol[n - 1] / Math.max(volMa[n - 1], 1e-9), cfg.getMinVolumeMultiplier()),
                    adx, rsiNow, rsiPrev, htfEmaFast, htfEmaSlow, candlesSince);
        }

        // ── Entry / stop / targets ──────────────────────────────────────────
        double entryPrice = close[n - 1];
        double atrNow = atr[n - 1];
        double stopLoss = pullbackLow - atrNow * cfg.getSlAtrBuffer();
        double risk = entryPrice - stopLoss;
        if (risk <= 0) {
            return fail(setupId, TrendPullbackState.PULLBACK_VALID,
                    TrendRejectReason.INVALID_STOP, 0, regime, "Stop loss is not below entry",
                    adx, rsiNow, rsiPrev, htfEmaFast, htfEmaSlow, candlesSince);
        }
        if (risk > cfg.getMaxSlAtr() * atrNow) {
            return fail(setupId, TrendPullbackState.PULLBACK_VALID,
                    TrendRejectReason.STOP_TOO_WIDE, 0, regime,
                    String.format("Stop distance %.2f ATR exceeds max %.2f ATR",
                            risk / Math.max(atrNow, 1e-9), cfg.getMaxSlAtr()),
                    adx, rsiNow, rsiPrev, htfEmaFast, htfEmaSlow, candlesSince);
        }
        if (resistance <= entryPrice) {
            return fail(setupId, TrendPullbackState.PULLBACK_VALID,
                    TrendRejectReason.RESISTANCE_TOO_CLOSE, 0, regime,
                    "No overhead resistance / resistance below entry",
                    adx, rsiNow, rsiPrev, htfEmaFast, htfEmaSlow, candlesSince);
        }
        double rr = (resistance - entryPrice) / risk;
        if (rr < cfg.getMinRR()) {
            return fail(setupId, TrendPullbackState.PULLBACK_VALID,
                    TrendRejectReason.INSUFFICIENT_RR, 0, regime,
                    String.format("R:R %.2f < minimum %.2f", rr, cfg.getMinRR()),
                    adx, rsiNow, rsiPrev, htfEmaFast, htfEmaSlow, candlesSince);
        }
        double tp1 = entryPrice + risk * cfg.getTp1R();
        double tp2 = entryPrice + risk * cfg.getTp2R();
        double tp3 = entryPrice + risk * cfg.getTp3R();

        // ── Score (only after every mandatory condition passed) ─────────────
        ScoreOutcome outcome = score(
                cfg, htfEmaFast, htfEmaSlow, hClose, slopeUp, adx,
                pullbackLow, entryPrice, ema50[n - 1], rsiAtLow, rsiNow, rsiPrev,
                vol[n - 1], volMa[n - 1], structureBreak, low[n - 1] >= low[n - 2], rr);
        if (!outcome.configured()) {
            return fail(setupId, TrendPullbackState.PULLBACK_VALID,
                    TrendRejectReason.SCORE_NOT_CONFIGURED, 0, regime,
                    "No active scoring criteria (all active weights are zero)",
                    adx, rsiNow, rsiPrev, htfEmaFast, htfEmaSlow, candlesSince);
        }
        TrendPullbackAssessment.ScoreBreakdown breakdown = outcome.breakdown();
        int total = breakdown.total();
        if (total < cfg.getMinimumScore()) {
            return new TrendPullbackAssessment(setupId, TrendPullbackState.PULLBACK_VALID,
                    false, TrendRejectReason.SCORE_BELOW_MINIMUM, total, breakdown,
                    String.format("Score %d below minimum %d", total, cfg.getMinimumScore()),
                    regime, null, null, null, null, null, null, resistance, pullbackLow,
                    adx, rsiNow, rsiPrev, htfEmaFast, htfEmaSlow, candlesSince);
        }

        String explanation = explain(symbol, cfg, regime, htfEmaFast, htfEmaSlow, adx,
                entryPrice, stopLoss, tp1, tp2, tp3, rr, pullbackLow,
                rsiAtLow, rsiNow, vol[n - 1], volMa[n - 1], total, breakdown);

        return new TrendPullbackAssessment(setupId, TrendPullbackState.SIGNAL_READY,
                true, TrendRejectReason.NONE, total, breakdown, explanation,
                regime, entryPrice, stopLoss, tp1, tp2, tp3, rr, resistance, pullbackLow,
                adx, rsiNow, rsiPrev, htfEmaFast, htfEmaSlow, candlesSince);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static boolean inZone(int k, double[] low, double[] ema20, double[] ema50,
            TrendPullbackConfig cfg) {
        if (low[k] > ema20[k]) return false;
        boolean bandMode = !ZONE_EMA20.equalsIgnoreCase(cfg.getZoneMode());
        return !bandMode || low[k] >= ema50[k];
    }

    /** Result of scoring — {@code configured=false} when no criterion is active. */
    private record ScoreOutcome(boolean configured, TrendPullbackAssessment.ScoreBreakdown breakdown) {}

    /**
     * Normalized 0–100 score.
     *
     * Each criterion earns {@code fraction × weight} points. Only ACTIVE
     * criteria participate: a criterion whose optional filter is disabled
     * (volume confirmation, RSI recovery) is excluded from BOTH the earned
     * points and the denominator, so disabling a filter never awards free
     * points. The weighted sum is scaled against the total active weight, so
     * the result is 0–100 and independent of the absolute weight scale
     * (weights summing to 70, 100 or 150 yield the same score for the same
     * fractions). If every active weight is zero the score is left
     * unconfigured and the caller rejects the signal instead of fabricating a
     * 100.
     */
    private static ScoreOutcome score(
            TrendPullbackConfig cfg,
            double htfEmaFast, double htfEmaSlow, double hClose, boolean slopeUp, double adx,
            double pullbackLow, double entry, double ema50Now, double rsiAtLow,
            double rsiNow, double rsiPrev, double vol, double volMa,
            boolean structureBreak, boolean higherLows, double rr) {

        int[] weights = {
                cfg.getWeightTrend(), cfg.getWeightAdx(), cfg.getWeightPullback(),
                cfg.getWeightRsi(), cfg.getWeightVolume(), cfg.getWeightStructure(),
                cfg.getWeightRiskReward()
        };
        // rsi criterion is active only when recovery is required; volume
        // criterion only when the volume filter is enabled.
        boolean[] active = {
                true, true, true, cfg.isRequireRecovery(), cfg.isVolumeFilterEnabled(), true, true
        };

        double trendFrac = 0;
        if (htfEmaFast > htfEmaSlow) trendFrac += 0.5;
        if (hClose > htfEmaSlow) trendFrac += 0.25;
        if (slopeUp) trendFrac += 0.25;

        double adxFrac = clamp((adx - cfg.getMinAdx()) / 15.0, 0, 1);

        double pullFrac = 0.4; // reached the zone (guaranteed here)
        if (pullbackLow >= ema50Now) pullFrac += 0.3;
        if (entry > pullbackLow) pullFrac += 0.3;

        double rsiFrac = 0;
        if (!cfg.isRequireRecovery() || rsiNow > rsiPrev) rsiFrac += 0.6;
        rsiFrac += 0.4 * (1 - clamp(Math.abs(rsiAtLow - 50.0) / 10.0, 0, 1));

        double volFrac = clamp((vol / Math.max(volMa, 1e-9) - 1.0) / 0.5, 0, 1);

        double structFrac = 0;
        if (higherLows) structFrac += 0.45;
        if (structureBreak) structFrac += 0.55;

        double rrFrac;
        if (rr >= 3.0) rrFrac = 1.0;
        else if (rr >= 2.0) rrFrac = 0.7;
        else if (rr >= cfg.getMinRR()) rrFrac = 0.4;
        else rrFrac = 0.0;

        double[] fracs = { trendFrac, adxFrac, pullFrac, rsiFrac, volFrac, structFrac, rrFrac };

        int activeWeight = 0;
        for (int i = 0; i < weights.length; i++) {
            if (active[i]) activeWeight += Math.max(0, weights[i]);
        }
        if (activeWeight <= 0) {
            return new ScoreOutcome(false,
                    new TrendPullbackAssessment.ScoreBreakdown(0, 0, 0, 0, 0, 0, 0));
        }

        double[] raw = new double[weights.length];
        double earned = 0;
        for (int i = 0; i < weights.length; i++) {
            raw[i] = active[i] ? 100.0 * Math.max(0, weights[i]) * clamp(fracs[i], 0, 1) / activeWeight : 0;
            earned += raw[i];
        }
        int target = (int) Math.round(earned);
        target = Math.max(0, Math.min(100, target));

        // Largest-remainder allocation: the per-criterion points sum exactly to
        // the normalized total, keeping the explanation consistent.
        int[] points = new int[weights.length];
        int assigned = 0;
        for (int i = 0; i < weights.length; i++) {
            points[i] = (int) Math.floor(raw[i]);
            assigned += points[i];
        }
        int remaining = target - assigned;
        while (remaining > 0) {
            int best = -1;
            double bestRemaining = 0;
            for (int i = 0; i < weights.length; i++) {
                double rem = raw[i] - Math.floor(raw[i]);
                if (rem > bestRemaining) { bestRemaining = rem; best = i; }
            }
            if (best < 0) break;
            points[best] += 1;
            raw[best] = Math.floor(raw[best]);
            remaining--;
        }
        while (remaining < 0) {
            int best = -1;
            int bestVal = 0;
            for (int i = 0; i < weights.length; i++) {
                if (points[i] > bestVal) { bestVal = points[i]; best = i; }
            }
            if (best < 0) break;
            points[best] -= 1;
            remaining++;
        }

        return new ScoreOutcome(true, new TrendPullbackAssessment.ScoreBreakdown(
                points[0], points[1], points[2], points[3], points[4], points[5], points[6]));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String explain(
            String symbol, TrendPullbackConfig cfg, MarketRegime regime,
            double htfEmaFast, double htfEmaSlow, double adx,
            double entry, double sl, double tp1, double tp2, double tp3, double rr,
            double pullbackLow, double rsiAtLow, double rsiNow, double vol, double volMa,
            int total, TrendPullbackAssessment.ScoreBreakdown b) {

        String name = symbol == null ? "" : symbol + " ";
        double riskPct = (entry - sl) / entry * 100.0;
        return String.format(
                "%sTREND_PULLBACK BUY%n"
                + "HTF Trend: %s%n"
                + "HTF: EMA-fast=%.4f > EMA-slow=%.4f, ADX=%.1f%n"
                + "Pullback: retested %s zone, low=%.4f%n"
                + "Momentum: RSI %.1f -> %.1f%n"
                + "Confirmation: bullish structure breakout%n"
                + "Volume: %.2fx average%n"
                + "Risk: SL=%.2f%% TP1=%.2f%% TP2=%.2f%% TP3=%.2f%%%n"
                + "RR: %.2fR%n"
                + "Score: %d/100 (trend=%d adx=%d pullback=%d rsi=%d vol=%d struct=%d rr=%d)",
                name, regime,
                htfEmaFast, htfEmaSlow, adx,
                cfg.getZoneMode(), pullbackLow,
                rsiAtLow, rsiNow,
                vol / Math.max(volMa, 1e-9),
                riskPct, riskPct * cfg.getTp1R(), riskPct * cfg.getTp2R(), riskPct * cfg.getTp3R(),
                rr, total, b.trend(), b.adx(), b.pullback(), b.rsi(),
                b.volume(), b.structure(), b.riskReward());
    }

    private static TrendPullbackAssessment fail(String setupId, TrendPullbackState state,
            TrendRejectReason reason, int score, MarketRegime regime, String explanation,
            Double adx, Double rsiNow, Double rsiPrev, Double emaFast, Double emaSlow,
            int candlesSince) {
        return new TrendPullbackAssessment(setupId, state, false, reason, score, null,
                explanation, regime, null, null, null, null, null, null, null, null,
                adx, rsiNow, rsiPrev, emaFast, emaSlow, candlesSince);
    }
}
