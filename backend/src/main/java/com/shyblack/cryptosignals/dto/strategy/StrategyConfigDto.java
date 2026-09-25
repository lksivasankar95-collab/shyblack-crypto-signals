package com.shyblack.cryptosignals.dto.strategy;

public record StrategyConfigDto(
        IndicatorConfig indicators,
        ScoringConfig scoring,
        FilterConfig filters,
        EntryConfig entry,
        FuturesConfig futures // null for SPOT
) {
    public record IndicatorConfig(
            int emaFast, int emaMid, int emaSlow,
            int rsiPeriod, double rsiOversold, double rsiNeutral,
            double rsiOverbought, double rsiExtremeOb,
            int macdFast, int macdSlow, int macdSignalPeriod,
            int atrPeriod, int adxPeriod, int volumeMaPeriod
    ) {
        public static IndicatorConfig defaults() {
            return new IndicatorConfig(20, 50, 200, 14, 30.0, 50.0, 70.0, 80.0, 12, 26, 9, 14, 14, 20);
        }
    }

    public record ScoringConfig(
            int strongBuyThreshold, int buyThreshold, int watchThreshold, double minRiskReward
    ) {
        public static ScoringConfig defaults() {
            return new ScoringConfig(85, 75, 65, 1.5);
        }
    }

    public record FilterConfig(
            double minVolumeUsdt, int signalCooldownHours, boolean skipBearishRegime
    ) {
        public static FilterConfig defaults() {
            return new FilterConfig(5_000_000, 4, true);
        }
    }

    public record EntryConfig(
            double atrSlBuffer, double tp1RMultiple, double tp2RMultiple, double tp3RMultiple
    ) {
        public static EntryConfig defaults() {
            return new EntryConfig(0.5, 1.5, 2.5, 4.0);
        }
    }

    public record FuturesConfig(
            int defaultLeverage, boolean allowLong, boolean allowShort
    ) {
        public static FuturesConfig defaults() {
            return new FuturesConfig(3, true, true);
        }
    }

    public static StrategyConfigDto spotDefaults() {
        return new StrategyConfigDto(
                IndicatorConfig.defaults(), ScoringConfig.defaults(),
                FilterConfig.defaults(), EntryConfig.defaults(), null);
    }

    public static StrategyConfigDto futuresDefaults() {
        return new StrategyConfigDto(
                IndicatorConfig.defaults(), ScoringConfig.defaults(),
                FilterConfig.defaults(), EntryConfig.defaults(), FuturesConfig.defaults());
    }
}
