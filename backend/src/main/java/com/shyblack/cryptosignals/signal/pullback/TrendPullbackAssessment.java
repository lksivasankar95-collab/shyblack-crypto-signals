package com.shyblack.cryptosignals.signal.pullback;

import com.shyblack.cryptosignals.entity.enums.MarketRegime;

/**
 * Immutable result of evaluating one symbol at the current closed candle.
 * Prices are {@code null} unless {@link #actionable()} is true.
 */
public record TrendPullbackAssessment(
        String setupId,
        TrendPullbackState state,
        boolean actionable,
        TrendRejectReason rejectReason,
        int score,
        ScoreBreakdown breakdown,
        String explanation,
        MarketRegime trend,
        Double entry,
        Double stopLoss,
        Double tp1,
        Double tp2,
        Double tp3,
        Double riskReward,
        Double resistance,
        Double pullbackLow,
        Double adx,
        Double rsiNow,
        Double rsiPrev,
        Double emaFastHtf,
        Double emaSlowHtf,
        int candlesSincePullback
) {

    /** Per-component score so the UI can explain the total. */
    public record ScoreBreakdown(
            int trend, int adx, int pullback, int rsi, int volume, int structure, int riskReward
    ) {
        public int total() {
            return trend + adx + pullback + rsi + volume + structure + riskReward;
        }
    }

    public String logLine() {
        return String.format(
                "state=%s score=%d actionable=%s reason=%s adx=%s rsi=%s/%s "
                        + "entry=%s sl=%s rr=%s",
                state, score, actionable, rejectReason,
                fmt(adx), fmt(rsiPrev), fmt(rsiNow),
                fmt(entry), fmt(stopLoss), fmt(riskReward));
    }

    private static String fmt(Double v) {
        return v == null ? "-" : String.format("%.4f", v);
    }
}
