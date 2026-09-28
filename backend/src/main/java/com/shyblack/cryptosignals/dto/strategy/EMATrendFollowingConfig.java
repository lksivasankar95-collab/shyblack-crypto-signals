package com.shyblack.cryptosignals.dto.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Full configuration for the EMA_TREND_FOLLOWING strategy (SPOT, LONG-only).
 *
 * A mutable bean so partial JSON persisted in {@code TradingStrategy.configJson}
 * deserializes with sensible defaults for any omitted field. Every threshold
 * lives here — no magic numbers inside the analyzer.
 */
@Getter
@Setter
@NoArgsConstructor
public class EMATrendFollowingConfig {

	/** Engine discriminator stored on TradingStrategy.engineKey. */
	public static final String ENGINE_KEY = "EMA_TREND_FOLLOWING";

	// ── Timeframes ──────────────────────────────────────────────────────────
	private String htfTimeframe = "1H";
	private String entryTimeframe = "15M";

	// ── Higher-timeframe trend ──────────────────────────────────────────────
	private int htfFastEma = 50;
	private int htfSlowEma = 200;
	private int trendSlopeLookback = 5;

	// ── Entry structure ─────────────────────────────────────────────────────
	private int entryFastEma = 20;
	private int entrySlowEma = 50;
	private double minimumEmaSeparationPct = 0.10;

	// ── Momentum (RSI) ──────────────────────────────────────────────────────
	private boolean rsiFilterEnabled = true;
	private int rsiPeriod = 14;
	private double minimumRsiForLong = 50.0;
	private double maximumRsiForLong = 70.0;

	// ── Volume ──────────────────────────────────────────────────────────────
	private boolean volumeFilterEnabled = true;
	private int volumePeriod = 20;
	private double minimumVolumeRatio = 1.20;

	// ── Volatility (ATR) ────────────────────────────────────────────────────
	private boolean atrFilterEnabled = true;
	private int atrPeriod = 14;
	private double minimumAtrPct = 0.30;

	// ── Risk / targets ──────────────────────────────────────────────────────
	private double slAtrBuffer = 1.5;
	private double minRR = 1.5;
	private double tp1R = 1.5;
	private double tp2R = 2.5;
	private double tp3R = 4.0;

	// ── Lifecycle ───────────────────────────────────────────────────────────
	private int cooldownCandles = 4;

	// ── Scoring ─────────────────────────────────────────────────────────────
	private int minimumScore = 70;
	private int weightTrendAlignment = 30;
	private int weightEmaTransition = 20;
	private int weightPriceConfirmation = 20;
	private int weightMomentum = 15;
	private int weightVolume = 10;
	private int weightVolatility = 5;

	// ── Data window ─────────────────────────────────────────────────────────
	private int htfCandleCount = 300;
	private int entryCandleCount = 200;

	public static EMATrendFollowingConfig defaults() {
		return new EMATrendFollowingConfig();
	}

	/**
	 * Parse a JSON object, filling any missing field with its default. Accepts
	 * either a bare config object or a full strategy config nesting it under
	 * {@code "emaTrendFollowing"}.
	 */
	public static EMATrendFollowingConfig fromJson(ObjectMapper mapper, String json) {
		if (json == null || json.isBlank()) return defaults();
		try {
			JsonNode node = mapper.readTree(json);
			if (node != null && node.has("emaTrendFollowing") && node.get("emaTrendFollowing").isObject()) {
				node = node.get("emaTrendFollowing");
			}
			return mapper.treeToValue(node, EMATrendFollowingConfig.class);
		} catch (Exception ex) {
			return defaults();
		}
	}
}
