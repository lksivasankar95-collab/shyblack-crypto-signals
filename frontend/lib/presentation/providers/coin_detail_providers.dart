import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/di/providers.dart';
import '../../domain/entities/app_settings.dart';
import '../../domain/entities/kline_candle.dart';
import '../../domain/entities/market_ticker.dart';
import 'settings_controller.dart';

enum ChartTimeframe {
  h1('1h', '1h'),
  h4('4h', '4h'),
  d1('1D', '1d'),
  w1('1W', '1w'),
  m1('1M', '1M');

  const ChartTimeframe(this.label, this.interval);
  final String label;
  final String interval;
}

class KlineQuery {
  const KlineQuery({required this.symbol, required this.interval});

  final String symbol;
  final String interval;

  @override
  bool operator ==(Object other) =>
      other is KlineQuery && other.symbol == symbol && other.interval == interval;

  @override
  int get hashCode => Object.hash(symbol, interval);
}

class LocalWatchlist extends Notifier<Set<String>> {
  @override
  Set<String> build() => <String>{};

  void toggle(String symbol) {
    final next = {...state};
    if (!next.add(symbol)) {
      next.remove(symbol);
    }
    state = next;
  }
}

final localWatchlistProvider = NotifierProvider<LocalWatchlist, Set<String>>(LocalWatchlist.new);

final coinTickerPollIntervalProvider = Provider<Duration?>((ref) => const Duration(seconds: 8));

TradingMode _mode(Ref ref) => ref.watch(
      settingsControllerProvider.select((async) => async.value?.tradingMode ?? TradingMode.spot),
    );

final coinTickerProvider = FutureProvider.autoDispose.family<MarketTicker, String>((ref, symbol) async {
  final mode = _mode(ref);
  final poll = ref.watch(coinTickerPollIntervalProvider);
  if (poll != null) {
    final timer = Timer.periodic(poll, (_) => ref.invalidateSelf());
    ref.onDispose(timer.cancel);
  }
  return ref.read(getMarketTickerProvider).call(symbol, mode);
});

final coinKlinesProvider =
    FutureProvider.autoDispose.family<List<KlineCandle>, KlineQuery>((ref, query) async {
  final mode = _mode(ref);
  return ref.read(getKlinesProvider).call(
        symbol: query.symbol,
        interval: query.interval,
        mode: mode,
      );
});

class PerformanceChange {
  const PerformanceChange({this.hour, this.day, this.week, this.month});

  final double? hour;
  final double? day;
  final double? week;
  final double? month;
}

PerformanceChange performanceFromKlines(List<KlineCandle> klines, {double? changePercent24h}) {
  if (klines.isEmpty) {
    return PerformanceChange(day: changePercent24h);
  }
  final last = klines.last;
  return PerformanceChange(
    hour: _pctSince(klines, last, const Duration(hours: 1)),
    day: changePercent24h ?? _pctSince(klines, last, const Duration(days: 1)),
    week: _pctSince(klines, last, const Duration(days: 7)),
    month: _pctSince(klines, last, const Duration(days: 30)),
  );
}

double? _pctSince(List<KlineCandle> klines, KlineCandle last, Duration lookback) {
  final target = last.openTime - lookback.inMilliseconds;
  KlineCandle? match;
  for (final candle in klines) {
    if (candle.openTime <= target) {
      match = candle;
    }
  }
  if (match == null || match.close == 0) {
    return null;
  }
  final slack = lookback.inMilliseconds * 0.4;
  if ((match.openTime - target).abs() > slack && match.openTime > target) {
    return null;
  }
  return (last.close - match.close) / match.close * 100;
}

List<MarketTicker> similarCoins(List<MarketTicker> all, String symbol, {int limit = 4}) {
  final others = all.where((ticker) => ticker.symbol != symbol).toList()
    ..sort((a, b) => b.volume24h.compareTo(a.volume24h));
  return others.take(limit).toList();
}
