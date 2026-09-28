package com.shyblack.cryptosignals.signal.emafollowing;

import com.shyblack.cryptosignals.entity.enums.MarketRegime;

/** Immutable result of evaluating one symbol at the current closed candle. */
public record EmaTrendFollowingAssessment(
		String setupId,
		EmaTrendFollowingState state,
		boolean actionable,
		EmaTrendFollowingReason reason,
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
		Double ema20,
		Double ema50,
		Double htfEmaFast,
		Double htfEmaSlow,
		Double rsi,
		Double atrPct,
		Double volumeRatio
) {

	/** Per-component score so the UI can explain the total. */
	public record ScoreBreakdown(
			int trendAlignment,
			int emaTransition,
			int priceConfirmation,
			int momentum,
			int volume,
			int volatility
	) {
		public int total() {
			return trendAlignment + emaTransition + priceConfirmation + momentum + volume + volatility;
		}
	}

	public String logLine() {
		return String.format(
				"state=%s score=%d actionable=%s reason=%s entry=%s sl=%s rr=%s rsi=%s atr%%=%s volRatio=%s",
				state, score, actionable, reason, fmt(entry), fmt(stopLoss), fmt(riskReward),
				fmt(rsi), fmt(atrPct), fmt(volumeRatio));
	}

	private static String fmt(Double v) {
		return v == null ? "-" : String.format("%.4f", v);
	}
}
