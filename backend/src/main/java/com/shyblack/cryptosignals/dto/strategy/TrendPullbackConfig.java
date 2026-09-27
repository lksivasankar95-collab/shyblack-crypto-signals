package com.shyblack.cryptosignals.dto.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Full configuration for the TREND_PULLBACK strategy.
 *
 * A mutable bean (rather than a record) so that partial JSON persisted in
 * {@code TradingStrategy.configJson} still deserializes with sensible
 * defaults for any omitted field. Every threshold used by the strategy lives
 * here — no magic numbers exist inside the analyzer.
 */
@Getter
@Setter
@NoArgsConstructor
public class TrendPullbackConfig {

    /** Engine discriminator stored on TradingStrategy.engineKey. */
    public static final String ENGINE_KEY = "TREND_PULLBACK";

    // ── Timeframes ──────────────────────────────────────────────────────────
    private String htf = "1H";
    private String entryTimeframe = "15M";

    // ── Higher-timeframe trend ──────────────────────────────────────────────
    private int emaFastHtf = 50;
    private int emaSlowHtf = 200;
    private int adxPeriod = 14;
    private double minAdx = 20.0;
    private boolean requirePositiveSlope = true;
    private int slopeLookback = 1;

    // ── Pullback ────────────────────────────────────────────────────────────
    private int pullbackEma = 20;
    private int entryEma = 50;
    /** {@code EMA20} or {@code EMA20_TO_EMA50}. */
    private String zoneMode = "EMA20_TO_EMA50";
    private double maxPullbackDistanceAtr = 1.5;

    // ── Momentum ────────────────────────────────────────────────────────────
    private int rsiPeriod = 14;
    private double rsiMin = 40.0;
    private double rsiMax = 60.0;
    private boolean requireRecovery = true;

    // ── Volume ──────────────────────────────────────────────────────────────
    private boolean volumeFilterEnabled = true;
    private int volumeSmaPeriod = 20;
    private double minVolumeMultiplier = 1.2;

    // ── Risk ────────────────────────────────────────────────────────────────
    private int atrPeriod = 14;
    private double slAtrBuffer = 0.2;
    private double maxSlAtr = 2.0;
    private double minRR = 1.5;

    // ── Take profit ─────────────────────────────────────────────────────────
    private double tp1R = 1.0;
    private double tp2R = 2.0;
    private double tp3R = 3.0;

    // ── Lifecycle ───────────────────────────────────────────────────────────
    private int maxSetupCandles = 12;
    private int cooldownCandles = 4;

    // ── Confirmation ────────────────────────────────────────────────────────
    private boolean candleConfirmationEnabled = true;
    /** Minimum lower-wick / total-range ratio; 0 disables the check. */
    private double minLowerWickRatio = 0.0;
    private int swingLookback = 2;

    // ── Invalidation ────────────────────────────────────────────────────────
    private double invalidationBodyAtr = 1.0;
    private double invalidationVolumeMult = 1.5;

    // ── Scoring ─────────────────────────────────────────────────────────────
    private int minimumScore = 70;
    private int weightTrend = 20;
    private int weightAdx = 15;
    private int weightPullback = 20;
    private int weightRsi = 10;
    private int weightVolume = 10;
    private int weightStructure = 15;
    private int weightRiskReward = 10;

    // ── Data window ─────────────────────────────────────────────────────────
    private int htfCandleCount = 300;
    private int entryCandleCount = 200;

    public static TrendPullbackConfig defaults() {
        return new TrendPullbackConfig();
    }

    /**
     * Parse a JSON object, filling any missing field with its default.
     * Accepts either a bare config object or a full strategy config that
     * nests it under {@code "pullback"}.
     */
    public static TrendPullbackConfig fromJson(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) return defaults();
        try {
            JsonNode node = mapper.readTree(json);
            if (node != null && node.has("pullback") && node.get("pullback").isObject()) {
                node = node.get("pullback");
            }
            return mapper.treeToValue(node, TrendPullbackConfig.class);
        } catch (Exception ex) {
            return defaults();
        }
    }
}
