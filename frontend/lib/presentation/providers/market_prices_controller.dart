import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/constants/api_constants.dart';
import '../../core/di/providers.dart';
import '../../data/datasources/markets_websocket_client.dart';
import '../../data/models/markets_ws_payload.dart';
import '../../domain/entities/app_settings.dart';
import '../../domain/entities/market_ticker.dart';
import 'auth_session.dart';
import 'coin_detail_providers.dart' show MarketRef;

/// Live quotes for every market, keyed by market and then symbol.
///
/// This exists because a symbol is not an identity. `BTCUSDT` is quoted on both spot and
/// futures, at different prices, and a consumer that holds a position on one while browsing
/// the other needs both numbers at once. The Markets browser shows a single market at a
/// time; this book keeps them all, so nothing has to guess which market a lookup meant.
class MarketPriceBook {
  const MarketPriceBook({
    this.byMarket = const {},
    this.connectedMarkets = const {},
  });

  final Map<TradingMode, Map<String, MarketTicker>> byMarket;
  final Set<TradingMode> connectedMarkets;

  bool isConnected(TradingMode mode) => connectedMarkets.contains(mode);

  /// The quote for `symbol` **on `mode`**.
  ///
  /// Returns null rather than falling back to another market. A fallback here would be
  /// indistinguishable from a real quote and would silently price a futures position off
  /// the spot book.
  MarketTicker? tickerFor(TradingMode mode, String symbol) =>
      byMarket[mode]?[symbol.toUpperCase()];

  double? priceFor(TradingMode mode, String symbol) => tickerFor(mode, symbol)?.price;

  bool isEmpty(TradingMode mode) => (byMarket[mode]?.isEmpty ?? true);

  /// Applies a snapshot for one market, replacing that market's previous contents.
  MarketPriceBook withSnapshot(TradingMode mode, List<MarketTicker> tickers) {
    return _copy(
      byMarket: {...byMarket, mode: {for (final t in tickers) t.symbol.toUpperCase(): t}},
      connectedMarkets: {...connectedMarkets, mode},
    );
  }

  /// Applies an incremental batch for one market.
  ///
  /// Tickers whose own `marketType` disagrees with the stream are dropped rather than stored:
  /// a mislabelled quote is worse than a missing one.
  MarketPriceBook withTicks(TradingMode mode, List<MarketTicker> ticks) {
    if (ticks.isEmpty) return this;
    final next = {...(byMarket[mode] ?? const <String, MarketTicker>{})};
    for (final tick in ticks) {
      if (tick.marketType != mode) continue;
      next[tick.symbol.toUpperCase()] = tick;
    }
    return _copy(
      byMarket: {...byMarket, mode: next},
      connectedMarkets: {...connectedMarkets, mode},
    );
  }

  MarketPriceBook _copy({
    Map<TradingMode, Map<String, MarketTicker>>? byMarket,
    Set<TradingMode>? connectedMarkets,
  }) {
    return MarketPriceBook(
      byMarket: byMarket ?? this.byMarket,
      connectedMarkets: connectedMarkets ?? this.connectedMarkets,
    );
  }
}

/// Markets that actually have a ticker stream. Options has none.
const kPricedMarkets = <TradingMode>[TradingMode.spot, TradingMode.futures];

/// Keeps one WebSocket per market and merges them into a single market-keyed book.
class MarketPricesController extends AsyncNotifier<MarketPriceBook> {
  final Map<TradingMode, StreamSubscription<dynamic>> _subscriptions = {};
  final Map<TradingMode, MarketsSocketSession> _sessions = {};
  final Map<TradingMode, Timer> _reconnects = {};
  int _generation = 0;

  @override
  Future<MarketPriceBook> build() async {
    final generation = ++_generation;

    ref.listen<AsyncValue<AuthStatus>>(authSessionProvider, (previous, next) {
      if (next.value == AuthStatus.unauthenticated) {
        _tearDownAll();
      } else if (next.value == AuthStatus.authenticated &&
          previous?.value != AuthStatus.authenticated) {
        _openAll(generation);
      }
    });

    ref.onDispose(() {
      if (_generation == generation) {
        _tearDownAll();
      }
    });

    if (ref.read(authSessionProvider).value != AuthStatus.unauthenticated) {
      _openAll(generation);
    }
    return const MarketPriceBook();
  }

  /// Test hook: apply a wire payload as if it arrived on `mode`'s socket.
  void ingestWireMessage(TradingMode mode, dynamic raw) {
    final payload = MarketsWsPayload.tryParse(raw);
    if (payload == null) return;
    final current = state.value ?? const MarketPriceBook();
    final next = payload.isSnapshot
        ? current.withSnapshot(mode, payload.tickers)
        : current.withTicks(mode, payload.tickers);
    state = AsyncData(next);
  }

  void _openAll(int generation) {
    _tearDownAll();
    for (final mode in kPricedMarkets) {
      _open(mode, generation);
    }
  }

  void _open(TradingMode mode, int generation) {
    if (generation != _generation) return;
    final uri = Uri.parse('${ApiConstants.marketsWsUrl}?mode=${mode.apiParam}');
    try {
      final session = ref.read(marketsSocketConnectorProvider).connect(uri);
      _sessions[mode] = session;
      session.ready.then((_) {
        if (generation != _generation) return;
        final current = state.value;
        if (current != null) {
          state = AsyncData(current._copy(
            connectedMarkets: {...current.connectedMarkets, mode},
          ));
        }
      }).catchError((Object _) {
        _scheduleReconnect(mode, generation);
      });
      _subscriptions[mode] = session.stream.listen(
        (raw) => _onMessage(mode, raw, generation),
        onError: (_) => _scheduleReconnect(mode, generation),
        onDone: () => _scheduleReconnect(mode, generation),
        cancelOnError: true,
      );
    } catch (_) {
      _scheduleReconnect(mode, generation);
    }
  }

  void _onMessage(TradingMode mode, dynamic raw, int generation) {
    if (generation != _generation) return;
    final payload = MarketsWsPayload.tryParse(raw);
    if (payload == null) return;
    // A payload that declares a different market than the socket was opened for is not
    // trusted; it would place a quote in the wrong book.
    if (payload.mode.isNotEmpty && payload.mode.toUpperCase() != mode.apiParam) {
      return;
    }
    final current = state.value ?? const MarketPriceBook();
    state = AsyncData(
      payload.isSnapshot
          ? current.withSnapshot(mode, payload.tickers)
          : current.withTicks(mode, payload.tickers),
    );
  }

  void _scheduleReconnect(TradingMode mode, int generation) {
    if (generation != _generation) return;
    _reconnects[mode]?.cancel();
    _reconnects[mode] = Timer(const Duration(seconds: 3), () => _open(mode, generation));
  }

  void _tearDownAll() {
    for (final subscription in _subscriptions.values) {
      unawaited(subscription.cancel());
    }
    _subscriptions.clear();
    for (final session in _sessions.values) {
      unawaited(session.close());
    }
    _sessions.clear();
    for (final timer in _reconnects.values) {
      timer.cancel();
    }
    _reconnects.clear();
  }
}

final marketPricesControllerProvider =
    AsyncNotifierProvider<MarketPricesController, MarketPriceBook>(MarketPricesController.new);

/// Convenience lookup for a single instrument.
final marketPriceProvider = Provider.family<double?, MarketRef>((ref, target) {
  return ref.watch(marketPricesControllerProvider).value?.priceFor(target.market, target.symbol);
});