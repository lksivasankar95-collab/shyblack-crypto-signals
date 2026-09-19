import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/di/providers.dart';
import '../../domain/entities/futures_account.dart';
import '../../domain/entities/futures_order.dart';
import '../../domain/entities/futures_position.dart';

class FuturesTradingViewData {
  const FuturesTradingViewData({
    required this.account,
    required this.openOrders,
    required this.history,
    required this.openPositions,
    required this.closedPositions,
  });

  final FuturesAccount? account;
  final List<FuturesOrder> openOrders;
  final List<FuturesOrder> history;
  final List<FuturesPosition> openPositions;
  final List<FuturesPosition> closedPositions;

  bool get connected =>
      account?.connectionStatus == FuturesConnectionStatus.connected;
  bool get isTradingReady => account?.isTradingReady ?? false;
}

class FuturesTradingController extends AsyncNotifier<FuturesTradingViewData> {
  Timer? _refreshTimer;

  @override
  Future<FuturesTradingViewData> build() async {
    ref.onDispose(() => _refreshTimer?.cancel());
    final view = await _fetch();
    _startAutoRefresh();
    return view;
  }

  Future<FuturesTradingViewData> _fetch() async {
    final repo = ref.read(futuresTradingRepositoryProvider);
    final account = await repo.getAccount();
    if (account == null) {
      return const FuturesTradingViewData(
        account: null,
        openOrders: [],
        history: [],
        openPositions: [],
        closedPositions: [],
      );
    }
    final results = await Future.wait([
      repo.listOpenOrders(),
      repo.listHistory(),
      repo.listOpenPositions(),
      repo.listClosedPositions(),
    ]);
    return FuturesTradingViewData(
      account: account,
      openOrders: results[0] as List<FuturesOrder>,
      history: results[1] as List<FuturesOrder>,
      openPositions: results[2] as List<FuturesPosition>,
      closedPositions: results[3] as List<FuturesPosition>,
    );
  }

  Future<void> refresh({bool silent = true}) async {
    try { state = AsyncData(await _fetch()); }
    catch (error, stack) { if (!silent) state = AsyncError(error, stack); }
  }

  Future<FuturesAccount> connect(String exchange) async {
    final acc = await ref.read(futuresTradingRepositoryProvider).connect(exchange);
    unawaited(refresh()); return acc;
  }

  Future<FuturesAccount> activate({required bool acknowledged}) async {
    final acc = await ref.read(futuresTradingRepositoryProvider)
        .activate(acknowledged: acknowledged);
    unawaited(refresh()); return acc;
  }

  Future<FuturesAccount> deactivate() async {
    final acc = await ref.read(futuresTradingRepositoryProvider).deactivate();
    unawaited(refresh()); return acc;
  }

  Future<FuturesAccount> triggerKillSwitch() async {
    final acc = await ref.read(futuresTradingRepositoryProvider).triggerKillSwitch();
    unawaited(refresh()); return acc;
  }

  Future<FuturesAccount> releaseKillSwitch() async {
    final acc = await ref.read(futuresTradingRepositoryProvider).releaseKillSwitch();
    unawaited(refresh()); return acc;
  }

  Future<FuturesOrder> cancelOrder(String id) async {
    final o = await ref.read(futuresTradingRepositoryProvider).cancelOrder(id);
    unawaited(refresh()); return o;
  }

  Future<FuturesOrder> closePosition(String positionId) async {
    final o = await ref.read(futuresTradingRepositoryProvider).closePosition(positionId);
    unawaited(refresh()); return o;
  }

  void _startAutoRefresh() {
    _refreshTimer?.cancel();
    _refreshTimer = Timer.periodic(const Duration(seconds: 10), (_) {
      unawaited(refresh());
    });
  }
}

final futuresTradingControllerProvider =
    AsyncNotifierProvider<FuturesTradingController, FuturesTradingViewData>(
  FuturesTradingController.new,
);
