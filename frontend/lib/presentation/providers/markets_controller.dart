import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/constants/api_constants.dart';
import '../../core/di/providers.dart';
import '../../data/datasources/markets_websocket_client.dart';
import '../../data/models/markets_ws_payload.dart';
import '../../domain/entities/app_settings.dart';
import '../../domain/entities/market_ticker.dart';
import 'auth_session.dart';
import 'settings_controller.dart';

class MarketsViewData {
  const MarketsViewData({
    required this.mode,
    required this.all,
    required this.symbols,
    required this.bySymbol,
    required this.gainers,
    required this.losers,
    this.message,
    this.connected = false,
    this.reconnecting = false,
  });

  static const leaderboardLimit = 10;

  final TradingMode mode;
  final String? message;
  final List<MarketTicker> all;
  final List<String> symbols;
  final Map<String, MarketTicker> bySymbol;
  final List<MarketTicker> gainers;
  final List<MarketTicker> losers;
  final bool connected;
  final bool reconnecting;

  bool get isOptionsUnavailable => mode == TradingMode.options;

  MarketsViewData copyWith({
    TradingMode? mode,
    String? message,
    List<MarketTicker>? all,
    List<String>? symbols,
    Map<String, MarketTicker>? bySymbol,
    List<MarketTicker>? gainers,
    List<MarketTicker>? losers,
    bool? connected,
    bool? reconnecting,
    bool clearMessage = false,
  }) {
    return MarketsViewData(
      mode: mode ?? this.mode,
      message: clearMessage ? null : (message ?? this.message),
      all: all ?? this.all,
      symbols: symbols ?? this.symbols,
      bySymbol: bySymbol ?? this.bySymbol,
      gainers: gainers ?? this.gainers,
      losers: losers ?? this.losers,
      connected: connected ?? this.connected,
      reconnecting: reconnecting ?? this.reconnecting,
    );
  }

  static MarketsViewData fromTickers({
    required TradingMode mode,
    required List<MarketTicker> tickers,
    String? message,
    bool connected = false,
    bool reconnecting = false,
  }) {
    final bySymbol = <String, MarketTicker>{
      for (final ticker in tickers) ticker.symbol: ticker,
    };
    return MarketsViewData(
      mode: mode,
      message: message,
      all: tickers,
      symbols: [for (final ticker in tickers) ticker.symbol],
      bySymbol: bySymbol,
      gainers: _leaderboard(tickers, gainers: true),
      losers: _leaderboard(tickers, gainers: false),
      connected: connected,
      reconnecting: reconnecting,
    );
  }

  MarketsViewData mergeTicks(List<MarketTicker> ticks) {
    if (ticks.isEmpty) {
      return this;
    }
    final nextAll = List<MarketTicker>.of(all);
    final indexOf = <String, int>{
      for (var i = 0; i < nextAll.length; i++) nextAll[i].symbol: i,
    };
    var orderChanged = false;
    for (final tick in ticks) {
      final index = indexOf[tick.symbol];
      if (index == null) {
        indexOf[tick.symbol] = nextAll.length;
        nextAll.add(tick);
        orderChanged = true;
      } else if (nextAll[index] != tick) {
        nextAll[index] = tick;
      }
    }
    final nextBySymbol = <String, MarketTicker>{
      for (final ticker in nextAll) ticker.symbol: ticker,
    };
    return MarketsViewData(
      mode: mode,
      message: message,
      all: nextAll,
      symbols: orderChanged ? [for (final ticker in nextAll) ticker.symbol] : symbols,
      bySymbol: nextBySymbol,
      gainers: _leaderboard(nextAll, gainers: true),
      losers: _leaderboard(nextAll, gainers: false),
      connected: true,
      reconnecting: false,
    );
  }

  static List<MarketTicker> _leaderboard(List<MarketTicker> source, {required bool gainers}) {
    final copy = List<MarketTicker>.of(source)
      ..sort(
        (a, b) => gainers
            ? b.changePercent24h.compareTo(a.changePercent24h)
            : a.changePercent24h.compareTo(b.changePercent24h),
      );
    if (copy.length <= leaderboardLimit) {
      return copy;
    }
    return copy.sublist(0, leaderboardLimit);
  }
}

class MarketsController extends AsyncNotifier<MarketsViewData> {
  StreamSubscription<dynamic>? _subscription;
  MarketsSocketSession? _session;
  Timer? _reconnectTimer;
  Timer? _bannerTimer;
  int _generation = 0;
  int _attempt = 0;

  @override
  Future<MarketsViewData> build() async {
    final generation = ++_generation;
    final mode = ref.watch(
      settingsControllerProvider.select(
        (async) => async.value?.tradingMode ?? TradingMode.spot,
      ),
    );

    ref.listen<AsyncValue<AuthStatus>>(authSessionProvider, (previous, next) {
      if (next.value == AuthStatus.unauthenticated) {
        _tearDownSocket();
      } else if (next.value == AuthStatus.authenticated && previous?.value != AuthStatus.authenticated) {
        _openSocket(mode, _generation);
      }
    });

    ref.onDispose(() {
      if (_generation == generation) {
        _tearDownSocket();
      }
    });

    final initial = await _fetch(mode);
    if (generation != _generation) {
      return initial;
    }
    if (ref.read(authSessionProvider).value != AuthStatus.unauthenticated) {
      _openSocket(mode, generation);
    }
    return initial;
  }

  /// Test hook: apply a snapshot/tickers JSON payload as if it arrived on the socket.
  void ingestWireMessage(dynamic raw) {
    final current = state.value;
    if (current == null) {
      return;
    }
    _onSocketMessage(raw, current.mode, _generation);
  }

  Future<void> refresh({bool silent = false}) async {
    final mode = ref.read(
      settingsControllerProvider.select(
        (async) => async.value?.tradingMode ?? TradingMode.spot,
      ),
    );
    try {
      final next = await _fetch(mode);
      final current = state.value;
      state = AsyncData(
        next.copyWith(
          connected: current?.connected ?? false,
          reconnecting: current?.reconnecting ?? false,
        ),
      );
    } catch (error, stack) {
      if (!silent) {
        state = AsyncError(error, stack);
      }
    }
  }

  Future<MarketsViewData> _fetch(TradingMode mode) async {
    final snapshot = await ref.read(getMarketsProvider).call(mode);
    return MarketsViewData.fromTickers(
      mode: mode,
      tickers: snapshot.tickers,
      message: snapshot.message,
    );
  }

  void _openSocket(TradingMode mode, int generation) {
    _tearDownSocket(cancelReconnect: true);
    final uri = Uri.parse('${ApiConstants.marketsWsUrl}?mode=${mode.apiParam}');
    final connector = ref.read(marketsSocketConnectorProvider);
    try {
      final session = connector.connect(uri);
      _session = session;
      session.ready.then((_) {
        if (generation != _generation) {
          return;
        }
        _attempt = 0;
        _bannerTimer?.cancel();
        final current = state.value;
        if (current != null) {
          state = AsyncData(current.copyWith(connected: true, reconnecting: false));
        }
      }).catchError((_) {
        _scheduleReconnect(mode, generation);
      });
      _subscription = session.stream.listen(
        (raw) => _onSocketMessage(raw, mode, generation),
        onError: (_) => _scheduleReconnect(mode, generation),
        onDone: () => _scheduleReconnect(mode, generation),
        cancelOnError: true,
      );
    } catch (_) {
      _scheduleReconnect(mode, generation);
    }
  }

  void _onSocketMessage(dynamic raw, TradingMode expected, int generation) {
    if (generation != _generation) {
      return;
    }
    final payload = MarketsWsPayload.tryParse(raw);
    if (payload == null) {
      return;
    }
    if (payload.mode.isNotEmpty && payload.mode.toUpperCase() != expected.apiParam) {
      _openSocket(expected, generation);
      return;
    }
    _attempt = 0;
    _bannerTimer?.cancel();
    final current = state.value;
    if (payload.isSnapshot) {
      state = AsyncData(
        MarketsViewData.fromTickers(
          mode: expected,
          tickers: payload.tickers,
          message: payload.message,
          connected: true,
        ),
      );
      return;
    }
    if (payload.isTickers && current != null && current.mode == expected) {
      state = AsyncData(current.mergeTicks(payload.tickers));
    }
  }

  void _scheduleReconnect(TradingMode mode, int generation) {
    if (generation != _generation) {
      return;
    }
    final current = state.value;
    if (current != null && (current.connected || !current.reconnecting)) {
      state = AsyncData(current.copyWith(connected: false));
    }
    _bannerTimer?.cancel();
    _bannerTimer = Timer(const Duration(seconds: 2), () {
      if (generation != _generation) {
        return;
      }
      final latest = state.value;
      if (latest != null && !latest.connected) {
        state = AsyncData(latest.copyWith(reconnecting: true));
      }
    });
    _reconnectTimer?.cancel();
    final delayMs = (500 * (1 << _attempt.clamp(0, 4))).clamp(500, 10000);
    _attempt++;
    _reconnectTimer = Timer(Duration(milliseconds: delayMs), () {
      if (generation != _generation) {
        return;
      }
      _openSocket(mode, generation);
    });
  }

  void _tearDownSocket({bool cancelReconnect = true}) {
    _subscription?.cancel();
    _subscription = null;
    final session = _session;
    _session = null;
    if (session != null) {
      unawaited(session.close());
    }
    if (cancelReconnect) {
      _reconnectTimer?.cancel();
      _reconnectTimer = null;
      _bannerTimer?.cancel();
      _bannerTimer = null;
    }
  }
}

final marketsControllerProvider = AsyncNotifierProvider<MarketsController, MarketsViewData>(
  MarketsController.new,
);
