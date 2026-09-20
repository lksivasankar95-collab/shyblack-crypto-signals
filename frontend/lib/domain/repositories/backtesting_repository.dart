import '../entities/backtest_equity_point.dart';
import '../entities/backtest_run.dart';
import '../entities/backtest_trade.dart';

class BacktestConfigInput {
  const BacktestConfigInput({
    required this.strategyId,
    required this.symbol,
    required this.timeframe,
    required this.tradingMode,
    required this.startDate,
    required this.endDate,
    required this.initialCapital,
    required this.riskPerTradePct,
    required this.feePct,
    required this.slippagePct,
    required this.leverage,
    this.executionModel = BacktestExecutionModel.nextCandleOpen,
    this.sameCandlePolicy = BacktestSameCandlePolicy.slFirst,
  });

  final String strategyId;
  final String symbol;
  final String timeframe;
  final BacktestTradingMode tradingMode;
  final DateTime startDate;
  final DateTime endDate;
  final double initialCapital;
  final double riskPerTradePct;
  final double feePct;
  final double slippagePct;
  final int leverage;
  final BacktestExecutionModel executionModel;
  final BacktestSameCandlePolicy sameCandlePolicy;
}

abstract class BacktestingRepository {
  Future<List<BacktestRun>> listRuns();
  Future<BacktestRun> getRun(String id);
  Future<BacktestRun> startRun(BacktestConfigInput config);
  Future<List<BacktestTrade>> getTrades(String id);
  Future<List<BacktestEquityPoint>> getEquity(String id);
  Future<BacktestRun> cancelRun(String id);
  Future<void> deleteRun(String id);
  Future<List<StrategyDescriptor>> listStrategies();
}

class StrategyDescriptor {
  const StrategyDescriptor({required this.id, required this.version});
  final String id;
  final String version;
}
