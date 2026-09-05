package com.shyblack.cryptosignals.signal;

import com.shyblack.cryptosignals.entity.enums.SignalGrade;

public record ScoreCard(
        int marketRegimeScore,   // max 10
        int trend4hScore,        // max 20
        int confirmation1hScore, // max 20
        int entrySetup15mScore,  // max 20
        int volumeScore,         // max 10
        int structureScore,      // max 10
        int riskRewardScore      // max 10
) {
    public int total() {
        return marketRegimeScore + trend4hScore + confirmation1hScore
                + entrySetup15mScore + volumeScore + structureScore + riskRewardScore;
    }

    public SignalGrade grade() {
        int t = total();
        if (t >= SignalConstants.SCORE_STRONG_BUY) return SignalGrade.STRONG_BUY;
        if (t >= SignalConstants.SCORE_BUY)        return SignalGrade.BUY;
        if (t >= SignalConstants.SCORE_WATCH)      return SignalGrade.WATCH;
        return SignalGrade.NO_TRADE;
    }
}
