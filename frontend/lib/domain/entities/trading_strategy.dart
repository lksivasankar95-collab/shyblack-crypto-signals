import 'package:flutter/foundation.dart';

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

  const StrategyConfig({this.indicators, this.scoring, this.filters, this.entry, this.futures});
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
