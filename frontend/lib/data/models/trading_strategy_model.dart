import '../../domain/entities/trading_strategy.dart';

class TradingStrategyModel {
  static TradingStrategy fromJson(Map<String, dynamic> json) {
    return TradingStrategy(
      id: json['id'] as String,
      name: json['name'] as String,
      description: json['description'] as String?,
      tradingMode: _parseMode(json['tradingMode'] as String),
      strategyType: _parseType(json['strategyType'] as String),
      version: json['version'] as int,
      status: _parseStatus(json['status'] as String),
      deletable: json['deletable'] as bool? ?? true,
      editable: json['editable'] as bool? ?? true,
      config: json['config'] != null
          ? _parseConfig(json['config'] as Map<String, dynamic>)
          : null,
      engineKey: json['engineKey'] as String?,
    );
  }

  static ActiveStrategyInfo activeFromJson(Map<String, dynamic> json) {
    return ActiveStrategyInfo(
      tradingMode: _parseMode(json['tradingMode'] as String),
      strategyId: json['strategyId'] as String?,
      strategyName: json['strategyName'] as String?,
      strategyVersion: json['strategyVersion'] as int? ?? 0,
      hasActiveStrategy: json['hasActiveStrategy'] as bool? ?? false,
    );
  }

  static Map<String, dynamic> createToJson({
    required String name,
    String? description,
    required String tradingMode,
    Map<String, dynamic>? config,
    String? engineKey,
  }) {
    return {
      'name': name,
      'description': description,
      'tradingMode': tradingMode,
      'config': ?config,
      'engineKey': ?engineKey,
    };
  }

  static Map<String, dynamic> setActiveToJson(
    String tradingMode,
    String strategyId,
  ) {
    return {'tradingMode': tradingMode, 'strategyId': strategyId};
  }

  static StrategyTradingMode _parseMode(String s) =>
      s == 'FUTURES' ? StrategyTradingMode.futures : StrategyTradingMode.spot;

  static StrategyType _parseType(String s) =>
      s == 'SYSTEM' ? StrategyType.system : StrategyType.user;

  static StrategyStatus _parseStatus(String s) =>
      s == 'ACTIVE' ? StrategyStatus.active : StrategyStatus.inactive;

  static StrategyConfig _parseConfig(Map<String, dynamic> json) {
    final ind = json['indicators'] as Map<String, dynamic>?;
    final sc = json['scoring'] as Map<String, dynamic>?;
    final fi = json['filters'] as Map<String, dynamic>?;
    final en = json['entry'] as Map<String, dynamic>?;
    final fu = json['futures'] as Map<String, dynamic>?;
    final pb = json['pullback'] as Map<String, dynamic>?;
    final etf = json['emaTrendFollowing'] as Map<String, dynamic>?;
    return StrategyConfig(
      emaTrendFollowing: etf != null ? _ema(etf) : null,
      pullback: pb != null
          ? PullbackConfig(
              htf: pb['htf'] as String? ?? '1H',
              entryTimeframe: pb['entryTimeframe'] as String? ?? '15M',
              emaFastHtf: pb['emaFastHtf'] as int? ?? 50,
              emaSlowHtf: pb['emaSlowHtf'] as int? ?? 200,
              adxPeriod: pb['adxPeriod'] as int? ?? 14,
              minAdx: (pb['minAdx'] as num?)?.toDouble() ?? 20,
              requirePositiveSlope: pb['requirePositiveSlope'] as bool? ?? true,
              pullbackEma: pb['pullbackEma'] as int? ?? 20,
              entryEma: pb['entryEma'] as int? ?? 50,
              zoneMode: pb['zoneMode'] as String? ?? 'EMA20_TO_EMA50',
              maxPullbackDistanceAtr:
                  (pb['maxPullbackDistanceAtr'] as num?)?.toDouble() ?? 1.5,
              rsiPeriod: pb['rsiPeriod'] as int? ?? 14,
              rsiMin: (pb['rsiMin'] as num?)?.toDouble() ?? 40,
              rsiMax: (pb['rsiMax'] as num?)?.toDouble() ?? 60,
              requireRecovery: pb['requireRecovery'] as bool? ?? true,
              volumeFilterEnabled: pb['volumeFilterEnabled'] as bool? ?? true,
              volumeSmaPeriod: pb['volumeSmaPeriod'] as int? ?? 20,
              minVolumeMultiplier:
                  (pb['minVolumeMultiplier'] as num?)?.toDouble() ?? 1.2,
              atrPeriod: pb['atrPeriod'] as int? ?? 14,
              slAtrBuffer: (pb['slAtrBuffer'] as num?)?.toDouble() ?? 0.2,
              maxSlAtr: (pb['maxSlAtr'] as num?)?.toDouble() ?? 2.0,
              minRR: (pb['minRR'] as num?)?.toDouble() ?? 1.5,
              tp1R: (pb['tp1R'] as num?)?.toDouble() ?? 1.0,
              tp2R: (pb['tp2R'] as num?)?.toDouble() ?? 2.0,
              tp3R: (pb['tp3R'] as num?)?.toDouble() ?? 3.0,
              maxSetupCandles: pb['maxSetupCandles'] as int? ?? 12,
              cooldownCandles: pb['cooldownCandles'] as int? ?? 4,
              candleConfirmationEnabled:
                  pb['candleConfirmationEnabled'] as bool? ?? true,
              minimumScore: pb['minimumScore'] as int? ?? 70,
            )
          : null,
      indicators: ind != null
          ? IndicatorConfig(
              emaFast: ind['emaFast'] as int? ?? 20,
              emaMid: ind['emaMid'] as int? ?? 50,
              emaSlow: ind['emaSlow'] as int? ?? 200,
              rsiPeriod: ind['rsiPeriod'] as int? ?? 14,
              rsiOversold: (ind['rsiOversold'] as num?)?.toDouble() ?? 30,
              rsiNeutral: (ind['rsiNeutral'] as num?)?.toDouble() ?? 50,
              rsiOverbought: (ind['rsiOverbought'] as num?)?.toDouble() ?? 70,
              rsiExtremeOb: (ind['rsiExtremeOb'] as num?)?.toDouble() ?? 80,
              macdFast: ind['macdFast'] as int? ?? 12,
              macdSlow: ind['macdSlow'] as int? ?? 26,
              macdSignalPeriod: ind['macdSignalPeriod'] as int? ?? 9,
              atrPeriod: ind['atrPeriod'] as int? ?? 14,
              adxPeriod: ind['adxPeriod'] as int? ?? 14,
              volumeMaPeriod: ind['volumeMaPeriod'] as int? ?? 20,
            )
          : null,
      scoring: sc != null
          ? ScoringConfig(
              strongBuyThreshold: sc['strongBuyThreshold'] as int? ?? 85,
              buyThreshold: sc['buyThreshold'] as int? ?? 75,
              watchThreshold: sc['watchThreshold'] as int? ?? 65,
              minRiskReward: (sc['minRiskReward'] as num?)?.toDouble() ?? 1.5,
            )
          : null,
      filters: fi != null
          ? FilterConfig(
              minVolumeUsdt:
                  (fi['minVolumeUsdt'] as num?)?.toDouble() ?? 5000000,
              signalCooldownHours: fi['signalCooldownHours'] as int? ?? 4,
              skipBearishRegime: fi['skipBearishRegime'] as bool? ?? true,
            )
          : null,
      entry: en != null
          ? EntryConfig(
              atrSlBuffer: (en['atrSlBuffer'] as num?)?.toDouble() ?? 0.5,
              tp1RMultiple: (en['tp1RMultiple'] as num?)?.toDouble() ?? 1.5,
              tp2RMultiple: (en['tp2RMultiple'] as num?)?.toDouble() ?? 2.5,
              tp3RMultiple: (en['tp3RMultiple'] as num?)?.toDouble() ?? 4.0,
            )
          : null,
      futures: fu != null
          ? FuturesStrategyConfig(
              defaultLeverage: fu['defaultLeverage'] as int? ?? 3,
              allowLong: fu['allowLong'] as bool? ?? true,
              allowShort: fu['allowShort'] as bool? ?? true,
            )
          : null,
    );
  }

  static EmaTrendFollowingConfig _ema(Map<String, dynamic> j) =>
      EmaTrendFollowingConfig(
        htfTimeframe: j['htfTimeframe'] as String? ?? '1H',
        entryTimeframe: j['entryTimeframe'] as String? ?? '15M',
        htfFastEma: j['htfFastEma'] as int? ?? 50,
        htfSlowEma: j['htfSlowEma'] as int? ?? 200,
        trendSlopeLookback: j['trendSlopeLookback'] as int? ?? 5,
        entryFastEma: j['entryFastEma'] as int? ?? 20,
        entrySlowEma: j['entrySlowEma'] as int? ?? 50,
        minimumEmaSeparationPct:
            (j['minimumEmaSeparationPct'] as num?)?.toDouble() ?? 0.10,
        rsiFilterEnabled: j['rsiFilterEnabled'] as bool? ?? true,
        rsiPeriod: j['rsiPeriod'] as int? ?? 14,
        minimumRsiForLong: (j['minimumRsiForLong'] as num?)?.toDouble() ?? 50,
        maximumRsiForLong: (j['maximumRsiForLong'] as num?)?.toDouble() ?? 70,
        volumeFilterEnabled: j['volumeFilterEnabled'] as bool? ?? true,
        volumePeriod: j['volumePeriod'] as int? ?? 20,
        minimumVolumeRatio:
            (j['minimumVolumeRatio'] as num?)?.toDouble() ?? 1.20,
        atrFilterEnabled: j['atrFilterEnabled'] as bool? ?? true,
        atrPeriod: j['atrPeriod'] as int? ?? 14,
        minimumAtrPct: (j['minimumAtrPct'] as num?)?.toDouble() ?? 0.30,
        slAtrBuffer: (j['slAtrBuffer'] as num?)?.toDouble() ?? 1.5,
        minRR: (j['minRR'] as num?)?.toDouble() ?? 1.5,
        tp1R: (j['tp1R'] as num?)?.toDouble() ?? 1.5,
        tp2R: (j['tp2R'] as num?)?.toDouble() ?? 2.5,
        tp3R: (j['tp3R'] as num?)?.toDouble() ?? 4.0,
        cooldownCandles: j['cooldownCandles'] as int? ?? 4,
        minimumScore: j['minimumScore'] as int? ?? 70,
        weightTrendAlignment: j['weightTrendAlignment'] as int? ?? 30,
        weightEmaTransition: j['weightEmaTransition'] as int? ?? 20,
        weightPriceConfirmation: j['weightPriceConfirmation'] as int? ?? 20,
        weightMomentum: j['weightMomentum'] as int? ?? 15,
        weightVolume: j['weightVolume'] as int? ?? 10,
        weightVolatility: j['weightVolatility'] as int? ?? 5,
      );
}
