package com.shyblack.cryptosignals.signal.nfm;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.dto.strategy.NfmFuturesConfig;
import com.shyblack.cryptosignals.entity.enums.FundingState;
import com.shyblack.cryptosignals.entity.enums.LiquidationState;
import com.shyblack.cryptosignals.entity.enums.MarketRegime;
import com.shyblack.cryptosignals.entity.enums.NfmAction;
import com.shyblack.cryptosignals.entity.enums.NfmGrade;
import com.shyblack.cryptosignals.entity.enums.NewsImpact;
import com.shyblack.cryptosignals.market.DerivativesSnapshot;
import com.shyblack.cryptosignals.signal.IndicatorEngine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * NFM signal decision engine (spec §3–§23). Pure and deterministic: given a
 * normalized event, futures candles, a derivatives snapshot and the market
 * regime, it produces one of LONG / SHORT / WAIT_CONFIRMATION / NO_TRADE /
 * RISK_BLOCKED.
 *
 * <p>News is the catalyst; the <em>market reaction</em> (post-event price move
 * + volume) determines direction — the engine never trades on headline
 * sentiment alone.</p>
 */
public final class NfmFuturesAnalyzer {

	private NfmFuturesAnalyzer() {
	}

	private static final int MIN_CANDLES = 30;

	public static NfmAssessment analyze(String symbol, NfmEventView event, List<KlineResponse> candles,
			DerivativesSnapshot derivatives, MarketRegime regime, NfmFuturesConfig cfg) {

		DerivativesSnapshot d = derivatives == null ? DerivativesSnapshot.unavailable() : derivatives;
		if (cfg == null) {
			cfg = NfmFuturesConfig.defaults();
		}
		int minCandles = Math.max(MIN_CANDLES, cfg.getAtrPeriod() + 2);
		if (candles == null || candles.size() < minCandles) {
			return rejected(NfmAction.NO_TRADE, 0, null, null, null, d, FundingState.UNKNOWN,
					LiquidationState.UNKNOWN, "insufficient candles for NFM evaluation", setupId(event, symbol, null, cfg));
		}
		if (event == null || !cfg.eventTypeAllowed(event.eventType() == null ? null : event.eventType().name())) {
			return rejected(NfmAction.NO_TRADE, 0, null, null, null, d, FundingState.UNKNOWN,
					LiquidationState.UNKNOWN, "event type not allowed", setupId(event, symbol, null, cfg));
		}

		IndicatorEngine.Params params = new IndicatorEngine.Params(
				20, 50, 200, 14, cfg.getAtrPeriod(), 14, cfg.getVolumeMaPeriod());
		IndicatorEngine.Indicators ind = IndicatorEngine.compute(candles, params);

		double lastClose = ind.lastClose();
		double atr = ind.lastAtr();
		double volumeMa = ind.lastVolumeMa();
		double lastVolume = ind.lastVolume();
		if (!(lastClose > 0)) {
			return rejected(NfmAction.NO_TRADE, 0, null, null, null, d, FundingState.UNKNOWN,
					LiquidationState.UNKNOWN, "no usable close price", setupId(event, symbol, null, cfg));
		}

		long eventMillis = event.eventTime() == null
				? candles.get(0).openTime() : event.eventTime().toEpochMilli();
		int eventIndex = 0;
		for (int i = 0; i < candles.size(); i++) {
			if (candles.get(i).openTime() >= eventMillis) {
				eventIndex = i;
				break;
			}
		}
		double priceAtEvent = closeAt(candles, eventIndex, lastClose);
		BigDecimal priceReactionPct = pct(priceAtEvent, lastClose);
		BigDecimal preEventReturnPct = pct(closeAt(candles, 0, lastClose), priceAtEvent);
		BigDecimal volumeMultiplier = volumeMa > 0
				? bd6(lastVolume / volumeMa) : null;

		FundingState funding = classifyFunding(d.lastFundingRate(), cfg);
		LiquidationState liquidation = classifyLiquidation(d, cfg);

		double threshold = cfg.getPriceReactionThresholdPct();
		NfmAction candidate;
		if (priceReactionPct == null) {
			candidate = NfmAction.NO_TRADE;
		} else if (priceReactionPct.doubleValue() >= threshold) {
			candidate = NfmAction.LONG;
		} else if (priceReactionPct.doubleValue() <= -threshold) {
			candidate = NfmAction.SHORT;
		} else {
			candidate = NfmAction.WAIT_CONFIRMATION;
		}
		String setup = setupId(event, symbol, candidate, cfg);

		if (candidate == NfmAction.NO_TRADE) {
			return rejected(candidate, 0, priceReactionPct, preEventReturnPct, volumeMultiplier, d, funding,
					liquidation, "no usable post-event reaction", setup);
		}
		if (candidate == NfmAction.WAIT_CONFIRMATION) {
			return rejected(candidate, 0, priceReactionPct, preEventReturnPct, volumeMultiplier, d, funding,
					liquidation, "post-event reaction below confirmation threshold", setup);
		}
		if (candidate == NfmAction.LONG && !cfg.isAllowLong()) {
			return rejected(NfmAction.NO_TRADE, 0, priceReactionPct, preEventReturnPct, volumeMultiplier, d,
					funding, liquidation, "long not allowed by config", setup);
		}
		if (candidate == NfmAction.SHORT && !cfg.isAllowShort()) {
			return rejected(NfmAction.NO_TRADE, 0, priceReactionPct, preEventReturnPct, volumeMultiplier, d,
					funding, liquidation, "short not allowed by config", setup);
		}
		// Funding crowding filter (§14): never chase an already-crowded side.
		if (candidate == NfmAction.LONG && funding == FundingState.EXTREME_LONG) {
			return rejected(NfmAction.RISK_BLOCKED, 0, priceReactionPct, preEventReturnPct, volumeMultiplier, d,
					funding, liquidation, "extreme long funding — crowded long, no chase", setup);
		}
		if (candidate == NfmAction.SHORT && funding == FundingState.EXTREME_SHORT) {
			return rejected(NfmAction.RISK_BLOCKED, 0, priceReactionPct, preEventReturnPct, volumeMultiplier, d,
					funding, liquidation, "extreme short funding — crowded short, no chase", setup);
		}
		if (volumeMultiplier == null || volumeMultiplier.doubleValue() < cfg.getVolumeThreshold()) {
			return rejected(NfmAction.WAIT_CONFIRMATION, 0, priceReactionPct, preEventReturnPct, volumeMultiplier,
					d, funding, liquidation, "volume not confirmed", setup);
		}
		boolean pricedIn = preEventReturnPct != null && (
				(candidate == NfmAction.LONG && preEventReturnPct.doubleValue() >= cfg.getPricedInReturnPct())
						|| (candidate == NfmAction.SHORT && preEventReturnPct.doubleValue() <= -cfg.getPricedInReturnPct()));
		if (pricedIn) {
			return rejected(NfmAction.WAIT_CONFIRMATION, 0, priceReactionPct, preEventReturnPct, volumeMultiplier,
					d, funding, liquidation, "move already extended pre-event (priced-in risk)", setup);
		}

		int score = score(event, candidate, priceReactionPct, volumeMultiplier, preEventReturnPct, d, funding,
				liquidation, regime, cfg);
		if (score < cfg.getMinimumScore()) {
			return rejected(NfmAction.NO_TRADE, score, priceReactionPct, preEventReturnPct, volumeMultiplier, d,
					funding, liquidation, "score below threshold", setup);
		}

		double entry = lastClose;
		double risk = atr * cfg.getSlAtrMultiplier();
		double minRisk = entry * cfg.getMinStopDistancePct() / 100.0;
		if (!(risk > 0) || risk < minRisk) {
			risk = minRisk;
		}
		if (!(risk > 0)) {
			return rejected(NfmAction.NO_TRADE, score, priceReactionPct, preEventReturnPct, volumeMultiplier, d,
					funding, liquidation, "invalid stop distance", setup);
		}
		boolean isLong = candidate == NfmAction.LONG;
		double stop = isLong ? entry - risk : entry + risk;
		double tp1 = isLong ? entry + risk * cfg.getTp1R() : entry - risk * cfg.getTp1R();
		double tp2 = isLong ? entry + risk * cfg.getTp2R() : entry - risk * cfg.getTp2R();
		double tp3 = isLong ? entry + risk * cfg.getTp3R() : entry - risk * cfg.getTp3R();
		double rr = Math.abs(tp1 - entry) / risk;
		if (rr < cfg.getMinRR()) {
			return rejected(NfmAction.NO_TRADE, score, priceReactionPct, preEventReturnPct, volumeMultiplier, d,
					funding, liquidation, "R:R below minimum", setup);
		}

		String reason = "reaction=" + fmt(priceReactionPct) + "% vol=" + fmt(volumeMultiplier)
				+ "x oi=" + fmt(d.openInterestChangePct()) + "% funding=" + funding
				+ " regime=" + regime + " score=" + score;
		return new NfmAssessment(true, candidate, grade(score, cfg), score,
				bd8(entry), bd8(stop), bd8(tp1), bd8(tp2), bd8(tp3), bd4(rr),
				priceReactionPct, preEventReturnPct, volumeMultiplier, d.openInterestChangePct(),
				funding, liquidation, reason, setup);
	}

	// ── Scoring (spec §17–§18) ──────────────────────────────────────────────

	static int score(NfmEventView event, NfmAction direction, BigDecimal priceReaction, BigDecimal volumeMultiplier,
			BigDecimal preEventReturn, DerivativesSnapshot d, FundingState funding, LiquidationState liquidation,
			MarketRegime regime, NfmFuturesConfig cfg) {

		double eventQuality = eventQuality(event);
		double surprise = surpriseScore(event);
		double relevance = relevanceScore(event.assetRelevance());
		double priceConf = clamp01(Math.abs(nz(priceReaction)) / (cfg.getPriceReactionThresholdPct() * 3.0));
		double volume = clamp01(nz(volumeMultiplier) / (cfg.getVolumeThreshold() * 2.0));
		double oi = oiScore(d, priceReaction);
		double fundingScore = fundingScore(funding, direction);
		double liquidationScore = liquidationScore(liquidation, direction);
		double regimeScore = regimeScore(regime, direction);
		double crossAsset = 0.5;

		double total = eventQuality * cfg.getWeightEventQuality()
				+ surprise * cfg.getWeightSurprise()
				+ relevance * cfg.getWeightAssetRelevance()
				+ priceConf * cfg.getWeightPriceConfirmation()
				+ volume * cfg.getWeightVolume()
				+ oi * cfg.getWeightOpenInterest()
				+ fundingScore * cfg.getWeightFunding()
				+ liquidationScore * cfg.getWeightLiquidation()
				+ regimeScore * cfg.getWeightMarketRegime()
				+ crossAsset * cfg.getWeightCrossAsset();
		return (int) Math.round(clamp(total, 0, 100));
	}

	static NfmGrade grade(int score, NfmFuturesConfig cfg) {
		if (score >= cfg.getGradeAThreshold()) return NfmGrade.A;
		if (score >= cfg.getGradeBThreshold()) return NfmGrade.B;
		return NfmGrade.C;
	}

	private static double eventQuality(NfmEventView event) {
		double impact = event.assetRelevance() == null
				? 0.0 : event.assetRelevance().ordinal() / 3.0;
		double tier = switch (event.sourceTier() == null ? com.shyblack.cryptosignals.entity.enums.NewsSourceTier.TIER_4
				: event.sourceTier()) {
			case TIER_1 -> 1.0;
			case TIER_2 -> 0.9;
			case TIER_3 -> 0.7;
			case TIER_4 -> 0.2;
		};
		double stage = switch (event.eventStage() == null
				? com.shyblack.cryptosignals.entity.enums.NewsEventStage.REPORT : event.eventStage()) {
			case OFFICIAL_CONFIRMATION, APPROVAL, LAUNCH -> 1.0;
			case FLOW -> 0.9;
			case REPORT -> 0.8;
			case EXPECTATION -> 0.7;
			case FOLLOW_UP -> 0.6;
			case RUMOR -> 0.4;
		};
		return clamp01(impact * tier * stage);
	}

	private static double surpriseScore(NfmEventView event) {
		if (event.expectedValue() == null || event.actualValue() == null) {
			return 0.0; // surprise is only meaningful when an expectation exists
		}
		BigDecimal expected = event.expectedValue();
		BigDecimal diff = event.actualValue().subtract(expected).abs();
		BigDecimal denom = expected.abs().max(new BigDecimal("0.00000001"));
		double relative = diff.divide(denom, 8, RoundingMode.HALF_UP).doubleValue();
		return clamp01(relative / 0.05); // 5% relative surprise saturates
	}

	private static double relevanceScore(NewsImpact relevance) {
		return relevance == null ? 0.0 : relevance.ordinal() / 3.0;
	}

	private static double oiScore(DerivativesSnapshot d, BigDecimal priceReaction) {
		if (d == null || !d.openInterestAvailable() || d.openInterestChangePct() == null || priceReaction == null) {
			return 0.5; // neutral fallback — never assumed zero (spec §47)
		}
		boolean sameSign = d.openInterestChangePct().signum() == priceReaction.signum();
		return sameSign ? 1.0 : 0.35;
	}

	private static double fundingScore(FundingState funding, NfmAction direction) {
		if (funding == null) return 0.6;
		return switch (funding) {
			case NORMAL -> 1.0;
			case UNKNOWN -> 0.6;
			case ELEVATED_LONG -> direction == NfmAction.LONG ? 0.25 : 0.6;
			case ELEVATED_SHORT -> direction == NfmAction.SHORT ? 0.25 : 0.6;
			case EXTREME_LONG -> direction == NfmAction.LONG ? 0.0 : 0.4;
			case EXTREME_SHORT -> direction == NfmAction.SHORT ? 0.0 : 0.4;
		};
	}

	private static double liquidationScore(LiquidationState liquidation, NfmAction direction) {
		if (liquidation == null || liquidation == LiquidationState.UNKNOWN) return 0.5;
		return switch (liquidation) {
			case NORMAL -> 0.5;
			case SHORT_SQUEEZE -> direction == NfmAction.LONG ? 1.0 : 0.2;
			case LONG_SQUEEZE -> direction == NfmAction.SHORT ? 1.0 : 0.2;
			case EXTREME_LIQUIDATION -> 0.3;
			case UNKNOWN -> 0.5;
		};
	}

	private static double regimeScore(MarketRegime regime, NfmAction direction) {
		if (regime == null) return 0.5;
		if (direction == NfmAction.LONG) {
			return switch (regime) {
				case BULLISH -> 1.0;
				case NEUTRAL -> 0.5;
				case BEARISH -> 0.0;
			};
		}
		return switch (regime) {
			case BEARISH -> 1.0;
			case NEUTRAL -> 0.5;
			case BULLISH -> 0.0;
		};
	}

	// ── Derivatives classification (spec §14–§15) ───────────────────────────

	static FundingState classifyFunding(BigDecimal fundingRate, NfmFuturesConfig cfg) {
		if (fundingRate == null) return FundingState.UNKNOWN;
		double rate = fundingRate.doubleValue();
		double extreme = cfg.getExtremeFundingRate();
		double elevated = cfg.getElevatedFundingRate();
		if (rate >= extreme) return FundingState.EXTREME_LONG;
		if (rate <= -extreme) return FundingState.EXTREME_SHORT;
		if (rate >= elevated) return FundingState.ELEVATED_LONG;
		if (rate <= -elevated) return FundingState.ELEVATED_SHORT;
		return FundingState.NORMAL;
	}

	static LiquidationState classifyLiquidation(DerivativesSnapshot d, NfmFuturesConfig cfg) {
		if (d == null || !d.liquidationAvailable()) return LiquidationState.UNKNOWN;
		BigDecimal longVol = d.longLiquidationVolume() == null ? BigDecimal.ZERO : d.longLiquidationVolume();
		BigDecimal shortVol = d.shortLiquidationVolume() == null ? BigDecimal.ZERO : d.shortLiquidationVolume();
		BigDecimal total = longVol.add(shortVol);
		if (total.signum() == 0) return LiquidationState.NORMAL;
		BigDecimal imbalance = longVol.subtract(shortVol).abs()
				.divide(total, 6, RoundingMode.HALF_UP);
		boolean dominantLong = longVol.compareTo(shortVol) >= 0;
		if (imbalance.doubleValue() >= 0.85) return LiquidationState.EXTREME_LIQUIDATION;
		if (imbalance.doubleValue() < 0.3) return LiquidationState.NORMAL;
		return dominantLong ? LiquidationState.LONG_SQUEEZE : LiquidationState.SHORT_SQUEEZE;
	}

	// ── Helpers ─────────────────────────────────────────────────────────────

	private static NfmAssessment rejected(NfmAction action, int score, BigDecimal priceReaction,
			BigDecimal preEvent, BigDecimal volumeMultiplier, DerivativesSnapshot d, FundingState funding,
			LiquidationState liquidation, String reason, String setupId) {
		return new NfmAssessment(false, action, grade(score, NfmFuturesConfig.defaults()), score,
				null, null, null, null, null, null,
				priceReaction, preEvent, volumeMultiplier, d == null ? null : d.openInterestChangePct(),
				funding, liquidation, reason, setupId);
	}

	private static String setupId(NfmEventView event, String symbol, NfmAction action, NfmFuturesConfig cfg) {
		UUID eventId = event == null ? null : event.eventId();
		Object stage = event == null ? null : event.eventStage();
		return "NFM|" + eventId + "|" + symbol + "|" + action + "|" + stage + "|" + cfg.getConfigVersion();
	}

	private static double closeAt(List<KlineResponse> candles, int index, double fallback) {
		if (candles == null || candles.isEmpty()) return fallback;
		int i = Math.max(0, Math.min(index, candles.size() - 1));
		KlineResponse c = candles.get(i);
		BigDecimal close = c.close();
		return close == null ? fallback : close.doubleValue();
	}

	private static BigDecimal pct(double from, double to) {
		if (from == 0) return null;
		return bd6((to - from) / from * 100.0);
	}

	private static double nz(BigDecimal v) {
		return v == null ? 0.0 : v.doubleValue();
	}

	private static double clamp01(double v) {
		return clamp(v, 0.0, 1.0);
	}

	private static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	private static BigDecimal bd8(double v) {
		return BigDecimal.valueOf(v).setScale(8, RoundingMode.HALF_UP);
	}

	private static BigDecimal bd6(double v) {
		return BigDecimal.valueOf(v).setScale(6, RoundingMode.HALF_UP);
	}

	private static BigDecimal bd4(double v) {
		return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
	}

	private static String fmt(BigDecimal v) {
		return v == null ? "n/a" : v.stripTrailingZeros().toPlainString();
	}
}
