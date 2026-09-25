import '../entities/trading_strategy.dart';

abstract class TradingStrategyRepository {
  Future<List<TradingStrategy>> listStrategies(StrategyTradingMode mode);
  Future<TradingStrategy> createStrategy({
    required String name, String? description, required StrategyTradingMode tradingMode,
  });
  Future<void> deleteStrategy(String id);
  Future<ActiveStrategyInfo> getActiveStrategy(StrategyTradingMode mode);
  Future<ActiveStrategyInfo> setActiveStrategy(StrategyTradingMode mode, String strategyId);
}
