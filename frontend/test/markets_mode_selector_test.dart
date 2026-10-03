import 'dart:async';

import 'package:cryptosignals/core/di/providers.dart';
import 'package:cryptosignals/core/theme/app_theme.dart';
import 'package:cryptosignals/data/datasources/markets_websocket_client.dart';
import 'package:cryptosignals/domain/entities/app_settings.dart';
import 'package:cryptosignals/domain/entities/kline_candle.dart';
import 'package:cryptosignals/domain/entities/market_ticker.dart';
import 'package:cryptosignals/domain/repositories/market_repository.dart';
import 'package:cryptosignals/domain/repositories/settings_repository.dart';
import 'package:cryptosignals/presentation/providers/auth_session.dart';
import 'package:cryptosignals/presentation/providers/markets_controller.dart';
import 'package:cryptosignals/presentation/screens/markets/markets_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

/// The Markets browser must offer Spot and Futures as separate views.
///
/// A merged "All Markets" list would place the spot pair and the futures perpetual that
/// share a symbol side by side as if they were one instrument, and tapping one would
/// silently choose which market is meant.
void main() {
  testWidgets(
    'markets offers SPOT and FUTURES views and no merged All Markets',
    (WidgetTester tester) async {
      tester.view.physicalSize = const Size(1200, 2000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      final requestedModes = <TradingMode>[];
      final container = ProviderContainer(
        overrides: [
          authSessionProvider.overrideWith(() => _AuthedSessionController()),
          marketRepositoryProvider.overrideWith(
            (ref) => _RecordingMarketRepository(requestedModes),
          ),
          marketsSocketConnectorProvider.overrideWith(
            (ref) => const _IdleSocketConnector(),
          ),
          settingsRepositoryProvider.overrideWith(
            (ref) => _FakeSettingsRepository(),
          ),
        ],
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp(
            theme: AppTheme.dark(),
            darkTheme: AppTheme.dark(),
            themeMode: ThemeMode.dark,
            home: const Scaffold(body: MarketsScreen()),
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));

      // Both markets are offered.
      expect(find.text('SPOT'), findsOneWidget);
      expect(find.text('FUTURES'), findsWidgets);

      // The merged view is gone.
      expect(find.text('All Markets'), findsNothing);
      expect(find.text('Markets'), findsOneWidget);

      // Spot is the default view.
      expect(container.read(marketsModeProvider), TradingMode.spot);
      expect(requestedModes, contains(TradingMode.spot));

      // Selecting Futures asks the backend for the futures universe.
      await tester.tap(find.text('FUTURES').first);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));

      expect(container.read(marketsModeProvider), TradingMode.futures);
      expect(requestedModes, contains(TradingMode.futures));
    },
  );
}

class _AuthedSessionController extends AuthSessionController {
  @override
  Future<AuthStatus> build() async => AuthStatus.authenticated;
}

class _RecordingMarketRepository implements MarketRepository {
  _RecordingMarketRepository(this.requestedModes);

  final List<TradingMode> requestedModes;

  @override
  Future<MarketSnapshot> getMarkets(TradingMode mode) async {
    requestedModes.add(mode);
    return MarketSnapshot(
      mode: mode.apiParam,
      tickers: [
        _ticker('BTCUSDT', mode == TradingMode.spot ? 100000 : 100500, mode),
      ],
    );
  }

  @override
  Future<MarketSnapshot> getGainers(TradingMode mode) => getMarkets(mode);

  @override
  Future<MarketSnapshot> getLosers(TradingMode mode) => getMarkets(mode);

  @override
  Future<MarketTicker> getTicker(String symbol, TradingMode mode) async =>
      _ticker(symbol, 1, mode);

  @override
  Future<List<KlineCandle>> getKlines({
    required String symbol,
    required String interval,
    required int limit,
    required TradingMode mode,
  }) async => const [];
}

/// Builds a ticker carrying the metadata the real API now supplies.
MarketTicker _ticker(String symbol, double price, TradingMode market) =>
    MarketTicker(
      symbol: symbol,
      name: 'BTC',
      price: price,
      change24h: 1,
      changePercent24h: 1,
      volume24h: 10,
      high24h: price,
      low24h: price,
      marketType: market,
      exchangeSymbol: symbol,
      displaySymbol: market == TradingMode.spot
          ? 'BTC/USDT'
          : 'BTCUSDT Perpetual',
      baseAsset: 'BTC',
      quoteAsset: 'USDT',
      contractType: market == TradingMode.spot ? null : 'PERPETUAL',
    );

class _IdleSocketConnector implements MarketsSocketConnector {
  const _IdleSocketConnector();

  @override
  MarketsSocketSession connect(Uri uri) {
    // A stream that never emits and never closes: an idle connection. A closed stream would
    // fire onDone and schedule a reconnect, leaving a pending timer at teardown.
    return MarketsSocketSession(
      stream: StreamController<dynamic>().stream,
      ready: Future<void>.value(),
      close: () async {},
    );
  }
}

class _FakeSettingsRepository implements SettingsRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}
