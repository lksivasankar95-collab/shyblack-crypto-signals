import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:cryptosignals/core/di/providers.dart';
import 'package:cryptosignals/core/theme/app_theme.dart';
import 'package:cryptosignals/domain/entities/backtest_equity_point.dart';
import 'package:cryptosignals/domain/entities/backtest_run.dart';
import 'package:cryptosignals/domain/entities/backtest_trade.dart';
import 'package:cryptosignals/domain/repositories/backtesting_repository.dart';
import 'package:cryptosignals/presentation/screens/backtesting/backtesting_screen.dart';

void main() {
  testWidgets('backtesting — empty list shows CTA + config form',
      (WidgetTester tester) async {
    tester.view.physicalSize = const Size(1000, 1600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repo = _FakeRepo();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [backtestingRepositoryProvider.overrideWith((ref) => repo)],
        child: MaterialApp(
          theme: AppTheme.dark(),
          darkTheme: AppTheme.dark(),
          themeMode: ThemeMode.dark,
          home: const BacktestingScreen(),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.text('Backtesting'), findsOneWidget);
    expect(find.text('New backtest'), findsOneWidget);
    expect(find.text('RUN BACKTEST'), findsOneWidget);
    expect(find.text('No runs yet.'), findsOneWidget);
  });

  testWidgets('backtesting — completed run displays metrics',
      (WidgetTester tester) async {
    tester.view.physicalSize = const Size(1000, 1600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final now = DateTime.parse('2024-05-01T00:00:00Z');
    final repo = _FakeRepo(
      runs: [
        BacktestRun(
          id: 'r1',
          status: BacktestStatus.completed,
          strategyId: 'ema-rsi',
          strategyVersion: 'v1',
          engineVersion: 'backtest-engine/v1',
          tradingMode: BacktestTradingMode.spot,
          symbol: 'BTCUSDT',
          timeframe: '1h',
          startDate: now.subtract(const Duration(days: 30)),
          endDate: now,
          initialCapital: 10000,
          riskPerTradePct: 1.0,
          feePct: 0.10,
          slippagePct: 0.05,
          leverage: 1,
          executionModel: BacktestExecutionModel.nextCandleOpen,
          sameCandlePolicy: BacktestSameCandlePolicy.slFirst,
          configurationHash: 'abc123',
          processedCandles: 720,
          totalCandles: 720,
          finalEquity: 10500,
          totalNetPnl: 500,
          totalReturnPct: 5.0,
          winRatePct: 60,
          totalTrades: 5,
          winningTrades: 3,
          losingTrades: 2,
          maxDrawdown: 100,
          maxDrawdownPct: 1.0,
          profitFactor: 1.8,
          sharpeRatio: 0.5,
        ),
      ],
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [backtestingRepositoryProvider.overrideWith((ref) => repo)],
        child: MaterialApp(
          theme: AppTheme.dark(),
          darkTheme: AppTheme.dark(),
          themeMode: ThemeMode.dark,
          home: const BacktestingScreen(),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.text('BTCUSDT • 1h • SPOT'), findsOneWidget);
    expect(find.text('COMPLETED'), findsOneWidget);
    // Metrics visible on the run card.
    expect(find.text('Net P&L'), findsOneWidget);
    expect(find.text('Win %'), findsOneWidget);
    expect(find.text('MaxDD'), findsOneWidget);
  });
}

class _FakeRepo implements BacktestingRepository {
  _FakeRepo({this.runs = const []});
  final List<BacktestRun> runs;

  @override
  Future<List<BacktestRun>> listRuns() async => runs;

  @override
  Future<BacktestRun> getRun(String id) async =>
      runs.firstWhere((r) => r.id == id);

  @override
  Future<BacktestRun> startRun(BacktestConfigInput config) async => runs.first;

  @override
  Future<List<BacktestTrade>> getTrades(String id) async => const [];

  @override
  Future<List<BacktestEquityPoint>> getEquity(String id) async => const [];

  @override
  Future<BacktestRun> cancelRun(String id) async =>
      runs.firstWhere((r) => r.id == id);

  @override
  Future<void> deleteRun(String id) async {}

  @override
  Future<List<StrategyDescriptor>> listStrategies() async =>
      const [StrategyDescriptor(id: 'ema-rsi', version: 'v1')];
}
