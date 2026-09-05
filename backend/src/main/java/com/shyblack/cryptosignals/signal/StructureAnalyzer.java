package com.shyblack.cryptosignals.signal;

import java.util.ArrayList;
import java.util.List;

public class StructureAnalyzer {

    public record StructureLevel(double price, boolean isResistance) {}

    public record StructureSummary(
            List<StructureLevel> supports,
            List<StructureLevel> resistances,
            boolean higherLows,
            boolean higherHighs
    ) {
        public double nearestSupport(double currentPrice) {
            return supports.stream()
                    .mapToDouble(StructureLevel::price)
                    .filter(p -> p < currentPrice)
                    .max()
                    .orElse(currentPrice * 0.95);
        }

        public double nearestResistance(double currentPrice) {
            return resistances.stream()
                    .mapToDouble(StructureLevel::price)
                    .filter(p -> p > currentPrice)
                    .min()
                    .orElse(currentPrice * 1.05);
        }
    }

    public static StructureSummary analyze(double[] high, double[] low, double[] close, int lookback) {
        int n = high.length;
        List<Double> swingHighs = new ArrayList<>();
        List<Double> swingLows  = new ArrayList<>();
        int window = SignalConstants.SWING_LOOKBACK;

        for (int i = window; i < n - window; i++) {
            boolean isHigh = true;
            boolean isLow  = true;
            for (int j = i - window; j <= i + window; j++) {
                if (j == i) continue;
                if (high[j] >= high[i]) isHigh = false;
                if (low[j]  <= low[i])  isLow  = false;
            }
            if (isHigh) swingHighs.add(high[i]);
            if (isLow)  swingLows.add(low[i]);
        }

        List<StructureLevel> supports    = new ArrayList<>();
        List<StructureLevel> resistances = new ArrayList<>();
        for (double p : swingLows)  supports.add(new StructureLevel(p, false));
        for (double p : swingHighs) resistances.add(new StructureLevel(p, true));

        boolean higherLows  = false;
        boolean higherHighs = false;

        if (swingLows.size() >= 2) {
            higherLows = swingLows.get(swingLows.size() - 1) > swingLows.get(swingLows.size() - 2);
        }
        if (swingHighs.size() >= 2) {
            higherHighs = swingHighs.get(swingHighs.size() - 1) > swingHighs.get(swingHighs.size() - 2);
        }

        return new StructureSummary(supports, resistances, higherLows, higherHighs);
    }
}
