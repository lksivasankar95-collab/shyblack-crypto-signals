import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/di/providers.dart';
import '../../domain/entities/trading_strategy.dart';

// State for one tab (SPOT or FUTURES)
class StrategyTabState {
  final List<TradingStrategy> strategies;
  final ActiveStrategyInfo? activeInfo;
  final bool loading;
  final String? error;

  const StrategyTabState({
    this.strategies = const [],
    this.activeInfo,
    this.loading = false,
    this.error,
  });

  StrategyTabState copyWith({
    List<TradingStrategy>? strategies,
    ActiveStrategyInfo? activeInfo,
    bool? loading,
    String? error,
  }) => StrategyTabState(
        strategies: strategies ?? this.strategies,
        activeInfo: activeInfo ?? this.activeInfo,
        loading: loading ?? this.loading,
        error: error,
      );
}

class StrategyTabController extends AutoDisposeAsyncNotifier<StrategyTabState> {
  late StrategyTradingMode _mode;

  static AutoDisposeAsyncNotifierProviderFamily<StrategyTabController, StrategyTabState, StrategyTradingMode>
      provider = AsyncNotifierProvider.autoDispose.family<StrategyTabController, StrategyTabState, StrategyTradingMode>(
    StrategyTabController.new,
  );

  @override
  Future<StrategyTabState> build(StrategyTradingMode arg) async {
    _mode = arg;
    return _load();
  }

  Future<StrategyTabState> _load() async {
    final repo = ref.read(strategyRepositoryProvider);
    final results = await Future.wait([
      repo.listStrategies(_mode),
      repo.getActiveStrategy(_mode),
    ]);
    return StrategyTabState(
      strategies: results[0] as List<TradingStrategy>,
      activeInfo: results[1] as ActiveStrategyInfo,
    );
  }

  Future<void> setActive(String strategyId) async {
    final repo = ref.read(strategyRepositoryProvider);
    final info = await repo.setActiveStrategy(_mode, strategyId);
    final current = state.valueOrNull;
    if (current != null) {
      state = AsyncData(current.copyWith(activeInfo: info));
    }
  }

  Future<void> createStrategy(String name, String? description) async {
    final repo = ref.read(strategyRepositoryProvider);
    await repo.createStrategy(name: name, description: description, tradingMode: _mode);
    state = AsyncData(await _load());
  }

  Future<void> deleteStrategy(String id) async {
    final repo = ref.read(strategyRepositoryProvider);
    await repo.deleteStrategy(id);
    state = AsyncData(await _load());
  }

  Future<void> refresh() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() => _load());
  }
}
