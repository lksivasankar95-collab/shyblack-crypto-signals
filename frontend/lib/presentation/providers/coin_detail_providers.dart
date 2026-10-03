import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/di/providers.dart';
import '../../domain/entities/app_settings.dart';
import '../../domain/entities/kline_candle.dart';
import '../../domain/entities/market_ticker.dart';
import 'markets_controller.dart';

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

/// A symbol qualified by the market it belongs to.
///
/// Keying provider families on the bare symbol is what allowed a spot price to satisfy a
/// futures request, or the reverse. The market is part of the key here so the two can
/// never be served by one another's cache entry.
class MarketRef {
  const MarketRef({required this.symbol, this.market = TradingMode.spot});

  final String symbol;
  final TradingMode market;

  @override
  bool operator ==(Object other) =>
      other is MarketRef && other.symbol == symbol && other.market == market;

  @override
  int get hashCode => Object.hash(symbol, market);

  @override
  String toString() => '${market.apiParam}:$symbol';
}

class KlineQuery {
  const KlineQuery({
    required this.symbol,
    required this.interval,
    this.market = TradingMode.spot,
  });

  final String symbol;
  final String interval;

  /// Market whose candles to load. Spot and futures candles differ and are not interchangeable.
  final TradingMode market;

  MarketRef get ref => MarketRef(symbol: symbol, market: market);

  @override
  bool operator ==(Object other) =>
      other is KlineQuery &&
      other.symbol == symbol &&
      other.interval == interval &&
      other.market == market;

  @override
  int get hashCode => Object.hash(symbol, interval, market);
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

final coinTickerRestProvider =
    FutureProvider.autoDispose.family<MarketTicker, MarketRef>((ref, target) async {
  return ref.read(getMarketTickerProvider).call(target.symbol, target.market);
});

final coinTickerProvider =
    Provider.autoDispose.family<AsyncValue<MarketTicker>, MarketRef>((ref, target) {
  // Only a ticker from the requested market may satisfy this lookup. Without the mode check
  // a spot cache entry would silently answer a futures request for the same symbol.
  final live = ref.watch(
    marketsControllerProvider.select(
      (async) => async.value?.tickerFor(target.symbol),
    ),
  );
  if (live != null && live.marketType == target.market) {
    return AsyncData(live);
  }
  return ref.watch(coinTickerRestProvider(target));
});

final coinKlinesProvider =
    FutureProvider.autoDispose.family<List<KlineCandle>, KlineQuery>((ref, query) async {
  return ref.read(getKlinesProvider).call(
        symbol: query.symbol,
        interval: query.interval,
        mode: query.market,
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
