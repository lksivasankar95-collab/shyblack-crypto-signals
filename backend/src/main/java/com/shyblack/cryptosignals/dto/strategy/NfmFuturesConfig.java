package com.shyblack.cryptosignals.dto.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Full configuration for the NFM_FUTURES strategy (Futures-only, event-driven).
 *
 * <p>Every weight, threshold and window is configurable so the backtest module
 * can sweep parameters (spec §18, §29, §44). No magic numbers live in the
 * engine.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class NfmFuturesConfig {

	/** Engine discriminator stored on TradingStrategy.engineKey. */
	public static final String ENGINE_KEY = "NFM_FUTURES";

	/** Configuration version stamped onto every NFM signal (spec §44). */
	private String configVersion = "NFM_FUTURES_V1";

	// ── Timeframes / windows ────────────────────────────────────────────────
	private String reactionTimeframe = "5M";
	private int reactionCandleCount = 200;
	private String contextTimeframe = "1H";
	private int contextCandleCount = 120;
	private int atrPeriod = 14;
	private int volumeMaPeriod = 20;

	// ── Trigger thresholds ──────────────────────────────────────────────────
	private int minimumScore = 65;
	private String minimumGrade = "C";
	private int gradeAThreshold = 85;
	private int gradeBThreshold = 75;

	/** Minimum absolute post-event move (%) to treat the reaction as directional. */
	private double priceReactionThresholdPct = 0.35;
	/** Minimum volume multiplier (current / volume MA) required for confirmation. */
	private double volumeThreshold = 1.2;
	/** Minimum absolute OI change (%) for OI confirmation to count. */
	private double oiChangeThresholdPct = 1.0;

	/** Funding rate (per interval, decimal) above which longs are crowded. */
	private double elevatedFundingRate = 0.0005;
	private double extremeFundingRate = 0.0010;

	/** Pre-event move (%) in the candidate direction that marks the move as priced in. */
	private double pricedInReturnPct = 8.0;
	private int pricedInScorePenalty = 15;

	// ── Risk / targets ──────────────────────────────────────────────────────
	private double slAtrMultiplier = 1.5;
	private double minStopDistancePct = 0.30;
	private double minRR = 1.5;
	private double tp1R = 1.5;
	private double tp2R = 2.5;
	private double tp3R = 4.0;

	// ── Lifecycle ───────────────────────────────────────────────────────────
	private int eventMaxAgeMinutes = 180;
	private int cooldownMinutes = 60;
	private boolean allowLong = true;
	private boolean allowShort = true;

	// ── Scoring weights (sum to 100) ────────────────────────────────────────
	private int weightEventQuality = 20;
	private int weightSurprise = 10;
	private int weightAssetRelevance = 10;
	private int weightPriceConfirmation = 20;
	private int weightVolume = 10;
	private int weightOpenInterest = 8;
	private int weightFunding = 7;
	private int weightLiquidation = 5;
	private int weightMarketRegime = 10;
	private int weightCrossAsset = 0;

	// ── Filters ─────────────────────────────────────────────────────────────
	/** Empty = all event types allowed. */
	private List<String> allowedEventTypes = new ArrayList<>();
	/** Empty = all symbols allowed. */
	private List<String> allowedSymbols = new ArrayList<>();

	public static NfmFuturesConfig defaults() {
		return new NfmFuturesConfig();
	}

	public boolean eventTypeAllowed(String eventType) {
		if (allowedEventTypes == null || allowedEventTypes.isEmpty()) return true;
		return allowedEventTypes.stream().anyMatch(t -> t != null && t.equalsIgnoreCase(eventType));
	}

	public boolean symbolAllowed(String symbol) {
		if (allowedSymbols == null || allowedSymbols.isEmpty()) return true;
		return allowedSymbols.stream().anyMatch(s -> s != null && s.equalsIgnoreCase(symbol));
	}

	/** Parse a JSON object, filling missing fields with defaults. Accepts a bare block or a nested {@code "nfmFutures"} block. */
	public static NfmFuturesConfig fromJson(ObjectMapper mapper, String json) {
		if (json == null || json.isBlank()) return defaults();
		try {
			JsonNode node = mapper.readTree(json);
			if (node != null && node.has("nfmFutures") && node.get("nfmFutures").isObject()) {
				node = node.get("nfmFutures");
			}
			return mapper.treeToValue(node, NfmFuturesConfig.class);
		} catch (Exception ex) {
			return defaults();
		}
	}
}
