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

void main() {
  testWidgets('paper trading screen renders account, open positions, history and stats',
      (WidgetTester tester) async {
    final repo = _FakePaperTradingRepository();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          paperTradingRepositoryProvider.overrideWith((ref) => repo),
        ],
        child: MaterialApp(
          theme: AppTheme.dark(),
          darkTheme: AppTheme.dark(),
          themeMode: ThemeMode.dark,
          home: const PaperTradingScreen(),
        ),
      ),
    );
    // Initial async load.
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    // Account header shows equity + simulation badge.
    expect(find.text('SIMULATION'), findsOneWidget);
    expect(find.textContaining('USDT'), findsWidgets);

    // Open tab is default; one open position visible.
    expect(find.text('BTCUSDT'), findsWidgets);
    expect(find.text('LONG'), findsWidgets);
    expect(find.text('CLOSE'), findsOneWidget);

    // Switch to History.
    await tester.tap(find.text('History'));
    await tester.pumpAndSettle();
    expect(find.text('ETHUSDT'), findsWidgets);

    // Switch to Stats.
    await tester.tap(find.text('Stats'));
    await tester.pumpAndSettle();
    expect(find.text('Total trades'), findsOneWidget);
    expect(find.text('Win rate'), findsOneWidget);
    expect(find.text('Profit factor'), findsOneWidget);
  });

  testWidgets('close position triggers repository close and shows snackbar',
      (WidgetTester tester) async {
    final repo = _FakePaperTradingRepository();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          paperTradingRepositoryProvider.overrideWith((ref) => repo),
        ],
        child: MaterialApp(
          theme: AppTheme.dark(),
          darkTheme: AppTheme.dark(),
          themeMode: ThemeMode.dark,
          home: const PaperTradingScreen(),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    await tester.tap(find.text('CLOSE'));
    await tester.pumpAndSettle();
    // Confirm dialog.
    expect(find.text('Close BTCUSDT?'), findsOneWidget);
    await tester.tap(find.text('CLOSE').last);
    await tester.pumpAndSettle();

    expect(repo.closeCalled, 'p1');
    expect(find.text('BTCUSDT closed'), findsOneWidget);
  });

  testWidgets('initial capital is shown and can be edited', (WidgetTester tester) async {
    final repo = _FakePaperTradingRepository();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          paperTradingRepositoryProvider.overrideWith((ref) => repo),
        ],
        child: MaterialApp(
          theme: AppTheme.dark(),
          darkTheme: AppTheme.dark(),
          themeMode: ThemeMode.dark,
          home: const PaperTradingScreen(),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    // Initial capital is displayed with the account currency.
    expect(find.text('Initial Capital'), findsOneWidget);
    expect(find.textContaining('10000.00'), findsWidgets);

    // Edit -> enter a new value -> save.
    await tester.tap(find.byKey(const Key('paper_capital_edit')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), '250');
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();

    expect(repo.capitalSet, 250);
    expect(find.textContaining('250.00'), findsWidgets);
  });

  testWidgets('initial capital rejects non-positive input without calling the backend',
      (WidgetTester tester) async {
    final repo = _FakePaperTradingRepository();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          paperTradingRepositoryProvider.overrideWith((ref) => repo),
        ],
        child: MaterialApp(
          theme: AppTheme.dark(),
          darkTheme: AppTheme.dark(),
          themeMode: ThemeMode.dark,
          home: const PaperTradingScreen(),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    await tester.tap(find.byKey(const Key('paper_capital_edit')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), '0');
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();

    expect(repo.capitalSet, isNull);
    expect(find.text('Enter a positive amount'), findsOneWidget);
  });
}

class _FakePaperTradingRepository implements PaperTradingRepository {
  String? closeCalled;
  bool resetCalled = false;
  double? capitalSet;
  double initial = 10000;

  @override
  Future<PaperAccount> getAccount() async => PaperAccount(
        id: 'acc1',
        quoteCurrency: 'USDT',
        initialBalance: initial,
        availableBalance: 8000,
        invested: 2000,
        totalBalance: initial,
        equity: 10050,
        realizedPnl: 25,
        unrealizedPnl: 25,
        totalFees: 4,
        totalTrades: 1,
        winningTrades: 1,
        losingTrades: 0,
        winRatePct: 100,
      );

  @override
  Future<List<PaperPosition>> listOpenPositions() async => [
        const PaperPosition(
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
        ),
      ];

  @override
  Future<List<PaperPosition>> listHistory() async => [
        const PaperPosition(
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
      ];

  @override
  Future<PaperPerformance> getPerformance() async => const PaperPerformance(
        totalTrades: 1,
        winningTrades: 1,
        losingTrades: 0,
        winRatePct: 100,
        totalNetPnl: 100,
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
    return getAccount();
  }

  @override
  Future<PaperAccount> updateInitialCapital(double initialCapital) async {
    capitalSet = initialCapital;
    initial = initialCapital;
    return getAccount();
  }
}
