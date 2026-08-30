import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/di/providers.dart';
import '../../domain/entities/app_settings.dart';
import '../../domain/entities/market_ticker.dart';
import 'settings_controller.dart';

class MarketsViewData {
  const MarketsViewData({
    required this.mode,
    required this.all,
    required this.gainers,
    required this.losers,
    this.message,
  });

  final TradingMode mode;
  final String? message;
  final List<MarketTicker> all;
  final List<MarketTicker> gainers;
  final List<MarketTicker> losers;

  bool get isOptionsUnavailable => mode == TradingMode.options;
}

class MarketsController extends AsyncNotifier<MarketsViewData> {
  @override
  Future<MarketsViewData> build() async {
    final mode = ref.watch(
      settingsControllerProvider.select(
        (async) => async.value?.tradingMode ?? TradingMode.spot,
      ),
    );
    final interval = ref.watch(marketsPollIntervalProvider);
    if (interval != null) {
      final timer = Timer.periodic(interval, (_) {
        unawaited(refresh(silent: true));
      });
      ref.onDispose(timer.cancel);
    }
    return _fetch(mode);
  }

  Future<void> refresh({bool silent = false}) async {
    final mode = ref.read(
      settingsControllerProvider.select(
        (async) => async.value?.tradingMode ?? TradingMode.spot,
      ),
    );
    try {
      final next = await _fetch(mode);
      state = AsyncData(next);
    } catch (error, stack) {
      if (!silent) {
        state = AsyncError(error, stack);
      }
    }
  }

  Future<MarketsViewData> _fetch(TradingMode mode) async {
    final results = await Future.wait([
      ref.read(getMarketsProvider).call(mode),
      ref.read(getMarketGainersProvider).call(mode),
      ref.read(getMarketLosersProvider).call(mode),
    ]);
    final all = results[0];
    return MarketsViewData(
      mode: mode,
      message: all.message,
      all: all.tickers,
      gainers: results[1].tickers,
      losers: results[2].tickers,
    );
  }
}

final marketsPollIntervalProvider = Provider<Duration?>((ref) => const Duration(seconds: 20));

final marketsControllerProvider = AsyncNotifierProvider<MarketsController, MarketsViewData>(
  MarketsController.new,
);
