import '../../domain/entities/backtest_equity_point.dart';
import '../../domain/entities/backtest_run.dart';
import '../../domain/entities/backtest_trade.dart';
import '../../domain/repositories/backtesting_repository.dart';
import '../datasources/backtesting_remote_data_source.dart';
import '../models/backtest_equity_model.dart';
import '../models/backtest_run_model.dart';
import '../models/backtest_trade_model.dart';

class BacktestingRepositoryImpl implements BacktestingRepository {
  BacktestingRepositoryImpl(this._remote);

  final BacktestingRemoteDataSource _remote;

  @override
  Future<List<BacktestRun>> listRuns() async =>
      (await _remote.listRuns()).map((r) => BacktestRunModel.fromJson(r).run).toList();

  @override
  Future<BacktestRun> getRun(String id) async =>
      BacktestRunModel.fromJson(await _remote.getRun(id)).run;

  @override
  Future<BacktestRun> startRun(BacktestConfigInput config) async {
    final body = <String, dynamic>{
      'strategyId': config.strategyId,
      'symbol': config.symbol,
      'timeframe': config.timeframe,
      'tradingMode': _mode(config.tradingMode),
      'startDate': config.startDate.toUtc().toIso8601String(),
      'endDate': config.endDate.toUtc().toIso8601String(),
      'initialCapital': config.initialCapital,
      'riskPerTradePct': config.riskPerTradePct,
      'feePct': config.feePct,
      'slippagePct': config.slippagePct,
      'leverage': config.leverage,
      'executionModel': _exec(config.executionModel),
      'sameCandlePolicy': _scp(config.sameCandlePolicy),
    };
    return BacktestRunModel.fromJson(await _remote.startRun(body)).run;
  }

  @override
  Future<List<BacktestTrade>> getTrades(String id) async =>
      (await _remote.getTrades(id)).map((r) => BacktestTradeModel.fromJson(r).trade).toList();

  @override
  Future<List<BacktestEquityPoint>> getEquity(String id) async =>
      (await _remote.getEquity(id)).map((r) => BacktestEquityModel.fromJson(r).point).toList();

  @override
  Future<BacktestRun> cancelRun(String id) async =>
      BacktestRunModel.fromJson(await _remote.cancelRun(id)).run;

  @override
  Future<void> deleteRun(String id) => _remote.deleteRun(id);

  @override
  Future<List<StrategyDescriptor>> listStrategies() async =>
      (await _remote.listStrategies()).map((r) => StrategyDescriptor(
            id: r['id'] as String? ?? '',
            version: r['version'] as String? ?? '',
          )).toList();

  static String _mode(BacktestTradingMode m) => switch (m) {
        BacktestTradingMode.spot => 'SPOT',
        BacktestTradingMode.futures => 'FUTURES',
        BacktestTradingMode.options => 'OPTIONS',
      };

  static String _exec(BacktestExecutionModel m) =>
      m == BacktestExecutionModel.sameCandleClose ? 'SAME_CANDLE_CLOSE' : 'NEXT_CANDLE_OPEN';

  static String _scp(BacktestSameCandlePolicy p) => switch (p) {
        BacktestSameCandlePolicy.conservative => 'CONSERVATIVE',
        BacktestSameCandlePolicy.slFirst => 'SL_FIRST',
        BacktestSameCandlePolicy.tpFirst => 'TP_FIRST',
        BacktestSameCandlePolicy.requireLowerTimeframe => 'REQUIRE_LOWER_TIMEFRAME',
      };
}
