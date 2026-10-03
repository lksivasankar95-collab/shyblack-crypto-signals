import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/di/providers.dart';
import '../../domain/entities/backtest_equity_point.dart';
import '../../domain/entities/backtest_run.dart';
import '../../domain/entities/backtest_trade.dart';
import '../../domain/repositories/backtesting_repository.dart';

class BacktestingListViewData {
  const BacktestingListViewData({
    required this.runs,
    required this.strategies,
  });
  final List<BacktestRun> runs;
  final List<StrategyDescriptor> strategies;
}

class BacktestingController extends AsyncNotifier<BacktestingListViewData> {
  Timer? _refreshTimer;

  @override
  Future<BacktestingListViewData> build() async {
    ref.onDispose(() => _refreshTimer?.cancel());
    final view = await _fetch();
    _startAutoRefresh();
    return view;
  }

  Future<BacktestingListViewData> _fetch() async {
    final repo = ref.read(backtestingRepositoryProvider);
    final results = await Future.wait([repo.listRuns(), repo.listStrategies()]);
    return BacktestingListViewData(
      runs: results[0] as List<BacktestRun>,
      strategies: results[1] as List<StrategyDescriptor>,
    );
  }

  Future<void> refresh({bool silent = true}) async {
    try { state = AsyncData(await _fetch()); }
    catch (error, stack) { if (!silent) state = AsyncError(error, stack); }
  }

  Future<BacktestRun> startRun(BacktestConfigInput config) async {
    final run = await ref.read(backtestingRepositoryProvider).startRun(config);
    unawaited(refresh());
    return run;
  }

  Future<void> cancelRun(String id) async {
    await ref.read(backtestingRepositoryProvider).cancelRun(id);
    unawaited(refresh());
  }

  Future<void> deleteRun(String id) async {
    await ref.read(backtestingRepositoryProvider).deleteRun(id);
    unawaited(refresh());
  }

  void _startAutoRefresh() {
    _refreshTimer?.cancel();
    _refreshTimer = Timer.periodic(const Duration(seconds: 4), (_) {
      // Poll while any run is not-terminal so the progress bar stays live.
      final async = state;
      final needsPoll = async is AsyncData<BacktestingListViewData>
          && async.value.runs.any((r) => !r.isTerminal);
      if (needsPoll) unawaited(refresh());
    });
  }
}

final backtestingControllerProvider =
    AsyncNotifierProvider<BacktestingController, BacktestingListViewData>(
  BacktestingController.new,
);

/// Detail view — trades + equity for a single run. Cached per id.
final backtestRunDetailProvider =
    FutureProvider.autoDispose.family<BacktestRunDetail, String>((ref, id) async {
  final repo = ref.watch(backtestingRepositoryProvider);
  final results = await Future.wait([
    repo.getRun(id),
    repo.getTrades(id),
    repo.getEquity(id),
  ]);
  return BacktestRunDetail(
    run: results[0] as BacktestRun,
    trades: results[1] as List<BacktestTrade>,
    equity: results[2] as List<BacktestEquityPoint>,
  );
});

class BacktestRunDetail {
  const BacktestRunDetail({required this.run, required this.trades, required this.equity});
  final BacktestRun run;
  final List<BacktestTrade> trades;
  final List<BacktestEquityPoint> equity;
}
