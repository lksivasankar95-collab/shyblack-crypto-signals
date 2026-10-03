import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:cryptosignals/core/di/providers.dart';
import 'package:cryptosignals/core/theme/app_theme.dart';
import 'package:cryptosignals/data/datasources/markets_websocket_client.dart';
import 'package:cryptosignals/domain/entities/app_settings.dart';
import 'package:cryptosignals/domain/entities/kline_candle.dart';
import 'package:cryptosignals/domain/entities/market_ticker.dart';
import 'package:cryptosignals/domain/entities/paper_account.dart';
import 'package:cryptosignals/domain/entities/paper_performance.dart';
import 'package:cryptosignals/domain/entities/paper_position.dart';
import 'package:cryptosignals/domain/repositories/market_repository.dart';
import 'package:cryptosignals/domain/repositories/paper_trading_repository.dart';
import 'package:cryptosignals/presentation/providers/auth_session.dart';
import 'package:cryptosignals/presentation/providers/market_prices_controller.dart';
import 'package:cryptosignals/presentation/providers/markets_controller.dart';
import 'package:cryptosignals/presentation/screens/paper_trading/paper_trading_screen.dart';

MarketTicker _ticker(String symbol, double price, [TradingMode market = TradingMode.spot]) => MarketTicker(
      symbol: symbol,
      name: symbol,
      price: price,
      change24h: 0,
      changePercent24h: 0,
      volume24h: 0,
      high24h: price,
      low24h: price,
      marketType: market,
      exchangeSymbol: symbol,
      baseAsset: symbol.length > 4 ? symbol.substring(0, symbol.length - 4) : symbol,
      quoteAsset: 'USDT',
    );

/// Fake shared market book so there is exactly ONE stream per market (no socket
/// here) and each card reads its live price from its own market.
class _FakeMarketPricesController extends MarketPricesController {
  _FakeMarketPricesController(this._book);
  MarketPriceBook _book;

  /// Test hook: push a new book exactly as WebSocket ticks would.
  void emit(MarketPriceBook book) {
    _book = book;
    state = AsyncData(book);
  }

  @override
  Future<MarketPriceBook> build() async => _book;
}

/// Builds a book holding `prices` on [market].
MarketPriceBook _book(
  Map<String, double> prices, {
  TradingMode market = TradingMode.spot,
  bool connected = true,
}) {
  final tickers = [
    for (final e in prices.entries) _ticker(e.key, e.value, market)
  ];
  return MarketPriceBook(
    byMarket: {
      market: {for (final t in tickers) t.symbol: t},
    },
    connectedMarkets: connected ? {market} : const {},
  );
}

Widget _app(
  _FakePaperTradingRepository repo, {
  Map<String, double> live = const {'BTCUSDT': 40500},
  TradingMode market = TradingMode.futures,
  MarketPriceBook? book,
  bool connected = true,
}) =>
    ProviderScope(
      overrides: [
        paperTradingRepositoryProvider.overrideWith((ref) => repo),
        marketPricesControllerProvider.overrideWith(
          () => _FakeMarketPricesController(
            book ?? _book(live, market: market, connected: connected),
          ),
        ),
      ],
      child: MaterialApp(
        theme: AppTheme.dark(),
        darkTheme: AppTheme.dark(),
        themeMode: ThemeMode.dark,
        home: const PaperTradingScreen(),
      ),
    );

Future<void> _load(
  WidgetTester tester,
  _FakePaperTradingRepository repo, {
  Map<String, double> live = const {'BTCUSDT': 40500},
  TradingMode market = TradingMode.futures,
  MarketPriceBook? book,
  bool connected = true,
}) async {
  await tester.pumpWidget(_app(repo, live: live, market: market, book: book, connected: connected));
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 50));
}

void main() {
  testWidgets('position card shows market type, strategy, opened time and live price',
      (WidgetTester tester) async {
    final repo = _FakePaperTradingRepository();
    await _load(tester, repo);

    expect(find.text('SIMULATION'), findsOneWidget);
    expect(find.text('BTCUSDT'), findsWidgets);
    // Market type from signal metadata (not inferred).
    expect(find.text('FUTURES'), findsOneWidget);
    expect(find.text('LONG'), findsWidgets);
    // Strategy name resolved from persisted strategy metadata.
    expect(find.text('Strategy: EMA Trend Following'), findsOneWidget);
    // Opened timestamp from the persisted OPENED time.
    expect(find.textContaining('30 Sep 2026'), findsOneWidget);
    // Live price comes from the shared markets stream (40500), not the backend 40000.
    expect(find.text('40500.0000'), findsOneWidget);
    // LONG unrealized PnL = (40500 - 40000) * 0.05 = +25.00.
    expect(find.textContaining('+25.00'), findsOneWidget);
    expect(find.text('● LIVE'), findsOneWidget);
  });

  testWidgets('SPOT position displays SPOT', (WidgetTester tester) async {
    final repo = _FakePaperTradingRepository(marketType: 'SPOT');
    await _load(tester, repo);
    expect(find.text('SPOT'), findsOneWidget);
    expect(find.text('FUTURES'), findsNothing);
  });

  testWidgets('SHORT position computes live PnL correctly', (WidgetTester tester) async {
    final repo = _FakePaperTradingRepository(side: PaperPositionSide.short);
    await _load(tester, repo, live: const {'BTCUSDT': 39500});
    // SHORT unrealized = (entry - current) * qty = (40000 - 39500) * 0.05 = +25.00.
    expect(find.text('SHORT'), findsWidgets);
    expect(find.textContaining('+25.00'), findsOneWidget);
  });

  testWidgets('multiple positions update independently', (WidgetTester tester) async {
    tester.view.physicalSize = const Size(1200, 2000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repo = _FakePaperTradingRepository(
      openPositions: [
        _position(symbol: 'BTCUSDT', marketType: 'FUTURES', price: 40500),
        _position(symbol: 'ETHUSDT', marketType: 'SPOT', price: 2200, qty: 1),
      ],
    );
    // Both markets are live at once: a mixed portfolio needs each position priced on its own market.
    final twoMarkets = _book(
      const {'BTCUSDT': 40500},
      market: TradingMode.futures,
    ).withSnapshot(TradingMode.spot, [_ticker('ETHUSDT', 2200, TradingMode.spot)]);
    await _load(tester, repo, book: twoMarkets);
    expect(find.text('FUTURES'), findsOneWidget);
    expect(find.text('SPOT'), findsOneWidget);
    expect(find.text('40500.0000'), findsOneWidget);
    expect(find.text('2200.0000'), findsOneWidget);
  });

  /// The same symbol open on both markets must show two different live prices.
  testWidgets('same symbol on two markets is priced from each market separately',
      (WidgetTester tester) async {
    tester.view.physicalSize = const Size(1200, 2000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final repo = _FakePaperTradingRepository(
      openPositions: [
        _position(symbol: 'BTCUSDT', marketType: 'SPOT', price: 100000),
        _position(symbol: 'BTCUSDT', marketType: 'FUTURES', price: 100500),
      ],
    );

    // Distinct synthetic prices so a cross-market read is detectable, not coincidental.
    final twoMarkets = _book(
      const {'BTCUSDT': 100000},
      market: TradingMode.spot,
    ).withSnapshot(TradingMode.futures, [_ticker('BTCUSDT', 100500, TradingMode.futures)]);

    await _load(tester, repo, book: twoMarkets);

    // Each card shows its own market's price, not one shared number.
    expect(find.text('100000.0000'), findsOneWidget);
    expect(find.text('100500.0000'), findsOneWidget);
  });

  /// A quote arriving for the wrong market must not be applied to a position.
  testWidgets('a tick for the other market never moves this position',
      (WidgetTester tester) async {
    final repo = _FakePaperTradingRepository(); // FUTURES, entry 40000
    await _load(tester, repo, live: const {'BTCUSDT': 40500});
    expect(find.text('40500.0000'), findsOneWidget);

    final container = ProviderScope.containerOf(
      tester.element(find.byType(PaperTradingScreen)),
    );
    final prices = container.read(marketPricesControllerProvider.notifier)
        as _FakeMarketPricesController;
    // A large spot move on the same symbol must leave the futures position untouched.
    final futuresStill = _book(
      const {'BTCUSDT': 40500},
      market: TradingMode.futures,
    ).withSnapshot(TradingMode.spot, [_ticker('BTCUSDT', 1, TradingMode.spot)]);
    prices.emit(futuresStill);
    await tester.pump();

    expect(find.text('40500.0000'), findsOneWidget);
    expect(find.text('1.0000'), findsNothing);
  });

  testWidgets('current price and PnL update when a live tick arrives',
      (WidgetTester tester) async {
    final repo = _FakePaperTradingRepository();
    await _load(tester, repo); // shared stream starts at 40500.
    expect(find.text('40500.0000'), findsOneWidget);

    final container = ProviderScope.containerOf(
      tester.element(find.byType(PaperTradingScreen)),
    );
    final prices = container.read(marketPricesControllerProvider.notifier)
        as _FakeMarketPricesController;
    prices.emit(_book(const {'BTCUSDT': 40600}, market: TradingMode.futures));
    await tester.pump();

    // Current price reflects the new tick; the old backend/live value is gone.
    expect(find.text('40600.0000'), findsOneWidget);
    expect(find.text('40500.0000'), findsNothing);
    // LONG live PnL = (40600 - 40000) * 0.05 = +30.00 (was +25.00).
    expect(find.textContaining('+30.00'), findsOneWidget);
    expect(find.textContaining('+25.00'), findsNothing);
  });

  testWidgets('closed positions do not show a live price', (WidgetTester tester) async {
    final repo = _FakePaperTradingRepository(openPositions: const []);
    await _load(tester, repo);
    // History only: shows the persisted EXIT price, never a live price.
    await tester.tap(find.text('History'));
    await tester.pumpAndSettle();
    expect(find.text('2100.0000'), findsOneWidget);
    expect(find.text('40500.0000'), findsNothing);
  });

  testWidgets('closed positions keep their exit price when live ticks arrive',
      (WidgetTester tester) async {
    final repo = _FakePaperTradingRepository(openPositions: const []);
    await _load(tester, repo);
    await tester.tap(find.text('History'));
    await tester.pumpAndSettle();

    final container = ProviderScope.containerOf(
      tester.element(find.byType(PaperTradingScreen)),
    );
    final prices = container.read(marketPricesControllerProvider.notifier)
        as _FakeMarketPricesController;
    // A live tick for the closed symbol must NOT overwrite the exit price.
    prices.emit(_book(const {'ETHUSDT': 9999}, market: TradingMode.futures));
    await tester.pump();

    expect(find.text('2100.0000'), findsOneWidget);
    expect(find.text('9999.0000'), findsNothing);
  });

  testWidgets('capital update succeeds when account has no trading activity',
      (WidgetTester tester) async {
    final repo = _FakePaperTradingRepository(
      trades: 0,
      openPositions: const [],
      realizedPnl: 0,
      invested: 0,
    );
    await _load(tester, repo);

    expect(find.text('Initial Capital'), findsOneWidget);
    await tester.tap(find.byKey(const Key('paper_capital_edit')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), '250');
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();

    expect(repo.capitalSet, 250);
    expect(find.textContaining('250.00'), findsWidgets);
  });

  testWidgets('capital update is proactively blocked when trades exist',
      (WidgetTester tester) async {
    final repo = _FakePaperTradingRepository(trades: 3);
    await _load(tester, repo);

    await tester.tap(find.byKey(const Key('paper_capital_edit')));
    await tester.pumpAndSettle();

    expect(find.text("Can't change initial capital"), findsOneWidget);
    expect(find.textContaining('Reset the paper account first'), findsOneWidget);
    expect(repo.capitalSet, isNull);
    expect(find.textContaining('DioException'), findsNothing);
  });

  testWidgets('backend 400 is mapped to a friendly message (no raw DioException)',
      (WidgetTester tester) async {
    final repo = _FakePaperTradingRepository(
      trades: 0,
      openPositions: const [],
      realizedPnl: 0,
      invested: 0,
      updateStatus: 400,
    );
    await _load(tester, repo);

    await tester.tap(find.byKey(const Key('paper_capital_edit')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), '500');
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();

    expect(find.text("Can't change initial capital"), findsOneWidget);
    expect(find.textContaining('DioException'), findsNothing);
  });

  testWidgets('reset then capital update flow works', (WidgetTester tester) async {
    final repo = _FakePaperTradingRepository(trades: 3);
    await _load(tester, repo);

    await tester.tap(find.byKey(const Key('paper_capital_edit')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Reset Account'));
    await tester.pumpAndSettle();
    expect(repo.resetCalled, isTrue);
    expect(repo.initial, 100);

    await tester.tap(find.byKey(const Key('paper_capital_edit')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), '500');
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();
    expect(repo.capitalSet, 500);
  });

  test('many positions share exactly one market socket, disposed on teardown',
      () async {
    final connector = _CountingSocketConnector();
    addTearDown(connector.dispose);
    final container = ProviderContainer(
      overrides: [
        authSessionProvider.overrideWith(() => _AuthedSessionController()),
        marketRepositoryProvider.overrideWith((ref) => _StubMarketRepository()),
        marketsSocketConnectorProvider.overrideWith((ref) => connector),
      ],
    );

    // Resolve the auth session first so the markets controller does not do its
    // one-time loading→authenticated reconnect during the assertion.
    await container.read(authSessionProvider.future);

    // Two independent listeners (as many position cards would create) must still
    // resolve to ONE shared controller instance and ONE socket connection.
    final subA = container.listen(marketsControllerProvider, (_, _) {});
    final subB = container.listen(marketsControllerProvider, (_, _) {});
    await container.read(marketsControllerProvider.future);
    await Future<void>.delayed(Duration.zero);

    expect(connector.connectCalls, 1);

    subA.close();
    subB.close();
    container.dispose();
    await Future<void>.delayed(Duration.zero);

    expect(connector.closeCalls, greaterThanOrEqualTo(1));
    expect(connector.connectCalls, 1);
  });
}

class _AuthedSessionController extends AuthSessionController {
  @override
  Future<AuthStatus> build() async => AuthStatus.authenticated;
}

class _CountingSocketConnector implements MarketsSocketConnector {
  int connectCalls = 0;
  int closeCalls = 0;
  final _controller = StreamController<dynamic>();

  @override
  MarketsSocketSession connect(Uri uri) {
    connectCalls++;
    return MarketsSocketSession(
      stream: _controller.stream,
      ready: Future.value(),
      close: () async {
        closeCalls++;
      },
    );
  }

  void dispose() => _controller.close();
}

class _StubMarketRepository implements MarketRepository {
  @override
  Future<MarketSnapshot> getMarkets(TradingMode mode) async =>
      const MarketSnapshot(mode: 'SPOT', tickers: []);

  @override
  Future<MarketSnapshot> getGainers(TradingMode mode) async =>
      const MarketSnapshot(mode: 'SPOT', tickers: []);

  @override
  Future<MarketSnapshot> getLosers(TradingMode mode) async =>
      const MarketSnapshot(mode: 'SPOT', tickers: []);

  @override
  Future<MarketTicker> getTicker(String symbol, TradingMode mode) async =>
      throw UnimplementedError();

  @override
  Future<List<KlineCandle>> getKlines({
    required String symbol,
    required String interval,
    required int limit,
    required TradingMode mode,
  }) async =>
      const [];
}

PaperPosition _position({
  String symbol = 'BTCUSDT',
  String marketType = 'FUTURES',
  double price = 40500,
  double qty = 0.05,
  PaperPositionSide side = PaperPositionSide.long,
}) =>
    PaperPosition(
      id: 'p_$symbol',
      signalId: 's_$symbol',
      symbol: symbol,
      side: side,
      quantity: qty,
      entryPrice: 40000,
      currentPrice: 40000,
      stopLoss: 39500,
      takeProfit1: 41000,
      takeProfit2: 42000,
      takeProfit3: 43000,
      notional: 40000 * qty,
      entryFee: 2,
      realizedPnl: 0,
      unrealizedPnl: 0,
      unrealizedPnlPct: 0,
      status: PaperPositionStatus.open,
      openedAt: DateTime.utc(2026, 9, 30, 6, 42, 18),
      strategyName: 'EMA Trend Following',
      marketType: marketType,
    );

class _FakePaperTradingRepository implements PaperTradingRepository {
  _FakePaperTradingRepository({
    this.trades = 1,
    this.openPositions,
    this.realizedPnl = 25,
    this.invested = 2000,
    this.updateStatus,
    this.marketType = 'FUTURES',
    this.side = PaperPositionSide.long,
  });

  int trades;
  List<PaperPosition>? openPositions;
  double realizedPnl;
  double invested;
  int? updateStatus;
  String marketType;
  PaperPositionSide side;

  bool resetCalled = false;
  double? capitalSet;
  double initial = 10000;

  PaperPosition get _defaultOpen {
    final p = _position(marketType: marketType, side: side);
    return p;
  }

  @override
  Future<PaperAccount> getAccount() async => PaperAccount(
        id: 'acc1',
        quoteCurrency: 'USDT',
        initialBalance: initial,
        availableBalance: invested == 0 ? initial : 8000,
        invested: invested,
        totalBalance: initial,
        equity: initial + realizedPnl,
        realizedPnl: realizedPnl,
        unrealizedPnl: 25,
        totalFees: 4,
        totalTrades: trades,
        winningTrades: trades > 0 ? 1 : 0,
        losingTrades: 0,
        winRatePct: trades > 0 ? 100 : 0,
      );

  @override
  Future<List<PaperPosition>> listOpenPositions() async =>
      openPositions ?? [_defaultOpen];

  @override
  Future<List<PaperPosition>> listHistory() async => trades > 0
      ? const [
          PaperPosition(
            id: 'p0',
            symbol: 'ETHUSDT',
            side: PaperPositionSide.long,
            quantity: 1,
            entryPrice: 2000,
            exitPrice: 2100,
            realizedPnl: 100,
            unrealizedPnl: 0,
            unrealizedPnlPct: 0,
            status: PaperPositionStatus.closed,
            closeReason: PaperCloseReason.takeProfit,
          ),
        ]
      : const [];

  @override
  Future<PaperPerformance> getPerformance() async => PaperPerformance(
        totalTrades: trades,
        winningTrades: trades > 0 ? 1 : 0,
        losingTrades: 0,
        winRatePct: trades > 0 ? 100 : 0,
        totalNetPnl: realizedPnl,
        averageWin: 100,
        averageLoss: 0,
        profitFactor: 999.99,
        bestTradePnl: 100,
        worstTradePnl: 0,
        totalFees: 4,
        returnPct: 1,
      );

  @override
  Future<PaperPosition> closePosition(String id) async => const PaperPosition(
        id: 'p0',
        symbol: 'BTCUSDT',
        side: PaperPositionSide.long,
        quantity: 0.05,
        entryPrice: 40000,
        exitPrice: 40500,
        realizedPnl: 23,
        unrealizedPnl: 0,
        unrealizedPnlPct: 0,
        status: PaperPositionStatus.closed,
        closeReason: PaperCloseReason.manual,
      );

  @override
  Future<PaperAccount> resetAccount() async {
    resetCalled = true;
    initial = 100;
    trades = 0;
    openPositions = const [];
    realizedPnl = 0;
    invested = 0;
    return getAccount();
  }

  @override
  Future<PaperAccount> updateInitialCapital(double initialCapital) async {
    if (updateStatus != null) {
      final options = RequestOptions(path: '/v1/paper-trading/account/capital');
      throw DioException(
        requestOptions: options,
        response: Response(requestOptions: options, statusCode: updateStatus),
      );
    }
    capitalSet = initialCapital;
    initial = initialCapital;
    return getAccount();
  }
}
