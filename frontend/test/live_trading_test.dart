import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:cryptosignals/core/di/providers.dart';
import 'package:cryptosignals/core/theme/app_theme.dart';
import 'package:cryptosignals/domain/entities/live_account.dart';
import 'package:cryptosignals/domain/entities/live_order.dart';
import 'package:cryptosignals/domain/entities/live_performance.dart';
import 'package:cryptosignals/domain/repositories/live_trading_repository.dart';
import 'package:cryptosignals/presentation/screens/live_trading/live_trading_screen.dart';

void main() {
  testWidgets('live trading — no account shows connect CTA',
      (WidgetTester tester) async {
    final repo = _FakeRepo(account: null);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          liveTradingRepositoryProvider.overrideWith((ref) => repo),
        ],
        child: MaterialApp(
          theme: AppTheme.dark(),
          darkTheme: AppTheme.dark(),
          themeMode: ThemeMode.dark,
          home: const LiveTradingScreen(),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.text('No live exchange connected'), findsOneWidget);
    expect(find.text('Connect Binance'), findsOneWidget);
  });

  testWidgets('live trading — connected account renders account, orders, kill switch',
      (WidgetTester tester) async {
    final repo = _FakeRepo(
      account: const LiveAccount(
        id: 'a1',
        exchange: LiveExchange.binance,
        connectionStatus: LiveConnectionStatus.connected,
        enabled: false,
        killSwitchActive: false,
        quoteCurrency: 'USDT',
        cachedAvailableBalance: 800,
        cachedTotalBalance: 1000,
        maxNotionalPerTrade: 200,
        maxActivePositions: 3,
        dailyLossLimitPct: 5,
      ),
      openOrders: [
        LiveOrder(
          id: 'o1',
          clientOrderId: 'SB-o1',
          symbol: 'BTCUSDT',
          side: LiveSide.long,
          type: LiveOrderType.market,
          purpose: LiveOrderPurpose.entry,
          status: LiveOrderStatus.acknowledged,
          requestedQuantity: 0.01,
          executedQuantity: 0,
          remainingQuantity: 0.01,
        ),
      ],
    );
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          liveTradingRepositoryProvider.overrideWith((ref) => repo),
        ],
        child: MaterialApp(
          theme: AppTheme.dark(),
          darkTheme: AppTheme.dark(),
          themeMode: ThemeMode.dark,
          home: const LiveTradingScreen(),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    // LIVE badge visible.
    expect(find.text('LIVE'), findsOneWidget);
    // Balance visible.
    expect(find.textContaining('USDT'), findsWidgets);
    // Open order row.
    expect(find.text('BTCUSDT'), findsOneWidget);
    expect(find.text('ENTRY • MARKET'), findsOneWidget);
    // Safety panel + kill switch labels.
    expect(find.text('Live trading'), findsOneWidget);
    expect(find.text('Kill switch'), findsOneWidget);
    // Cancel button visible for non-terminal order.
    expect(find.text('CANCEL'), findsOneWidget);
  });

  testWidgets('live trading — enabling live requires acknowledgement dialog',
      (WidgetTester tester) async {
    final repo = _FakeRepo(
      account: const LiveAccount(
        id: 'a1',
        exchange: LiveExchange.binance,
        connectionStatus: LiveConnectionStatus.connected,
        enabled: false,
        killSwitchActive: false,
        quoteCurrency: 'USDT',
        cachedAvailableBalance: 800,
        cachedTotalBalance: 1000,
        maxNotionalPerTrade: 200,
        maxActivePositions: 3,
      ),
    );
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          liveTradingRepositoryProvider.overrideWith((ref) => repo),
        ],
        child: MaterialApp(
          theme: AppTheme.dark(),
          darkTheme: AppTheme.dark(),
          themeMode: ThemeMode.dark,
          home: const LiveTradingScreen(),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    // Tap the Live-trading Switch (first switch).
    await tester.tap(find.byType(Switch).first);
    await tester.pumpAndSettle();

    // Confirmation dialog visible with acknowledgment copy.
    expect(find.text('Enable live trading?'), findsOneWidget);
    expect(find.text('I UNDERSTAND — ENABLE'), findsOneWidget);

    await tester.tap(find.text('I UNDERSTAND — ENABLE'));
    await tester.pumpAndSettle();

    expect(repo.activatedAcknowledged, isTrue);
    expect(find.text('Live trading enabled'), findsOneWidget);
  });
}

class _FakeRepo implements LiveTradingRepository {
  _FakeRepo({this.account, this.openOrders = const []});

  LiveAccount? account;
  final List<LiveOrder> openOrders;
  bool activatedAcknowledged = false;

  @override
  Future<LiveAccount?> getAccount() async => account;

  @override
  Future<LiveAccount> connect(LiveExchange exchange) async {
    account = const LiveAccount(
      id: 'a1',
      exchange: LiveExchange.binance,
      connectionStatus: LiveConnectionStatus.connected,
      enabled: false,
      killSwitchActive: false,
      quoteCurrency: 'USDT',
      cachedAvailableBalance: 1000,
      cachedTotalBalance: 1000,
      maxNotionalPerTrade: 200,
      maxActivePositions: 3,
    );
    return account!;
  }

  @override
  Future<LiveAccount> validate() async => account!;

  @override
  Future<LiveAccount> disconnect() async {
    account = null;
    return const LiveAccount(
      id: 'a1',
      exchange: LiveExchange.binance,
      connectionStatus: LiveConnectionStatus.notConnected,
      enabled: false,
      killSwitchActive: false,
      quoteCurrency: 'USDT',
      maxActivePositions: 3,
    );
  }

  @override
  Future<LiveAccount> activate({required bool acknowledged}) async {
    activatedAcknowledged = acknowledged;
    account = LiveAccount(
      id: 'a1',
      exchange: LiveExchange.binance,
      connectionStatus: LiveConnectionStatus.connected,
      enabled: true,
      killSwitchActive: false,
      quoteCurrency: 'USDT',
      cachedAvailableBalance: account?.cachedAvailableBalance,
      cachedTotalBalance: account?.cachedTotalBalance,
      maxNotionalPerTrade: 200,
      maxActivePositions: 3,
    );
    return account!;
  }

  @override
  Future<LiveAccount> deactivate() async => account!;

  @override
  Future<LiveAccount> triggerKillSwitch() async => account!;

  @override
  Future<LiveAccount> releaseKillSwitch() async => account!;

  @override
  Future<List<LiveOrder>> listOpenOrders() async => openOrders;

  @override
  Future<List<LiveOrder>> listHistory() async => const [];

  @override
  Future<LiveOrder> cancelOrder(String id) async =>
      openOrders.firstWhere((o) => o.id == id);

  @override
  Future<LivePerformance> getPerformance() async => const LivePerformance(
        totalOrders: 0,
        filledEntries: 0,
        rejections: 0,
        totalFees: 0,
        totalNotional: 0,
      );
}
