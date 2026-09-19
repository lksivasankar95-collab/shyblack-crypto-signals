import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:cryptosignals/core/di/providers.dart';
import 'package:cryptosignals/core/theme/app_theme.dart';
import 'package:cryptosignals/domain/entities/futures_account.dart';
import 'package:cryptosignals/domain/entities/futures_order.dart';
import 'package:cryptosignals/domain/entities/futures_position.dart';
import 'package:cryptosignals/domain/repositories/futures_trading_repository.dart';
import 'package:cryptosignals/presentation/screens/futures_trading/futures_trading_screen.dart';

void main() {
  testWidgets('futures — no account shows connect CTA',
      (WidgetTester tester) async {
    final repo = _FakeRepo(account: null);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [futuresTradingRepositoryProvider.overrideWith((ref) => repo)],
        child: MaterialApp(
          theme: AppTheme.dark(),
          darkTheme: AppTheme.dark(),
          themeMode: ThemeMode.dark,
          home: const FuturesTradingScreen(),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.text('No futures account connected'), findsOneWidget);
    expect(find.text('Connect Binance Futures'), findsOneWidget);
  });

  testWidgets('futures — connected LONG position shows CLOSE POSITION + protection',
      (WidgetTester tester) async {
    final repo = _FakeRepo(
      account: const FuturesAccount(
        id: 'a1', exchange: 'BINANCE',
        connectionStatus: FuturesConnectionStatus.connected,
        enabled: true, killSwitchActive: false, acknowledged: true,
        marginAsset: 'USDT',
        marginMode: FuturesMarginMode.isolated,
        positionMode: FuturesPositionMode.oneWay,
        maxLeverage: 3, maxActivePositions: 2,
        walletBalance: 1000, availableBalance: 900, marginBalance: 1000,
        usedMargin: 50, unrealizedPnl: 12,
      ),
      openPositions: [
        const FuturesPosition(
          id: 'p1',
          symbol: 'BTCUSDT',
          positionSide: FuturesSide.long,
          marginMode: FuturesMarginMode.isolated,
          leverage: 3,
          quantity: 0.01,
          entryPrice: 50000,
          stopLoss: 49500,
          initialMargin: 166.67,
          liquidationPrice: 33500,
          realizedPnl: 0,
          unrealizedPnl: 20,
          tradingFees: 0.5,
          fundingFees: 0,
          status: FuturesPositionStatus.open,
          protectionStatus: FuturesProtectionStatus.protected_,
        ),
      ],
    );
    await tester.pumpWidget(
      ProviderScope(
        overrides: [futuresTradingRepositoryProvider.overrideWith((ref) => repo)],
        child: MaterialApp(
          theme: AppTheme.dark(),
          darkTheme: AppTheme.dark(),
          themeMode: ThemeMode.dark,
          home: const FuturesTradingScreen(),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.text('LIVE • FUTURES'), findsOneWidget);
    expect(find.text('BTCUSDT'), findsOneWidget);
    expect(find.text('LONG 3x'), findsOneWidget);
    expect(find.text('Protected by SL'), findsOneWidget);
    expect(find.text('CLOSE POSITION'), findsOneWidget);

    await tester.tap(find.text('CLOSE POSITION'));
    await tester.pumpAndSettle();
    expect(find.text('Close LONG BTCUSDT?'), findsOneWidget);
    await tester.tap(find.text('CLOSE POSITION').last);
    await tester.pumpAndSettle();
    expect(repo.closedPositionId, 'p1');
    expect(find.text('Close submitted for BTCUSDT'), findsOneWidget);
  });

  testWidgets('futures — enabling live requires acknowledgement dialog',
      (WidgetTester tester) async {
    final repo = _FakeRepo(
      account: const FuturesAccount(
        id: 'a1', exchange: 'BINANCE',
        connectionStatus: FuturesConnectionStatus.connected,
        enabled: false, killSwitchActive: false, acknowledged: false,
        marginAsset: 'USDT',
        marginMode: FuturesMarginMode.isolated,
        positionMode: FuturesPositionMode.oneWay,
        maxLeverage: 3, maxActivePositions: 2,
      ),
    );
    await tester.pumpWidget(
      ProviderScope(
        overrides: [futuresTradingRepositoryProvider.overrideWith((ref) => repo)],
        child: MaterialApp(
          theme: AppTheme.dark(),
          darkTheme: AppTheme.dark(),
          themeMode: ThemeMode.dark,
          home: const FuturesTradingScreen(),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    await tester.tap(find.byType(Switch).first);
    await tester.pumpAndSettle();
    expect(find.text('Enable live FUTURES trading?'), findsOneWidget);
    expect(find.text('I UNDERSTAND — ENABLE'), findsOneWidget);
    await tester.tap(find.text('I UNDERSTAND — ENABLE'));
    await tester.pumpAndSettle();
    expect(repo.activatedAcknowledged, isTrue);
    expect(find.text('Futures trading enabled'), findsOneWidget);
  });
}

class _FakeRepo implements FuturesTradingRepository {
  _FakeRepo({this.account, this.openPositions = const []});
  FuturesAccount? account;
  final List<FuturesPosition> openPositions;
  String? closedPositionId;
  bool activatedAcknowledged = false;

  @override Future<FuturesAccount?> getAccount() async => account;

  @override
  Future<FuturesAccount> connect(String exchange) async {
    account = const FuturesAccount(
      id: 'a1', exchange: 'BINANCE',
      connectionStatus: FuturesConnectionStatus.connected,
      enabled: false, killSwitchActive: false, acknowledged: false,
      marginAsset: 'USDT',
      marginMode: FuturesMarginMode.isolated,
      positionMode: FuturesPositionMode.oneWay,
      maxLeverage: 3, maxActivePositions: 2,
      walletBalance: 1000, availableBalance: 1000,
    );
    return account!;
  }

  @override Future<FuturesAccount> validate() async => account!;
  @override Future<FuturesAccount> disconnect() async => account!;
  @override Future<FuturesAccount> acknowledge(bool flag) async => account!;

  @override
  Future<FuturesAccount> activate({required bool acknowledged}) async {
    activatedAcknowledged = acknowledged;
    account = FuturesAccount(
      id: 'a1', exchange: 'BINANCE',
      connectionStatus: FuturesConnectionStatus.connected,
      enabled: true, killSwitchActive: false, acknowledged: true,
      marginAsset: 'USDT',
      marginMode: FuturesMarginMode.isolated,
      positionMode: FuturesPositionMode.oneWay,
      maxLeverage: 3, maxActivePositions: 2,
      walletBalance: account?.walletBalance,
      availableBalance: account?.availableBalance,
    );
    return account!;
  }

  @override Future<FuturesAccount> deactivate() async => account!;
  @override Future<FuturesAccount> triggerKillSwitch() async => account!;
  @override Future<FuturesAccount> releaseKillSwitch() async => account!;
  @override Future<List<FuturesOrder>> listOpenOrders() async => const [];
  @override Future<List<FuturesOrder>> listHistory() async => const [];
  @override Future<FuturesOrder> cancelOrder(String id) async => throw UnimplementedError();
  @override Future<List<FuturesPosition>> listOpenPositions() async => openPositions;
  @override Future<List<FuturesPosition>> listClosedPositions() async => const [];

  @override
  Future<FuturesOrder> closePosition(String id) async {
    closedPositionId = id;
    return const FuturesOrder(
      id: 'close-1', clientOrderId: 'SBF-close',
      symbol: 'BTCUSDT',
      side: FuturesSide.short, positionSide: FuturesSide.long,
      type: FuturesOrderType.market, purpose: FuturesOrderPurpose.manualClose,
      status: FuturesOrderStatus.filled,
      reduceOnly: true, leverage: 3,
      requestedQuantity: 0.01, executedQuantity: 0.01,
    );
  }
}
