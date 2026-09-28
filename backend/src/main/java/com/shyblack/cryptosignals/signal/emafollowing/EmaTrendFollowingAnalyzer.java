package com.shyblack.cryptosignals.signal.emafollowing;

import com.shyblack.cryptosignals.dto.market.KlineResponse;
import com.shyblack.cryptosignals.dto.strategy.EMATrendFollowingConfig;
import com.shyblack.cryptosignals.entity.enums.MarketRegime;
import com.shyblack.cryptosignals.signal.IndicatorEngine;
import com.shyblack.cryptosignals.signal.SignalConstants;
import java.util.List;

/**
 * Deterministic EMA_TREND_FOLLOWING evaluator (SPOT, LONG-only).
 *
 * Works on CLOSED candles only. HTF establishes the trend regime; the entry
 * timeframe requires a fresh EMA20/EMA50 bullish transition, price confirmation,
 * EMA separation and (optionally) RSI/volume/ATR confirmation, then a normalized
 * 0–100 score and an ATR-based stop with R-multiple targets.
 *
 * No I/O, no mutable state — the single implementation shared by the live signal
 * service and the backtest strategy.
 */
public final class EmaTrendFollowingAnalyzer {

	private EmaTrendFollowingAnalyzer() {}

	public static EmaTrendFollowingAssessment analyze(
			String symbol,
			List<KlineResponse> htfCandles,
			List<KlineResponse> entryCandles,
			EMATrendFollowingConfig cfg) {

		int htfMin = cfg.getHtfSlowEma() + cfg.getTrendSlopeLookback() + 5;
		int entryMin = Math.max(Math.max(cfg.getEntrySlowEma(), cfg.getRsiPeriod() + 1),
				Math.max(cfg.getAtrPeriod() + 1, cfg.getVolumePeriod())) + 5;

		if (htfCandles == null || entryCandles == null
				|| htfCandles.size() < htfMin || entryCandles.size() < entryMin) {
			return fail(null, EmaTrendFollowingState.INSUFFICIENT_DATA,
					EmaTrendFollowingReason.INSUFFICIENT_DATA, 0, MarketRegime.NEUTRAL,
					"Insufficient closed-candle history");
		}

		// ── HTF trend regime ────────────────────────────────────────────────
		IndicatorEngine.Params htfParams = new IndicatorEngine.Params(
				cfg.getHtfFastEma(), cfg.getHtfFastEma(), cfg.getHtfSlowEma(),
				cfg.getRsiPeriod(), cfg.getAtrPeriod(), SignalConstants.ADX_PERIOD, cfg.getVolumePeriod());
		IndicatorEngine.Indicators h = IndicatorEngine.compute(htfCandles, htfParams);
		int m = htfCandles.size();
		double hClose = h.lastClose();
		double hFast = h.lastFastEma();
		double hSlow = h.lastSlowEma();
		double slopePrev = h.fastEma()[Math.max(0, m - 1 - cfg.getTrendSlopeLookback())];
		boolean slopeUp = hFast > slopePrev;

		MarketRegime regime;
		if (hClose > hFast && hFast > hSlow) regime = MarketRegime.BULLISH;
		else if (hClose < hSlow && hFast < hSlow) regime = MarketRegime.BEARISH;
		else regime = MarketRegime.NEUTRAL;

		if (!(hClose > hFast && hFast > hSlow)) {
			return fail(null, EmaTrendFollowingState.NO_TREND,
					EmaTrendFollowingReason.NO_BULLISH_TREND, 0, regime, "HTF not bullish (close>EMA-fast>EMA-slow)");
		}
		if (!slopeUp) {
			return fail(null, EmaTrendFollowingState.NO_TREND,
					EmaTrendFollowingReason.SLOPE_NOT_POSITIVE, 0, regime, "HTF EMA-fast slope not positive");
		}

		// ── Entry structure ─────────────────────────────────────────────────
		IndicatorEngine.Params entryParams = new IndicatorEngine.Params(
				cfg.getEntryFastEma(), cfg.getEntrySlowEma(), cfg.getEntrySlowEma(),
				cfg.getRsiPeriod(), cfg.getAtrPeriod(), SignalConstants.ADX_PERIOD, cfg.getVolumePeriod());
		IndicatorEngine.Indicators e = IndicatorEngine.compute(entryCandles, entryParams);
		int n = entryCandles.size();
		double[] ema20 = e.fastEma();
		double[] ema50 = e.midEma();
		double[] close = e.close();
		double[] vol = e.volume();
		double[] volMa = e.volumeMa20();
		double[] rsi = e.rsi();
		double[] atr = e.atr();

		double ma20Now = ema20[n - 1];
		double ma50Now = ema50[n - 1];
		double ma20Prev = ema20[n - 2];
		double ma50Prev = ema50[n - 2];

		boolean transition = ma20Prev <= ma50Prev && ma20Now > ma50Now;
		if (!transition) {
			return fail(null, EmaTrendFollowingState.NO_TRANSITION,
					EmaTrendFollowingReason.NO_EMA_TRANSITION, 0, regime,
					"No fresh EMA-fast/EMA-slow bullish transition on the entry timeframe");
		}

		double price = close[n - 1];
		boolean aboveFast = price > ma20Now;
		boolean aboveSlow = price > ma50Now;
		boolean higherClose = price > close[n - 2];
		if (!(aboveFast && aboveSlow && higherClose)) {
			return fail(null, EmaTrendFollowingState.WAITING_CONFIRMATION,
					EmaTrendFollowingReason.PRICE_NOT_CONFIRMED, 0, regime,
					"Price not confirmed (close>EMA-fast, close>EMA-slow, rising close)");
		}

		double separationPct = Math.abs(ma20Now - ma50Now) / (price == 0 ? 1 : price) * 100.0;
		if (separationPct < cfg.getMinimumEmaSeparationPct()) {
			return fail(null, EmaTrendFollowingState.WAITING_CONFIRMATION,
					EmaTrendFollowingReason.WEAK_EMA_SEPARATION, 0, regime,
					String.format("EMA separation %.3f%% < %.3f%%", separationPct, cfg.getMinimumEmaSeparationPct()));
		}

		double rsiNow = rsi[n - 1];
		if (cfg.isRsiFilterEnabled()
				&& (rsiNow < cfg.getMinimumRsiForLong() || rsiNow > cfg.getMaximumRsiForLong())) {
			return fail(null, EmaTrendFollowingState.WAITING_CONFIRMATION,
					EmaTrendFollowingReason.RSI_OUT_OF_RANGE, 0, regime,
					String.format("RSI %.1f outside [%.0f,%.0f]",
							rsiNow, cfg.getMinimumRsiForLong(), cfg.getMaximumRsiForLong()));
		}

		double volRatio = vol[n - 1] / Math.max(volMa[n - 1], 1e-9);
		if (cfg.isVolumeFilterEnabled() && volRatio < cfg.getMinimumVolumeRatio()) {
			return fail(null, EmaTrendFollowingState.WAITING_CONFIRMATION,
					EmaTrendFollowingReason.VOLUME_TOO_LOW, 0, regime,
					String.format("Volume %.2fx < %.2fx", volRatio, cfg.getMinimumVolumeRatio()));
		}

		double atrNow = atr[n - 1];
		double atrPct = atrNow / (price == 0 ? 1 : price) * 100.0;
		if (cfg.isAtrFilterEnabled() && atrPct < cfg.getMinimumAtrPct()) {
			return fail(null, EmaTrendFollowingState.WAITING_CONFIRMATION,
					EmaTrendFollowingReason.ATR_TOO_LOW, 0, regime,
					String.format("ATR %.3f%% < %.3f%%", atrPct, cfg.getMinimumAtrPct()));
		}

		// ── Entry / stop / targets ──────────────────────────────────────────
		double entryPrice = price;
		double stopLoss = entryPrice - atrNow * cfg.getSlAtrBuffer();
		double risk = entryPrice - stopLoss;
		if (risk <= 0) {
			return fail(null, EmaTrendFollowingState.SIGNAL_READY,
					EmaTrendFollowingReason.INVALID_STOP, 0, regime, "Stop loss is not below entry");
		}
		double tp1 = entryPrice + risk * cfg.getTp1R();
		double tp2 = entryPrice + risk * cfg.getTp2R();
		double tp3 = entryPrice + risk * cfg.getTp3R();
		double rr = (tp1 - entryPrice) / risk;
		if (rr < cfg.getMinRR()) {
			return fail(null, EmaTrendFollowingState.SIGNAL_READY,
					EmaTrendFollowingReason.INSUFFICIENT_RR, 0, regime,
					String.format("R:R %.2f < minimum %.2f", rr, cfg.getMinRR()));
		}

		// ── Score ───────────────────────────────────────────────────────────
		ScoreOutcome outcome = score(cfg, aboveFast, aboveSlow, higherClose, slopeUp,
				hFast, hSlow, hClose, separationPct, rsiNow, volRatio, atrPct);
		if (!outcome.configured()) {
			return fail(null, EmaTrendFollowingState.SIGNAL_READY,
					EmaTrendFollowingReason.SCORE_NOT_CONFIGURED, 0, regime,
					"No active scoring criteria (all active weights are zero)");
		}
		int total = outcome.breakdown().total();
		if (total < cfg.getMinimumScore()) {
			return new EmaTrendFollowingAssessment(null, EmaTrendFollowingState.SIGNAL_READY, false,
					EmaTrendFollowingReason.SCORE_BELOW_MINIMUM, total, outcome.breakdown(),
					String.format("Score %d below minimum %d", total, cfg.getMinimumScore()),
					regime, null, null, null, null, null, null, ma20Now, ma50Now, hFast, hSlow,
					rsiNow, atrPct, volRatio);
		}

		String setupId = "ETF:" + entryCandles.get(n - 1).openTime() + ":" + entryCandles.get(n - 2).openTime();
		String explanation = explain(symbol, cfg, regime, hFast, hSlow, ma20Now, ma50Now, entryPrice,
				stopLoss, tp1, tp2, tp3, rr, rsiNow, volRatio, atrPct, separationPct, total, outcome.breakdown());

		return new EmaTrendFollowingAssessment(setupId, EmaTrendFollowingState.SIGNAL_READY, true,
				EmaTrendFollowingReason.NONE, total, outcome.breakdown(), explanation, regime,
				entryPrice, stopLoss, tp1, tp2, tp3, rr, ma20Now, ma50Now, hFast, hSlow,
				rsiNow, atrPct, volRatio);
	}

	// ── Scoring ─────────────────────────────────────────────────────────────

	private record ScoreOutcome(boolean configured, EmaTrendFollowingAssessment.ScoreBreakdown breakdown) {}

	private static ScoreOutcome score(
			EMATrendFollowingConfig cfg, boolean aboveFast, boolean aboveSlow, boolean higherClose,
			boolean slopeUp, double hFast, double hSlow, double hClose, double separationPct,
			double rsiNow, double volRatio, double atrPct) {

		int[] weights = {
				cfg.getWeightTrendAlignment(), cfg.getWeightEmaTransition(), cfg.getWeightPriceConfirmation(),
				cfg.getWeightMomentum(), cfg.getWeightVolume(), cfg.getWeightVolatility()
		};
		boolean[] active = {
				true, true, true, cfg.isRsiFilterEnabled(), cfg.isVolumeFilterEnabled(), cfg.isAtrFilterEnabled()
		};

		double trendFrac = 0;
		if (hFast > hSlow) trendFrac += 0.4;
		if (hClose > hFast) trendFrac += 0.3;
		if (slopeUp) trendFrac += 0.3;

		double separationTarget = Math.max(cfg.getMinimumEmaSeparationPct() * 2.0, 1e-9);
		double transitionFrac = 0.7 + 0.3 * clamp(separationPct / separationTarget, 0, 1); // transition guaranteed here

		double priceFrac = (aboveFast ? 0.4 : 0) + (aboveSlow ? 0.3 : 0) + (higherClose ? 0.3 : 0);

		double momentumFrac = clamp((rsiNow - cfg.getMinimumRsiForLong())
				/ Math.max(cfg.getMaximumRsiForLong() - cfg.getMinimumRsiForLong(), 1e-9), 0, 1);

		double volumeFrac = clamp((volRatio - 1.0) / 0.5, 0, 1);

		double volatilityFrac = clamp(atrPct / Math.max(cfg.getMinimumAtrPct() * 2.0, 1e-9), 0, 1);

		double[] fracs = { trendFrac, transitionFrac, priceFrac, momentumFrac, volumeFrac, volatilityFrac };

		int activeWeight = 0;
		for (int i = 0; i < weights.length; i++) {
			if (active[i]) activeWeight += Math.max(0, weights[i]);
		}
		if (activeWeight <= 0) {
			return new ScoreOutcome(false, new EmaTrendFollowingAssessment.ScoreBreakdown(0, 0, 0, 0, 0, 0));
		}

		double[] raw = new double[weights.length];
		double earned = 0;
		for (int i = 0; i < weights.length; i++) {
			raw[i] = active[i] ? 100.0 * Math.max(0, weights[i]) * clamp(fracs[i], 0, 1) / activeWeight : 0;
			earned += raw[i];
		}
		int target = (int) Math.round(earned);
		target = Math.max(0, Math.min(100, target));

		int[] points = new int[weights.length];
		int assigned = 0;
		for (int i = 0; i < weights.length; i++) {
			points[i] = (int) Math.floor(raw[i]);
			assigned += points[i];
		}
		int remaining = target - assigned;
		while (remaining > 0) {
			int best = -1;
			double bestRem = 0;
			for (int i = 0; i < weights.length; i++) {
				double rem = raw[i] - Math.floor(raw[i]);
				if (rem > bestRem) { bestRem = rem; best = i; }
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

		return new ScoreOutcome(true, new EmaTrendFollowingAssessment.ScoreBreakdown(
				points[0], points[1], points[2], points[3], points[4], points[5]));
	}

	private static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	private static String explain(
			String symbol, EMATrendFollowingConfig cfg, MarketRegime regime,
			double hFast, double hSlow, double ema20, double ema50,
			double entry, double sl, double tp1, double tp2, double tp3, double rr,
			double rsi, double volRatio, double atrPct, double separationPct,
			int total, EmaTrendFollowingAssessment.ScoreBreakdown b) {

		String name = symbol == null ? "" : symbol + " ";
		double riskPct = (entry - sl) / entry * 100.0;
		return String.format(
				"%sEMA_TREND_FOLLOWING BUY%n"
				+ "HTF Trend: %s (EMA-fast %.4f > EMA-slow %.4f, slope%s)%n"
				+ "Entry EMA: %.4f > %.4f (separation %.3f%%)%n"
				+ "Momentum: RSI %.1f | Volume: %.2fx | ATR: %.3f%%%n"
				+ "Risk: SL=%.2f%% TP1=%.2f%% TP2=%.2f%% TP3=%.2f%%%n"
				+ "RR: %.2fR%n"
				+ "Score: %d/100 (trend=%d transition=%d price=%d momentum=%d volume=%d volatility=%d)",
				name, regime, hFast, hSlow, cfg.isRsiFilterEnabled() ? " (RSI-filtered)" : "",
				ema20, ema50, separationPct,
				rsi, volRatio, atrPct,
				riskPct, riskPct * cfg.getTp1R(), riskPct * cfg.getTp2R(), riskPct * cfg.getTp3R(),
				rr, total, b.trendAlignment(), b.emaTransition(), b.priceConfirmation(),
				b.momentum(), b.volume(), b.volatility());
	}

	private static EmaTrendFollowingAssessment fail(String setupId, EmaTrendFollowingState state,
			EmaTrendFollowingReason reason, int score, MarketRegime regime, String explanation) {
		return new EmaTrendFollowingAssessment(setupId, state, false, reason, score, null,
				explanation, regime, null, null, null, null, null, null, null, null, null, null,
				null, null, null);
	}
}
