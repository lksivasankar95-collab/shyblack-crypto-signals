import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:cryptosignals/core/di/providers.dart';
import 'package:cryptosignals/core/theme/app_theme.dart';
import 'package:cryptosignals/domain/entities/paper_account.dart';
import 'package:cryptosignals/domain/entities/paper_performance.dart';
import 'package:cryptosignals/domain/entities/paper_position.dart';
import 'package:cryptosignals/domain/repositories/paper_trading_repository.dart';
import 'package:cryptosignals/presentation/screens/paper_trading/paper_trading_screen.dart';

Widget _app(_FakePaperTradingRepository repo) => ProviderScope(
      overrides: [paperTradingRepositoryProvider.overrideWith((ref) => repo)],
      child: MaterialApp(
        theme: AppTheme.dark(),
        darkTheme: AppTheme.dark(),
        themeMode: ThemeMode.dark,
        home: const PaperTradingScreen(),
      ),
    );

Future<void> _load(WidgetTester tester, _FakePaperTradingRepository repo) async {
  await tester.pumpWidget(_app(repo));
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 50));
}

void main() {
  testWidgets('paper trading screen renders account, open positions, history and stats',
      (WidgetTester tester) async {
    final repo = _FakePaperTradingRepository();
    await _load(tester, repo);

    expect(find.text('SIMULATION'), findsOneWidget);
    expect(find.textContaining('USDT'), findsWidgets);
    expect(find.text('BTCUSDT'), findsWidgets);
    expect(find.text('LONG'), findsWidgets);
    expect(find.text('CLOSE'), findsOneWidget);

    await tester.tap(find.text('History'));
    await tester.pumpAndSettle();
    expect(find.text('ETHUSDT'), findsWidgets);

    await tester.tap(find.text('Stats'));
    await tester.pumpAndSettle();
    expect(find.text('Total trades'), findsOneWidget);
    expect(find.text('Win rate'), findsOneWidget);
    expect(find.text('Profit factor'), findsOneWidget);
  });

  testWidgets('close position triggers repository close and shows snackbar',
      (WidgetTester tester) async {
    final repo = _FakePaperTradingRepository();
    await _load(tester, repo);

    await tester.tap(find.text('CLOSE'));
    await tester.pumpAndSettle();
    expect(find.text('Close BTCUSDT?'), findsOneWidget);
    await tester.tap(find.text('CLOSE').last);
    await tester.pumpAndSettle();

    expect(repo.closeCalled, 'p1');
    expect(find.text('BTCUSDT closed'), findsOneWidget);
  });

  testWidgets('capital update succeeds when account has no trading activity',
      (WidgetTester tester) async {
    final repo = _FakePaperTradingRepository(
      trades: 0,
      openPositionCount: 0,
      realizedPnl: 0,
      invested: 0,
    );
    await _load(tester, repo);

    expect(find.text('Initial Capital'), findsOneWidget);
    expect(find.textContaining('10000.00'), findsWidgets);

    await tester.tap(find.byKey(const Key('paper_capital_edit')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), '250');
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();

    expect(repo.capitalSet, 250);
    expect(find.textContaining('250.00'), findsWidgets);
    expect(find.text('Initial capital updated'), findsOneWidget);
  });

  testWidgets('capital update is proactively blocked when trades exist',
      (WidgetTester tester) async {
    final repo = _FakePaperTradingRepository(trades: 3, openPositionCount: 0);
    await _load(tester, repo);

    await tester.tap(find.byKey(const Key('paper_capital_edit')));
    await tester.pumpAndSettle();

    expect(find.text("Can't change initial capital"), findsOneWidget);
    expect(
      find.textContaining("Reset the paper account first"),
      findsOneWidget,
    );
    // No backend call was made.
    expect(repo.capitalSet, isNull);
    expect(find.textContaining('DioException'), findsNothing);
  });

  testWidgets('capital update is proactively blocked when open positions exist',
      (WidgetTester tester) async {
    final repo = _FakePaperTradingRepository(
      trades: 0,
      openPositionCount: 1,
      realizedPnl: 0,
      invested: 0,
    );
    await _load(tester, repo);

    await tester.tap(find.byKey(const Key('paper_capital_edit')));
    await tester.pumpAndSettle();

    expect(find.text("Can't change initial capital"), findsOneWidget);
    expect(repo.capitalSet, isNull);
  });

  testWidgets('capital update is proactively blocked when realized PnL exists',
      (WidgetTester tester) async {
    final repo = _FakePaperTradingRepository(
      trades: 0,
      openPositionCount: 0,
      realizedPnl: 25,
      invested: 0,
    );
    await _load(tester, repo);

    await tester.tap(find.byKey(const Key('paper_capital_edit')));
    await tester.pumpAndSettle();

    expect(find.text("Can't change initial capital"), findsOneWidget);
    expect(repo.capitalSet, isNull);
  });

  testWidgets('backend 400 is mapped to a friendly message (no raw DioException)',
      (WidgetTester tester) async {
    final repo = _FakePaperTradingRepository(
      trades: 0,
      openPositionCount: 0,
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
    expect(find.textContaining('bad response'), findsNothing);
  });

  testWidgets('unexpected error shows a generic friendly message',
      (WidgetTester tester) async {
    final repo = _FakePaperTradingRepository(
      trades: 0,
      openPositionCount: 0,
      realizedPnl: 0,
      invested: 0,
      updateStatus: 500,
    );
    await _load(tester, repo);

    await tester.tap(find.byKey(const Key('paper_capital_edit')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), '500');
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();

    expect(find.text('Unable to update initial capital. Please try again.'), findsOneWidget);
    expect(find.textContaining('DioException'), findsNothing);
  });

  testWidgets('capital update rejects non-positive input without calling the backend',
      (WidgetTester tester) async {
    final repo = _FakePaperTradingRepository(
      trades: 0,
      openPositionCount: 0,
      realizedPnl: 0,
      invested: 0,
    );
    await _load(tester, repo);

    await tester.tap(find.byKey(const Key('paper_capital_edit')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), '0');
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();

    expect(repo.capitalSet, isNull);
    expect(find.text('Enter a positive amount'), findsOneWidget);
  });

  testWidgets('reset then capital update flow works', (WidgetTester tester) async {
    final repo = _FakePaperTradingRepository(trades: 3, openPositionCount: 1);
    await _load(tester, repo);

    // Edit while activity exists -> blocked, offers Reset.
    await tester.tap(find.byKey(const Key('paper_capital_edit')));
    await tester.pumpAndSettle();
    expect(find.text('Reset Account'), findsOneWidget);

    await tester.tap(find.text('Reset Account'));
    await tester.pumpAndSettle();
    expect(repo.resetCalled, isTrue);
    expect(repo.initial, 100);
    expect(find.textContaining('100.00'), findsWidgets);

    // Now the account is clean: edit anew.
    await tester.tap(find.byKey(const Key('paper_capital_edit')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), '500');
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();

    expect(repo.capitalSet, 500);
    expect(find.textContaining('500.00'), findsWidgets);
  });
}

class _FakePaperTradingRepository implements PaperTradingRepository {
  _FakePaperTradingRepository({
    this.trades = 1,
    this.openPositionCount = 1,
    this.realizedPnl = 25,
    this.invested = 2000,
    this.updateStatus,
  });

  int trades;
  int openPositionCount;
  double realizedPnl;
  double invested;

  /// When set, [updateInitialCapital] throws a DioException with this status.
  int? updateStatus;

  String? closeCalled;
  bool resetCalled = false;
  double? capitalSet;
  double initial = 10000;

  PaperPosition _openPosition() => const PaperPosition(
        id: 'p1',
        signalId: 's1',
        symbol: 'BTCUSDT',
        side: PaperPositionSide.long,
        quantity: 0.05,
        entryPrice: 40000,
        currentPrice: 40500,
        stopLoss: 39500,
        takeProfit1: 41000,
        notional: 2000,
        entryFee: 2,
        realizedPnl: 0,
        unrealizedPnl: 25,
        unrealizedPnlPct: 1.25,
        status: PaperPositionStatus.open,
      );

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
      List.generate(openPositionCount, (_) => _openPosition());

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
  Future<PaperPosition> closePosition(String id) async {
    closeCalled = id;
    return const PaperPosition(
      id: 'p1',
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
  }

  @override
  Future<PaperAccount> resetAccount() async {
    resetCalled = true;
    initial = 100;
    trades = 0;
    openPositionCount = 0;
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
