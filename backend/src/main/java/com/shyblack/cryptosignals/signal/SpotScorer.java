package com.shyblack.cryptosignals.signal;

import com.shyblack.cryptosignals.entity.enums.MarketRegime;

public class SpotScorer {

    /** Score market regime (BTC context). Max 10 points. */
    public static int scoreMarketRegime(MarketRegime regime) {
        return switch (regime) {
            case BULLISH -> 10;
            case NEUTRAL -> 4;
            case BEARISH -> 0;
        };
    }

    /**
     * Score 4H trend for the coin. Max 20 points.
     * Penalty applied for pump-chasing (24h change% too high).
     */
    public static int scoreTrend4h(IndicatorEngine.Indicators ind, double change24hPct) {
        double price = ind.lastClose();
        int score;
        if (price > ind.lastEma200() && ind.lastEma50() > ind.lastEma200() && price > ind.lastEma20()) {
            score = 20;
        } else if (price > ind.lastEma200() && ind.lastEma50() > ind.lastEma200()) {
            score = 15;
        } else if (price > ind.lastEma200()) {
            score = 8;
        } else if (price > ind.lastEma50()) {
            score = 4;
        } else {
            score = 0;
        }
        if (change24hPct > SignalConstants.EXTREME_PUMP_PCT) score = Math.max(0, score - 8);
        else if (change24hPct > SignalConstants.PUMP_THRESHOLD_PCT) score = Math.max(0, score - 4);
        return score;
    }

    /**
     * Score 1H confirmation. Max 20 points.
     * Checks 4 binary conditions; penalty for extreme RSI overbought.
     */
    public static int scoreConfirmation1h(IndicatorEngine.Indicators ind) {
        int conditions = 0;
        double price = ind.lastClose();
        if (ind.lastEma20() > ind.lastEma50()) conditions++;
        if (price > ind.lastEma20()) conditions++;
        if (ind.lastRsi() > SignalConstants.RSI_NEUTRAL) conditions++;
        if (ind.lastMacdHist() > 0) conditions++;

        int score = switch (conditions) {
            case 4 -> 20;
            case 3 -> 14;
            case 2 -> 8;
            default -> 2;
        };
        if (ind.lastRsi() > SignalConstants.RSI_EXTREME_OB) score = Math.max(0, score - 5);
        return score;
    }

    /**
     * Score 15M entry setup. Max 20 points.
     * Checks: higher lows (structure), volume expanding, MACD bullish, price > EMA20.
     */
    public static int scoreEntrySetup15m(
            IndicatorEngine.Indicators ind,
            StructureAnalyzer.StructureSummary structure) {
        int conditions = 0;
        if (structure.higherLows()) conditions++;
        if (ind.lastVolume() > ind.lastVolumeMa() * SignalConstants.VOLUME_MODERATE) conditions++;
        if (ind.lastMacdHist() > 0 && ind.lastMacdHist() > ind.prevMacdHist()) conditions++;
        if (ind.lastClose() > ind.lastEma20()) conditions++;

        return switch (conditions) {
            case 4 -> 20;
            case 3 -> 14;
            case 2 -> 8;
            case 1 -> 4;
            default -> 0;
        };
    }

    /**
     * Score volume / accumulation. Max 10 points.
     * Volume spike on extreme pump is penalized.
     */
    public static int scoreVolume(IndicatorEngine.Indicators ind, double change24hPct) {
        double ratio = ind.lastVolume() / Math.max(ind.lastVolumeMa(), 1);
        int base;
        if (ratio >= SignalConstants.VOLUME_HIGH)     base = 10;
        else if (ratio >= SignalConstants.VOLUME_MODERATE) base = 6;
        else if (ratio >= 1.0)                       base = 4;
        else                                         base = 0;

        if (change24hPct > SignalConstants.EXTREME_PUMP_PCT && base == 10) base = 4;
        return base;
    }

    /**
     * Score structure / support-resistance. Max 10 points.
     */
    public static int scoreStructure(
            StructureAnalyzer.StructureSummary structure,
            double currentPrice) {
        double nearestRes = structure.nearestResistance(currentPrice);
        double nearestSup = structure.nearestSupport(currentPrice);
        double roomToRes   = (nearestRes - currentPrice) / currentPrice;
        double distFromSup = (currentPrice - nearestSup) / currentPrice;

        if (roomToRes < 0.01)  return 1;
        if (distFromSup > 0.15) return 2;

        if (structure.higherLows() && structure.higherHighs()) return 10;
        if (structure.higherLows()) return 6;
        return 3;
    }

    /**
     * Score risk/reward. Max 10 points.
     */
    public static int scoreRiskReward(double rr) {
        if (rr >= SignalConstants.EXCELLENT_RR) return 10;
        if (rr >= SignalConstants.GOOD_RR)      return 7;
        if (rr >= SignalConstants.MIN_RR)       return 4;
        return 0;
    }
}
