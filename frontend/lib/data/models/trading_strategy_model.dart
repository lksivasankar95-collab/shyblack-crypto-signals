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
      config: json['config'] != null ? _parseConfig(json['config'] as Map<String, dynamic>) : null,
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
    required String name, String? description,
    required String tradingMode, Map<String, dynamic>? config,
  }) {
    return {
      'name': name,
      'description': description,
      'tradingMode': tradingMode,
      if (config != null) 'config': config,
    };
  }

  static Map<String, dynamic> setActiveToJson(String tradingMode, String strategyId) {
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
    return StrategyConfig(
      indicators: ind != null ? IndicatorConfig(
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
      ) : null,
      scoring: sc != null ? ScoringConfig(
        strongBuyThreshold: sc['strongBuyThreshold'] as int? ?? 85,
        buyThreshold: sc['buyThreshold'] as int? ?? 75,
        watchThreshold: sc['watchThreshold'] as int? ?? 65,
        minRiskReward: (sc['minRiskReward'] as num?)?.toDouble() ?? 1.5,
      ) : null,
      filters: fi != null ? FilterConfig(
        minVolumeUsdt: (fi['minVolumeUsdt'] as num?)?.toDouble() ?? 5000000,
        signalCooldownHours: fi['signalCooldownHours'] as int? ?? 4,
        skipBearishRegime: fi['skipBearishRegime'] as bool? ?? true,
      ) : null,
      entry: en != null ? EntryConfig(
        atrSlBuffer: (en['atrSlBuffer'] as num?)?.toDouble() ?? 0.5,
        tp1RMultiple: (en['tp1RMultiple'] as num?)?.toDouble() ?? 1.5,
        tp2RMultiple: (en['tp2RMultiple'] as num?)?.toDouble() ?? 2.5,
        tp3RMultiple: (en['tp3RMultiple'] as num?)?.toDouble() ?? 4.0,
      ) : null,
      futures: fu != null ? FuturesStrategyConfig(
        defaultLeverage: fu['defaultLeverage'] as int? ?? 3,
        allowLong: fu['allowLong'] as bool? ?? true,
        allowShort: fu['allowShort'] as bool? ?? true,
      ) : null,
    );
  }
}
