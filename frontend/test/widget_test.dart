import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:cryptosignals/core/constants/app_constants.dart';
import 'package:cryptosignals/core/theme/app_theme.dart';
import 'package:cryptosignals/main.dart';
import 'package:cryptosignals/presentation/screens/auth/login_screen.dart';
import 'package:cryptosignals/core/di/providers.dart';
import 'package:cryptosignals/domain/entities/app_settings.dart';
import 'package:cryptosignals/domain/entities/market_ticker.dart';
import 'package:cryptosignals/domain/repositories/market_repository.dart';
import 'package:cryptosignals/presentation/providers/markets_controller.dart';
import 'package:cryptosignals/presentation/providers/settings_controller.dart';
import 'package:cryptosignals/presentation/screens/markets/markets_screen.dart';
import 'package:cryptosignals/presentation/screens/settings/settings_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  testWidgets('splash shows branding then opens login', (WidgetTester tester) async {
    await tester.pumpWidget(const ProviderScope(child: ShyBlackApp()));

    expect(find.text(AppConstants.appName), findsOneWidget);
    expect(find.text(AppConstants.tagline), findsOneWidget);

    await tester.pump(const Duration(seconds: 2));
    await tester.pumpAndSettle();

    expect(find.text('LOGIN'), findsOneWidget);
    expect(find.text('Continue with Google'), findsOneWidget);
    expect(find.text('Welcome Back!'), findsOneWidget);
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
        marketRepositoryProvider.overrideWith((ref) => _FakeMarketRepository()),
        marketsPollIntervalProvider.overrideWith((ref) => null),
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
