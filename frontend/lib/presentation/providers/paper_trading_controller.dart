import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/di/providers.dart';
import '../../domain/entities/paper_account.dart';
import '../../domain/entities/paper_performance.dart';
import '../../domain/entities/paper_position.dart';

class PaperTradingViewData {
  const PaperTradingViewData({
    required this.account,
    required this.openPositions,
    required this.history,
    required this.performance,
  });

  final PaperAccount account;
  final List<PaperPosition> openPositions;
  final List<PaperPosition> history;
  final PaperPerformance performance;
}

class PaperTradingController extends AsyncNotifier<PaperTradingViewData> {
  Timer? _refreshTimer;

  @override
  Future<PaperTradingViewData> build() async {
    ref.onDispose(() => _refreshTimer?.cancel());
    final view = await _fetch();
    _startAutoRefresh();
    return view;
  }

  Future<PaperTradingViewData> _fetch() async {
    final repo = ref.read(paperTradingRepositoryProvider);
    final results = await Future.wait([
      repo.getAccount(),
      repo.listOpenPositions(),
      repo.listHistory(),
      repo.getPerformance(),
    ]);
    return PaperTradingViewData(
      account: results[0] as PaperAccount,
      openPositions: results[1] as List<PaperPosition>,
      history: results[2] as List<PaperPosition>,
      performance: results[3] as PaperPerformance,
    );
  }

  Future<void> refresh({bool silent = true}) async {
    try {
      final next = await _fetch();
      state = AsyncData(next);
    } catch (error, stack) {
      if (!silent) state = AsyncError(error, stack);
    }
  }

  Future<PaperPosition> closePosition(String id) async {
    final repo = ref.read(paperTradingRepositoryProvider);
    final closed = await repo.closePosition(id);
    // Optimistic refresh so account balance + history reflect the change.
    unawaited(refresh());
    return closed;
  }

  Future<void> resetAccount() async {
    final repo = ref.read(paperTradingRepositoryProvider);
    await repo.resetAccount();
    await refresh(silent: false);
  }

  void _startAutoRefresh() {
    _refreshTimer?.cancel();
    _refreshTimer = Timer.periodic(const Duration(seconds: 10), (_) {
      unawaited(refresh());
    });
  }
}

final paperTradingControllerProvider =
    AsyncNotifierProvider<PaperTradingController, PaperTradingViewData>(
  PaperTradingController.new,
);
