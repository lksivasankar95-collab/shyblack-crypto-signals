enum StrategyTradingMode { spot, futures }
enum StrategyType { system, user }
enum StrategyStatus { active, inactive }

class TradingStrategy {
  final String id;
  final String name;
  final String? description;
  final StrategyTradingMode tradingMode;
  final StrategyType strategyType;
  final int version;
  final StrategyStatus status;
  final bool deletable;
  final bool editable;
  final StrategyConfig? config;
  final String? engineKey;

  const TradingStrategy({
    required this.id,
    required this.name,
    this.description,
    required this.tradingMode,
    required this.strategyType,
    required this.version,
    required this.status,
    required this.deletable,
    required this.editable,
    this.config,
    this.engineKey,
  });
}

class ActiveStrategyInfo {
  final StrategyTradingMode tradingMode;
  final String? strategyId;
  final String? strategyName;
  final int strategyVersion;
  final bool hasActiveStrategy;

  const ActiveStrategyInfo({
    required this.tradingMode,
    this.strategyId,
    this.strategyName,
    required this.strategyVersion,
    required this.hasActiveStrategy,
  });
}

class StrategyConfig {
  final IndicatorConfig? indicators;
  final ScoringConfig? scoring;
  final FilterConfig? filters;
  final EntryConfig? entry;
  final FuturesStrategyConfig? futures;
  final PullbackConfig? pullback;

  const StrategyConfig({
    this.indicators,
    this.scoring,
    this.filters,
    this.entry,
    this.futures,
    this.pullback,
  });
}

/// Configuration for the TREND_PULLBACK strategy (LONG-only SPOT).
class PullbackConfig {
  final String htf;
  final String entryTimeframe;
  final int emaFastHtf, emaSlowHtf;
  final int adxPeriod;
  final double minAdx;
  final bool requirePositiveSlope;
  final int pullbackEma, entryEma;
  final String zoneMode;
  final double maxPullbackDistanceAtr;
  final int rsiPeriod;
  final double rsiMin, rsiMax;
  final bool requireRecovery;
  final bool volumeFilterEnabled;
  final int volumeSmaPeriod;
  final double minVolumeMultiplier;
  final int atrPeriod;
  final double slAtrBuffer, maxSlAtr, minRR;
  final double tp1R, tp2R, tp3R;
  final int maxSetupCandles, cooldownCandles;
  final bool candleConfirmationEnabled;
  final int minimumScore;

  const PullbackConfig({
    this.htf = '1H',
    this.entryTimeframe = '15M',
    this.emaFastHtf = 50,
    this.emaSlowHtf = 200,
    this.adxPeriod = 14,
    this.minAdx = 20,
    this.requirePositiveSlope = true,
    this.pullbackEma = 20,
    this.entryEma = 50,
    this.zoneMode = 'EMA20_TO_EMA50',
    this.maxPullbackDistanceAtr = 1.5,
    this.rsiPeriod = 14,
    this.rsiMin = 40,
    this.rsiMax = 60,
    this.requireRecovery = true,
    this.volumeFilterEnabled = true,
    this.volumeSmaPeriod = 20,
    this.minVolumeMultiplier = 1.2,
    this.atrPeriod = 14,
    this.slAtrBuffer = 0.2,
    this.maxSlAtr = 2.0,
    this.minRR = 1.5,
    this.tp1R = 1.0,
    this.tp2R = 2.0,
    this.tp3R = 3.0,
    this.maxSetupCandles = 12,
    this.cooldownCandles = 4,
    this.candleConfirmationEnabled = true,
    this.minimumScore = 70,
  });

  /// Serializable form matching the backend TrendPullbackConfig.
  Map<String, dynamic> toJson() => {
        'htf': htf,
        'entryTimeframe': entryTimeframe,
        'emaFastHtf': emaFastHtf,
        'emaSlowHtf': emaSlowHtf,
        'adxPeriod': adxPeriod,
        'minAdx': minAdx,
        'requirePositiveSlope': requirePositiveSlope,
        'pullbackEma': pullbackEma,
        'entryEma': entryEma,
        'zoneMode': zoneMode,
        'maxPullbackDistanceAtr': maxPullbackDistanceAtr,
        'rsiPeriod': rsiPeriod,
        'rsiMin': rsiMin,
        'rsiMax': rsiMax,
        'requireRecovery': requireRecovery,
        'volumeFilterEnabled': volumeFilterEnabled,
        'volumeSmaPeriod': volumeSmaPeriod,
        'minVolumeMultiplier': minVolumeMultiplier,
        'atrPeriod': atrPeriod,
        'slAtrBuffer': slAtrBuffer,
        'maxSlAtr': maxSlAtr,
        'minRR': minRR,
        'tp1R': tp1R,
        'tp2R': tp2R,
        'tp3R': tp3R,
        'maxSetupCandles': maxSetupCandles,
        'cooldownCandles': cooldownCandles,
        'candleConfirmationEnabled': candleConfirmationEnabled,
        'minimumScore': minimumScore,
      };
}

class IndicatorConfig {
  final int emaFast, emaMid, emaSlow;
  final int rsiPeriod;
  final double rsiOversold, rsiNeutral, rsiOverbought, rsiExtremeOb;
  final int macdFast, macdSlow, macdSignalPeriod;
  final int atrPeriod, adxPeriod, volumeMaPeriod;

  const IndicatorConfig({
    this.emaFast = 20, this.emaMid = 50, this.emaSlow = 200,
    this.rsiPeriod = 14, this.rsiOversold = 30, this.rsiNeutral = 50,
    this.rsiOverbought = 70, this.rsiExtremeOb = 80,
    this.macdFast = 12, this.macdSlow = 26, this.macdSignalPeriod = 9,
    this.atrPeriod = 14, this.adxPeriod = 14, this.volumeMaPeriod = 20,
  });
}

class ScoringConfig {
  final int strongBuyThreshold, buyThreshold, watchThreshold;
  final double minRiskReward;

  const ScoringConfig({
    this.strongBuyThreshold = 85, this.buyThreshold = 75,
    this.watchThreshold = 65, this.minRiskReward = 1.5,
  });
}

class FilterConfig {
  final double minVolumeUsdt;
  final int signalCooldownHours;
  final bool skipBearishRegime;

  const FilterConfig({
    this.minVolumeUsdt = 5000000, this.signalCooldownHours = 4, this.skipBearishRegime = true,
  });
}

class EntryConfig {
  final double atrSlBuffer, tp1RMultiple, tp2RMultiple, tp3RMultiple;

  const EntryConfig({
    this.atrSlBuffer = 0.5, this.tp1RMultiple = 1.5,
    this.tp2RMultiple = 2.5, this.tp3RMultiple = 4.0,
  });
}

class FuturesStrategyConfig {
  final int defaultLeverage;
  final bool allowLong, allowShort;

  const FuturesStrategyConfig({
    this.defaultLeverage = 3, this.allowLong = true, this.allowShort = true,
  });
}
