import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/di/providers.dart';
import '../../domain/entities/trading_strategy.dart';

class StrategyTabState {
  final List<TradingStrategy> strategies;
  final ActiveStrategyInfo? activeInfo;
  final String? error;

  const StrategyTabState({
    this.strategies = const [],
    this.activeInfo,
    this.error,
  });

  StrategyTabState copyWith({
    List<TradingStrategy>? strategies,
    ActiveStrategyInfo? activeInfo,
    String? error,
  }) => StrategyTabState(
        strategies: strategies ?? this.strategies,
        activeInfo: activeInfo ?? this.activeInfo,
        error: error,
      );
}

// ── Base controller ───────────────────────────────────────────────────────────

abstract class StrategyTabController
    extends AsyncNotifier<StrategyTabState> {
  StrategyTradingMode get mode;

  @override
  Future<StrategyTabState> build() => _load();

  Future<StrategyTabState> _load() async {
    final repo = ref.read(strategyRepositoryProvider);
    final results = await Future.wait([
      repo.listStrategies(mode),
      repo.getActiveStrategy(mode),
    ]);
    return StrategyTabState(
      strategies: results[0] as List<TradingStrategy>,
      activeInfo: results[1] as ActiveStrategyInfo?,
    );
  }

  Future<void> setActive(String strategyId) async {
    final repo = ref.read(strategyRepositoryProvider);
    final info = await repo.setActiveStrategy(mode, strategyId);
    final current = state.asData?.value;
    if (current != null) {
      state = AsyncData(current.copyWith(activeInfo: info));
    }
  }

  Future<void> createStrategy(String name, String? description) async {
    final repo = ref.read(strategyRepositoryProvider);
    await repo.createStrategy(name: name, description: description, tradingMode: mode);
    state = await AsyncValue.guard(() => _load());
  }

  Future<void> deleteStrategy(String id) async {
    final repo = ref.read(strategyRepositoryProvider);
    await repo.deleteStrategy(id);
    state = await AsyncValue.guard(() => _load());
  }

  Future<void> refresh() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() => _load());
  }
}

// ── Concrete per-mode controllers ─────────────────────────────────────────────

class SpotStrategyController extends StrategyTabController {
  @override
  StrategyTradingMode get mode => StrategyTradingMode.spot;
}

class FuturesStrategyController extends StrategyTabController {
  @override
  StrategyTradingMode get mode => StrategyTradingMode.futures;
}

// ── Providers ─────────────────────────────────────────────────────────────────

final spotStrategyTabProvider =
    AsyncNotifierProvider<SpotStrategyController, StrategyTabState>(
  SpotStrategyController.new,
);

final futuresStrategyTabProvider =
    AsyncNotifierProvider<FuturesStrategyController, StrategyTabState>(
  FuturesStrategyController.new,
);

/// Convenience helper — returns the provider for a given mode.
AsyncNotifierProvider<StrategyTabController, StrategyTabState>
    strategyTabProvider(StrategyTradingMode mode) => mode == StrategyTradingMode.spot
        ? spotStrategyTabProvider
        : futuresStrategyTabProvider;
