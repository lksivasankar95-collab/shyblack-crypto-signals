import '../../domain/entities/trading_strategy.dart';
import '../../domain/repositories/trading_strategy_repository.dart';
import '../datasources/strategy_remote_data_source.dart';

class TradingStrategyRepositoryImpl implements TradingStrategyRepository {
  final StrategyRemoteDataSource _remote;
  TradingStrategyRepositoryImpl(this._remote);

  String _modeStr(StrategyTradingMode mode) => mode == StrategyTradingMode.futures ? 'FUTURES' : 'SPOT';

  @override
  Future<List<TradingStrategy>> listStrategies(StrategyTradingMode mode) =>
      _remote.listStrategies(_modeStr(mode));

  @override
  Future<TradingStrategy> createStrategy({
    required String name, String? description, required StrategyTradingMode tradingMode,
    StrategyConfig? config, String? engineKey,
  }) => _remote.createStrategy(
      name: name,
      description: description,
      tradingMode: _modeStr(tradingMode),
      config: _configMap(config),
      engineKey: engineKey);

  /// Serializes whichever strategy-engine sub-config is present.
  static Map<String, dynamic>? _configMap(StrategyConfig? config) {
    if (config == null) return null;
    final map = <String, dynamic>{};
    if (config.pullback != null) map['pullback'] = config.pullback!.toJson();
    if (config.emaTrendFollowing != null) {
      map['emaTrendFollowing'] = config.emaTrendFollowing!.toJson();
    }
    return map.isEmpty ? null : map;
  }

  @override
  Future<void> deleteStrategy(String id) => _remote.deleteStrategy(id);

  @override
  Future<ActiveStrategyInfo> getActiveStrategy(StrategyTradingMode mode) =>
      _remote.getActiveStrategy(_modeStr(mode));

  @override
  Future<ActiveStrategyInfo> setActiveStrategy(StrategyTradingMode mode, String strategyId) =>
      _remote.setActiveStrategy(_modeStr(mode), strategyId);
}
