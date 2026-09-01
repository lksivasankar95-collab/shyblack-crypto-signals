import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:cryptosignals/core/constants/app_constants.dart';
import 'package:cryptosignals/core/theme/app_theme.dart';
import 'package:cryptosignals/main.dart';
import 'package:cryptosignals/presentation/screens/auth/login_screen.dart';
import 'package:cryptosignals/core/di/providers.dart';
import 'package:cryptosignals/data/datasources/markets_websocket_client.dart';
import 'package:cryptosignals/domain/entities/auth_tokens.dart';
import 'package:cryptosignals/domain/entities/app_settings.dart';
import 'package:cryptosignals/domain/entities/kline_candle.dart';
import 'package:cryptosignals/domain/entities/market_ticker.dart';
import 'package:cryptosignals/domain/repositories/auth_repository.dart';
import 'package:cryptosignals/domain/repositories/market_repository.dart';
import 'package:cryptosignals/presentation/providers/markets_controller.dart';
import 'package:cryptosignals/presentation/providers/settings_controller.dart';
import 'package:cryptosignals/presentation/screens/markets/markets_screen.dart';
import 'package:cryptosignals/presentation/screens/settings/settings_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  testWidgets('splash shows branding then opens login', (WidgetTester tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authRepositoryProvider.overrideWith((ref) => _SessionAuthRepository(restored: false)),
        ],
        child: const ShyBlackApp(),
      ),
    );

    expect(find.text(AppConstants.appName), findsOneWidget);
    expect(find.text(AppConstants.tagline), findsOneWidget);

    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.text('LOGIN'), findsOneWidget);
    expect(find.text('Continue with Google'), findsOneWidget);
    expect(find.text('Welcome Back!'), findsOneWidget);
  });

  testWidgets('restored session opens the main shell without login', (WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({});
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authRepositoryProvider.overrideWith((ref) => _SessionAuthRepository(restored: true)),
          marketRepositoryProvider.overrideWith((ref) => _FakeMarketRepository()),
          marketsSocketConnectorProvider.overrideWith((ref) => const _IdleSocketConnector()),
        ],
        child: const ShyBlackApp(),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.text('LOGIN'), findsNothing);
    expect(find.text('Markets'), findsWidgets);
  });

  testWidgets('login fits iPhone SE height without scrolling', (WidgetTester tester) async {
    tester.view.physicalSize = const Size(375, 667);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final theme = AppTheme.dark();
    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(
          theme: theme,
          darkTheme: theme,
          themeMode: ThemeMode.dark,
          home: const LoginScreen(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.text('Welcome Back!'), findsOneWidget);
    expect(find.text('LOGIN'), findsOneWidget);
    expect(find.text('Continue with Google'), findsOneWidget);

    final scrollable = tester.state<ScrollableState>(find.byType(Scrollable).first);
    expect(
      scrollable.position.maxScrollExtent,
      0,
      reason: 'Login should fit on a 375x667 screen without scrolling',
    );
  });

  testWidgets('settings shows profile and trading mode options', (WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({});
    final theme = AppTheme.dark();
    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(
          theme: theme,
          darkTheme: theme,
          themeMode: ThemeMode.dark,
          home: const SettingsScreen(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Ada Trader'), findsOneWidget);
    expect(find.text('Spot'), findsOneWidget);
    expect(find.text('Futures'), findsOneWidget);
    expect(find.text('Options'), findsOneWidget);
    expect(find.text('Paper Trading Account'), findsOneWidget);
    expect(find.text('LOGOUT'), findsOneWidget);

    await tester.tap(find.text('Futures'));
    await tester.pumpAndSettle();
    expect(find.text('Active'), findsWidgets);
  });

  testWidgets('markets lists coins, searches, and follows trading mode', (WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({});
    final container = ProviderContainer(
      overrides: [
        authRepositoryProvider.overrideWith((ref) => _SessionAuthRepository(restored: true)),
        marketRepositoryProvider.overrideWith((ref) => _FakeMarketRepository()),
        marketsSocketConnectorProvider.overrideWith((ref) => const _IdleSocketConnector()),
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

    expect(find.text('BTC'), findsOneWidget);
    expect(find.text('ETH'), findsOneWidget);
    expect(find.text('Search symbol or name'), findsOneWidget);

    await tester.enterText(find.byType(TextField), 'eth');
    await tester.pump();
    expect(find.text('ETH'), findsOneWidget);
    expect(find.text('BTC'), findsNothing);

    await tester.enterText(find.byType(TextField), '');
    await tester.pump();

    await container.read(settingsControllerProvider.notifier).setTradingMode(TradingMode.futures);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    expect(find.text('BTC'), findsOneWidget);
    expect(find.text('ETH'), findsNothing);

    await container.read(settingsControllerProvider.notifier).setTradingMode(TradingMode.options);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    expect(find.text("Options trading data isn't available yet"), findsOneWidget);
  });

  testWidgets('tapping a market opens coin detail', (WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({});
    final container = ProviderContainer(
      overrides: [
        authRepositoryProvider.overrideWith((ref) => _SessionAuthRepository(restored: true)),
        marketRepositoryProvider.overrideWith((ref) => _FakeMarketRepository()),
        marketsSocketConnectorProvider.overrideWith((ref) => const _IdleSocketConnector()),
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

    await tester.tap(find.text('BTC'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.text('Trade BTC'), findsOneWidget);
    expect(find.text('Bitcoin / Tether'), findsOneWidget);
    expect(find.text('1D'), findsOneWidget);
  });

  testWidgets('websocket ticks patch a market row in place', (WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({});
    final ticks = StreamController<dynamic>.broadcast();
    addTearDown(ticks.close);
    final container = ProviderContainer(
      overrides: [
        authRepositoryProvider.overrideWith((ref) => _SessionAuthRepository(restored: true)),
        marketRepositoryProvider.overrideWith((ref) => _FakeMarketRepository()),
        marketsSocketConnectorProvider.overrideWith((ref) => _ScriptedSocketConnector(ticks)),
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

    expect(find.text('65000.00'), findsOneWidget);

    container.read(marketsControllerProvider.notifier).ingestWireMessage(
      jsonEncode({
        'type': 'tickers',
        'mode': 'SPOT',
        'tickers': [
          {
            'symbol': 'BTCUSDT',
            'name': 'Bitcoin',
            'price': 66123,
            'change24h': 2,
            'changePercent24h': 4.5,
            'volume24h': 1000,
            'high24h': 67000,
            'low24h': 64000,
          },
        ],
      }),
    );
    await tester.pump();

    expect(find.text('66123.00'), findsOneWidget);
    expect(find.text('65000.00'), findsNothing);
    expect(find.text('+4.50%'), findsOneWidget);
  });
}

MarketTicker _ticker(String symbol, String name, double price, double changePct) {
  return MarketTicker(
    symbol: symbol,
    name: name,
    price: price,
    change24h: 1,
    changePercent24h: changePct,
    volume24h: 1000,
    high24h: price + 10,
    low24h: price - 10,
  );
}

class _FakeMarketRepository implements MarketRepository {
  @override
  Future<MarketSnapshot> getMarkets(TradingMode mode) async => _snapshot(mode);

  @override
  Future<MarketSnapshot> getGainers(TradingMode mode) async => _snapshot(mode);

  @override
  Future<MarketSnapshot> getLosers(TradingMode mode) async => _snapshot(mode);

  @override
  Future<MarketTicker> getTicker(String symbol, TradingMode mode) async {
    return _snapshot(mode).tickers.firstWhere(
          (item) => item.symbol == symbol,
          orElse: () => _ticker(symbol, symbol, 1, 0),
        );
  }

  @override
  Future<List<KlineCandle>> getKlines({
    required String symbol,
    required String interval,
    required int limit,
    required TradingMode mode,
  }) async {
    return [
      for (var i = 0; i < 8; i++)
        KlineCandle(
          openTime: 1700000000000 + i * 86400000,
          open: 100 + i.toDouble(),
          high: 102 + i.toDouble(),
          low: 99 + i.toDouble(),
          close: 101 + i.toDouble(),
          volume: 50,
          closeTime: 1700000000000 + (i + 1) * 86400000,
        ),
    ];
  }

  MarketSnapshot _snapshot(TradingMode mode) {
    if (mode == TradingMode.options) {
      return const MarketSnapshot(
        mode: 'OPTIONS',
        message: 'Options data not yet available',
        tickers: [],
      );
    }
    if (mode == TradingMode.futures) {
      return MarketSnapshot(mode: 'FUTURES', tickers: [_ticker('BTCUSDT', 'Bitcoin', 65100, 3.1)]);
    }
    return MarketSnapshot(
      mode: 'SPOT',
      tickers: [
        _ticker('BTCUSDT', 'Bitcoin', 65000, 2.5),
        _ticker('ETHUSDT', 'Ethereum', 3400, -1.2),
      ],
    );
  }
}

class _IdleSocketConnector implements MarketsSocketConnector {
  const _IdleSocketConnector();

  @override
  MarketsSocketSession connect(Uri uri) {
    return MarketsSocketSession(
      stream: StreamController<dynamic>().stream,
      ready: Future.value(),
      close: () async {},
    );
  }
}

class _ScriptedSocketConnector implements MarketsSocketConnector {
  const _ScriptedSocketConnector(this.messages);

  final StreamController<dynamic> messages;

  @override
  MarketsSocketSession connect(Uri uri) {
    return MarketsSocketSession(
      stream: messages.stream,
      ready: Future.value(),
      close: () async {},
    );
  }
}

class _SessionAuthRepository implements AuthRepository {
  _SessionAuthRepository({required this.restored});

  final bool restored;

  @override
  Future<AuthTokens> login({required String email, required String password}) {
    throw UnimplementedError();
  }

  @override
  Future<AuthTokens> loginWithGoogle({String? idToken}) {
    throw UnimplementedError();
  }

  @override
  Future<void> signup({
    required String fullName,
    required String email,
    required String password,
  }) async {}

  @override
  Future<bool> restoreSession() async => restored;

  @override
  Future<void> logout() async {}
}
