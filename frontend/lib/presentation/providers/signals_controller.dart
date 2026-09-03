import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/di/providers.dart';
import '../../domain/entities/app_settings.dart';
import '../../domain/entities/signal.dart';
import 'auth_session.dart';
import 'settings_controller.dart';

class SignalsStats {
  const SignalsStats({
    required this.activeCount,
    this.winRateLast30d,
    this.totalPnlUsdt,
    this.avgReturnPerSignal,
  });

  final int activeCount;

  /// Average strategy win rate over signals closed in the last 30 days,
  /// when the backend provides outcome data.
  final double? winRateLast30d;

  /// Realised PnL from closed signals, when outcome data is available.
  final double? totalPnlUsdt;

  /// Average return per signal, when outcome data is available.
  final double? avgReturnPerSignal;
}

class SignalsViewData {
  const SignalsViewData({
    required this.mode,
    required this.all,
    required this.stats,
    this.lastUpdated,
  });

  final TradingMode mode;
  final List<Signal> all;
  final SignalsStats stats;
  final DateTime? lastUpdated;

  static const signalRefreshInterval = Duration(seconds: 15);

  List<Signal> byStatus(SignalStatus status) => _byStatus[status] ?? const [];

  Map<SignalStatus, List<Signal>> get _byStatus {
    final grouped = <SignalStatus, List<Signal>>{};
    for (final status in SignalStatus.values) {
      grouped[status] = [
        for (final signal in all)
          if (signal.status == status) signal,
      ];
    }
    return grouped;
  }
}

class SignalsController extends AsyncNotifier<SignalsViewData> {
  Timer? _refreshTimer;
  int _generation = 0;

  @override
  Future<SignalsViewData> build() async {
    final generation = ++_generation;
    final mode = ref.watch(
      settingsControllerProvider.select(
        (async) => async.value?.tradingMode ?? TradingMode.spot,
      ),
    );

    ref.listen<AsyncValue<AuthStatus>>(authSessionProvider, (previous, next) {
      if (next.value == AuthStatus.authenticated) {
        _startAutoRefresh(generation);
      } else {
        _stopAutoRefresh();
      }
    });

    ref.onDispose(() {
      if (_generation == generation) {
        _stopAutoRefresh();
      }
    });

    final signals = await _fetch();
    if (ref.read(authSessionProvider).value == AuthStatus.authenticated) {
      _startAutoRefresh(generation);
    }
    return _toView(signals, mode);
  }

  /// Test hook: publish a list of signals as if a refresh just completed.
  void ingestSignals(List<Signal> signals) {
    final mode = ref.read(
      settingsControllerProvider.select(
        (async) => async.value?.tradingMode ?? TradingMode.spot,
      ),
    );
    state = AsyncData(_toView(signals, mode));
  }

  Future<void> refresh({bool silent = false}) async {
    try {
      final signals = await _fetch();
      final mode = ref.read(
        settingsControllerProvider.select(
          (async) => async.value?.tradingMode ?? TradingMode.spot,
        ),
      );
      state = AsyncData(_toView(signals, mode));
    } catch (error, stack) {
      if (!silent) {
        state = AsyncError(error, stack);
      }
    }
  }

  Future<List<Signal>> _fetch() => ref.read(getSignalsProvider).call();

  SignalsViewData _toView(List<Signal> signals, TradingMode mode) {
    final sorted = List<Signal>.of(signals)
      ..sort((a, b) => b.createdAt.compareTo(a.createdAt));
    return SignalsViewData(
      mode: mode,
      all: sorted,
      stats: _computeStats(sorted),
      lastUpdated: DateTime.now(),
    );
  }

  SignalsStats _computeStats(List<Signal> signals) {
    final activeCount = signals
        .where((signal) => signal.status == SignalStatus.active)
        .length;

    final now = DateTime.now();
    final cutoff = now.subtract(const Duration(days: 30));
    final recent = signals.where((signal) {
      final date = signal.closedAt ?? signal.createdAt;
      return !date.isBefore(cutoff) && !date.isAfter(now);
    });
    final winRates = recent
        .map((signal) => signal.strategyWinRate)
        .whereType<double>()
        .toList();

    double? winRate;
    if (winRates.isNotEmpty) {
      winRate = winRates.reduce((a, b) => a + b) / winRates.length;
    }

    return SignalsStats(activeCount: activeCount, winRateLast30d: winRate);
  }

  void _startAutoRefresh(int generation) {
    _stopAutoRefresh();
    _refreshTimer = Timer.periodic(SignalsViewData.signalRefreshInterval, (_) {
      if (generation != _generation) {
        return;
      }
      refresh(silent: true);
    });
  }

  void _stopAutoRefresh() {
    _refreshTimer?.cancel();
    _refreshTimer = null;
  }
}

final signalsControllerProvider =
    AsyncNotifierProvider<SignalsController, SignalsViewData>(
      SignalsController.new,
    );
