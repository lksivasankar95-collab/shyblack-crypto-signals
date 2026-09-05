package com.shyblack.cryptosignals.signal;

import com.shyblack.cryptosignals.entity.enums.EntryType;

public class EntryCalculator {

    public record EntryPlan(
            double entry,
            double stopLoss,
            double tp1,
            double tp2,
            double tp3,
            double riskReward,
            EntryType entryType,
            boolean valid       // false if R:R < minimum
    ) {}

    public static EntryPlan calculate(
            IndicatorEngine.Indicators ind15m,
            StructureAnalyzer.StructureSummary structure,
            double livePrice) {

        double currentPrice = livePrice > 0 ? livePrice : ind15m.lastClose();
        double nearestSupport    = structure.nearestSupport(currentPrice);
        double nearestResistance = structure.nearestResistance(currentPrice);
        double atr = ind15m.lastAtr();

        // Determine entry type
        EntryType entryType;
        double entry;
        boolean breakoutOccurred = currentPrice > nearestResistance * 0.995;
        boolean recentBreakout   = ind15m.lastClose() > ind15m.lastEma20()
                && ind15m.lastEma20() > ind15m.lastEma50();

        if (breakoutOccurred && ind15m.lastVolume() > ind15m.lastVolumeMa() * 1.2) {
            entryType = EntryType.BREAKOUT;
            entry = currentPrice;
        } else if (recentBreakout && currentPrice < nearestResistance * 0.99) {
            entryType = EntryType.BREAKOUT_RETEST;
            entry = Math.min(currentPrice, nearestSupport * 1.005);
        } else {
            entryType = EntryType.PRE_BREAKOUT;
            entry = currentPrice;
        }

        // Stop loss: below nearest support + ATR buffer, enforce minimum distance
        double stopLoss = nearestSupport - atr * SignalConstants.ATR_SL_BUFFER;
        stopLoss = Math.min(stopLoss, entry * 0.995);

        double risk = entry - stopLoss;
        if (risk <= 0) {
            return new EntryPlan(entry, stopLoss, 0, 0, 0, 0, entryType, false);
        }

        // Targets: prefer structure-based TP1, fall back to R multiples
        double tp1 = nearestResistance > entry
                ? nearestResistance
                : entry + risk * SignalConstants.TP1_R_MULTIPLE;
        double tp2 = entry + risk * SignalConstants.TP2_R_MULTIPLE;
        double tp3 = entry + risk * SignalConstants.TP3_R_MULTIPLE;

        // Ensure ordering
        if (tp1 <= entry) tp1 = entry + risk * SignalConstants.TP1_R_MULTIPLE;
        if (tp2 <= tp1)   tp2 = tp1 + risk;
        if (tp3 <= tp2)   tp3 = tp2 + risk;

        double rr    = (tp1 - entry) / risk;
        boolean valid = rr >= SignalConstants.MIN_RR;

        return new EntryPlan(entry, stopLoss, tp1, tp2, tp3, rr, entryType, valid);
    }
}
