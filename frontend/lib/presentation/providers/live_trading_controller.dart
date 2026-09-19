import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/di/providers.dart';
import '../../domain/entities/live_account.dart';
import '../../domain/entities/live_order.dart';
import '../../domain/entities/live_performance.dart';

class LiveTradingViewData {
  const LiveTradingViewData({
    required this.account,
    required this.openOrders,
    required this.history,
    required this.performance,
  });

  /// null when the user has never connected an exchange.
  final LiveAccount? account;
  final List<LiveOrder> openOrders;
  final List<LiveOrder> history;
  final LivePerformance performance;

  bool get connected => account?.connectionStatus == LiveConnectionStatus.connected;
  bool get isTradingReady => account?.isTradingReady ?? false;
}

class LiveTradingController extends AsyncNotifier<LiveTradingViewData> {
  Timer? _refreshTimer;

  @override
  Future<LiveTradingViewData> build() async {
    ref.onDispose(() => _refreshTimer?.cancel());
    final view = await _fetch();
    _startAutoRefresh();
    return view;
  }

  Future<LiveTradingViewData> _fetch() async {
    final repo = ref.read(liveTradingRepositoryProvider);
    final account = await repo.getAccount();
    if (account == null) {
      return LiveTradingViewData(
        account: null,
        openOrders: const [],
        history: const [],
        performance: const LivePerformance(
            totalOrders: 0,
            filledEntries: 0,
            rejections: 0,
            totalFees: 0,
            totalNotional: 0),
      );
    }
    final results = await Future.wait([
      repo.listOpenOrders(),
      repo.listHistory(),
      repo.getPerformance(),
    ]);
    return LiveTradingViewData(
      account: account,
      openOrders: results[0] as List<LiveOrder>,
      history: results[1] as List<LiveOrder>,
      performance: results[2] as LivePerformance,
    );
  }

  Future<void> refresh({bool silent = true}) async {
    try {
      state = AsyncData(await _fetch());
    } catch (error, stack) {
      if (!silent) state = AsyncError(error, stack);
    }
  }

  Future<LiveAccount> connect(LiveExchange exchange) async {
    final account = await ref.read(liveTradingRepositoryProvider).connect(exchange);
    unawaited(refresh());
    return account;
  }

  Future<LiveAccount> activate({required bool acknowledged}) async {
    final acc = await ref.read(liveTradingRepositoryProvider)
        .activate(acknowledged: acknowledged);
    unawaited(refresh());
    return acc;
  }

  Future<LiveAccount> deactivate() async {
    final acc = await ref.read(liveTradingRepositoryProvider).deactivate();
    unawaited(refresh());
    return acc;
  }

  Future<LiveAccount> triggerKillSwitch() async {
    final acc = await ref.read(liveTradingRepositoryProvider).triggerKillSwitch();
    unawaited(refresh());
    return acc;
  }

  Future<LiveAccount> releaseKillSwitch() async {
    final acc = await ref.read(liveTradingRepositoryProvider).releaseKillSwitch();
    unawaited(refresh());
    return acc;
  }

  Future<LiveOrder> cancelOrder(String id) async {
    final order = await ref.read(liveTradingRepositoryProvider).cancelOrder(id);
    unawaited(refresh());
    return order;
  }

  Future<LiveOrder> closePosition(String entryOrderId) async {
    final order = await ref.read(liveTradingRepositoryProvider)
        .closePosition(entryOrderId);
    unawaited(refresh());
    return order;
  }

  void _startAutoRefresh() {
    _refreshTimer?.cancel();
    _refreshTimer = Timer.periodic(const Duration(seconds: 10), (_) {
      unawaited(refresh());
    });
  }
}

final liveTradingControllerProvider =
    AsyncNotifierProvider<LiveTradingController, LiveTradingViewData>(
  LiveTradingController.new,
);
