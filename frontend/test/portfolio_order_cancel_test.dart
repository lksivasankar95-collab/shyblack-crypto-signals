import 'package:cryptosignals/core/di/providers.dart';
import 'package:cryptosignals/domain/entities/app_settings.dart';
import 'package:cryptosignals/domain/entities/portfolio_account.dart';
import 'package:cryptosignals/presentation/providers/portfolio_controller.dart';
import 'package:cryptosignals/presentation/screens/portfolio/portfolio_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'fake_settings_repository.dart';
import 'portfolio_fake_repository.dart';

/// Open-order detail, trade detail and order cancellation.
///
/// The load-bearing properties:
///  * cancel is offered only when the backend published an addressable key AND the
///    exchange state is not terminal — no broken buttons;
///  * cancellation always confirms first, and confirming routes to the correct
///    market scope and the correct record;
///  * a failure is reported as a failure and leaves the order listed;
///  * PAPER never reaches a cancellation at all.
void main() {
  late FakePortfolioRepository portfolio;
  late FakeSettingsRepository settings;

  String key(PortfolioMode mode, PortfolioCategory category) =>
      '${mode.apiValue}:${category.apiValue}';

  PortfolioOrder liveOrder({
    String? cancelId = 'local-1',
    PortfolioCategory category = PortfolioCategory.spot,
    String side = 'BUY',
    PortfolioOrderStatus status = PortfolioOrderStatus.open,
    String symbol = 'BTCUSDT',
  }) {
    return PortfolioOrder(
      cancelId: cancelId,
      accountMode: PortfolioMode.live,
      accountCategory: category,
      symbol: symbol,
      side: side,
      orderType: 'LIMIT',
      status: status,
      price: 60000,
      originalQuantity: 1,
      executedQuantity: 0.4,
      remainingQuantity: 0.6,
      orderId: 555,
      clientOrderId: 'abc-123',
      positionSide: category == PortfolioCategory.futures ? 'LONG' : null,
      reduceOnly: category == PortfolioCategory.futures ? true : null,
      stopPrice: category == PortfolioCategory.futures ? 59000 : null,
      createdAt: DateTime.utc(2024, 1, 1, 10),
      updatedAt: DateTime.utc(2024, 1, 1, 11),
    );
  }

  Widget harness() => ProviderScope(
    overrides: [
      portfolioRepositoryProvider.overrideWithValue(portfolio),
      settingsRepositoryProvider.overrideWithValue(settings),
    ],
    child: const MaterialApp(home: PortfolioScreen()),
  );

  void seedOrders(
    List<PortfolioOrder> orders, {
    PortfolioMode mode = PortfolioMode.live,
  }) {
    portfolio.openOrders[key(mode, PortfolioCategory.spot)] = PortfolioOrders(
      accountMode: mode,
      accountCategory: PortfolioCategory.spot,
      availability: PortfolioAvailability.available,
      source: 'EXCHANGE',
      orders: orders,
    );
    portfolio.openOrders[key(
      mode,
      PortfolioCategory.futures,
    )] = PortfolioOrders(
      accountMode: mode,
      accountCategory: PortfolioCategory.futures,
      availability: PortfolioAvailability.available,
      source: 'EXCHANGE',
      orders: orders,
    );
  }

  void seedHistory(List<PortfolioHistoryEntry> entries) {
    portfolio.history[key(
      PortfolioMode.live,
      PortfolioCategory.spot,
    )] = PortfolioHistory(
      accountMode: PortfolioMode.live,
      accountCategory: PortfolioCategory.spot,
      availability: PortfolioAvailability.available,
      entries: entries,
    );
    portfolio.history[key(
      PortfolioMode.live,
      PortfolioCategory.futures,
    )] = PortfolioHistory(
      accountMode: PortfolioMode.live,
      accountCategory: PortfolioCategory.futures,
      availability: PortfolioAvailability.available,
      entries: entries,
    );
  }

  Future<void> openSection(WidgetTester tester, String section) async {
    final target = find.byKey(Key('section-tab-$section'));
    final strip = find.byKey(const Key('portfolio-section-strip'));
    for (var step = 0; step < 8; step++) {
      if (target.evaluate().isNotEmpty) {
        final bounds = tester.getRect(strip);
        final wanted = tester.getRect(target);
        if (wanted.center.dx >= bounds.left && wanted.center.dx <= bounds.right)
          break;
      }
      await tester.drag(strip, const Offset(-120, 0));
      await tester.pumpAndSettle();
    }
    await tester.tap(target, warnIfMissed: false);
    await tester.pumpAndSettle();
  }

  Future<void> showOrders(
    WidgetTester tester, {
    PortfolioCategory tab = PortfolioCategory.spot,
  }) async {
    await tester.pumpWidget(harness());
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(ValueKey('portfolio-category-${tab.apiValue}')),
    );
    await tester.pumpAndSettle();
    await openSection(tester, 'openOrders');
  }

  setUp(() {
    portfolio = FakePortfolioRepository();
    settings = FakeSettingsRepository(account: TradingAccount.live);
  });

  // ── Order detail ─────────────────────────────────────────────

  group('order detail', () {
    testWidgets('a spot order opens its detail with spot fields', (
      tester,
    ) async {
      seedOrders([liveOrder()]);
      await showOrders(tester);

      await tester.tap(find.byKey(const Key('order-card-abc-123')));
      await tester.pumpAndSettle();

      expect(find.text('BTCUSDT'), findsWidgets);
      expect(find.text('SPOT'), findsWidgets);
      expect(find.text('LIVE'), findsWidgets);
      expect(find.text('555'), findsOneWidget);
      expect(find.text('abc-123'), findsOneWidget);
      expect(find.text('LIMIT'), findsWidgets);
      expect(find.text('0.60'), findsWidgets);
    });

    testWidgets('a spot order shows no futures-only attributes', (
      tester,
    ) async {
      seedOrders([liveOrder()]);
      await showOrders(tester);

      await tester.tap(find.byKey(const Key('order-card-abc-123')));
      await tester.pumpAndSettle();

      // The market tab itself always shows a FUTURES label, so the futures-only
      // attributes are what distinguish a spot detail.
      expect(find.text('Position Side'), findsNothing);
      expect(find.text('Reduce Only'), findsNothing);
      expect(find.text('Trigger Price'), findsNothing);
    });

    testWidgets('a futures order shows its futures attributes', (tester) async {
      seedOrders([
        liveOrder(category: PortfolioCategory.futures, cancelId: 'f-1'),
      ]);
      await showOrders(tester, tab: PortfolioCategory.futures);

      await tester.tap(find.byKey(const Key('order-card-abc-123')));
      await tester.pumpAndSettle();

      expect(find.text('FUTURES'), findsWidgets);
      expect(find.text('Position Side'), findsOneWidget);
      expect(find.text('Reduce Only'), findsOneWidget);
      expect(find.text('LONG'), findsOneWidget);
      expect(find.text('Trigger Price'), findsOneWidget);
    });
  });

  // ── Cancel visibility ─────────────────────────────────────────

  group('cancel availability', () {
    testWidgets('a cancellable order shows Cancel', (tester) async {
      seedOrders([liveOrder()]);
      await showOrders(tester);

      await tester.tap(find.byKey(const Key('order-card-abc-123')));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('order-cancel')), findsOneWidget);
    });

    testWidgets('an order with no addressable record hides Cancel', (
      tester,
    ) async {
      seedOrders([liveOrder(cancelId: null)]);
      await showOrders(tester);

      await tester.tap(find.byKey(const Key('order-card-abc-123')));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('order-cancel')), findsNothing);
      expect(find.text('Not cancellable'), findsOneWidget);
    });

    testWidgets('a filled order hides Cancel', (tester) async {
      seedOrders([
        liveOrder(status: PortfolioOrderStatus.filled, cancelId: 'x'),
      ]);
      await showOrders(tester);

      await tester.tap(find.byKey(const Key('order-card-abc-123')));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('order-cancel')), findsNothing);
    });

    testWidgets('a cancelled order hides Cancel', (tester) async {
      seedOrders([
        liveOrder(status: PortfolioOrderStatus.canceled, cancelId: 'x'),
      ]);
      await showOrders(tester);

      await tester.tap(find.byKey(const Key('order-card-abc-123')));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('order-cancel')), findsNothing);
    });

    testWidgets('a partially filled order still shows Cancel', (tester) async {
      seedOrders([
        liveOrder(status: PortfolioOrderStatus.partiallyFilled, cancelId: 'x'),
      ]);
      await showOrders(tester);

      await tester.tap(find.byKey(const Key('order-card-abc-123')));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('order-cancel')), findsOneWidget);
    });
  });

  // ── Cancel flow ──────────────────────────────────────────────

  group('cancel', () {
    Future<void> openConfirm(WidgetTester tester) async {
      await showOrders(tester);
      await tester.tap(find.byKey(const Key('order-card-abc-123')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('order-cancel')));
      await tester.pumpAndSettle();
    }

    testWidgets('the first tap never cancels', (tester) async {
      seedOrders([liveOrder()]);
      await showOrders(tester);

      await tester.tap(find.byKey(const Key('order-card-abc-123')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('order-cancel')));
      await tester.pumpAndSettle();

      expect(find.text('Cancel BTCUSDT order?'), findsOneWidget);
      expect(portfolio.cancelOrderCalls, isEmpty);
    });

    testWidgets('the confirmation states the order figures', (tester) async {
      seedOrders([liveOrder()]);
      await openConfirm(tester);

      final dialog = find.byWidgetPredicate((w) => w is AlertDialog);
      for (final label in [
        'Market',
        'Account',
        'Side',
        'Order Type',
        'Price',
        'Quantity',
        'Filled',
        'Remaining',
      ]) {
        expect(
          find.descendant(of: dialog, matching: find.text(label)),
          findsOneWidget,
          reason: 'the cancel confirmation must state $label',
        );
      }
      expect(
        find.descendant(of: dialog, matching: find.text('LIVE')),
        findsOneWidget,
      );
    });

    testWidgets('dismissing the confirmation cancels nothing', (tester) async {
      seedOrders([liveOrder()]);
      await openConfirm(tester);

      await tester.tap(find.byKey(const Key('cancel-dialog-cancel')));
      await tester.pumpAndSettle();

      expect(portfolio.cancelOrderCalls, isEmpty);
    });

    testWidgets('confirming routes to the spot scope and the local id', (
      tester,
    ) async {
      seedOrders([liveOrder(cancelId: 'local-abc')]);
      await openConfirm(tester);

      await tester.tap(find.byKey(const Key('cancel-dialog-confirm')));
      await tester.pumpAndSettle();

      expect(portfolio.cancelOrderCalls, ['LIVE:SPOT:local-abc']);
      expect(find.textContaining('cancelled'), findsOneWidget);
    });

    testWidgets('confirming a futures order routes to the futures scope', (
      tester,
    ) async {
      seedOrders([
        liveOrder(category: PortfolioCategory.futures, cancelId: 'f-local'),
      ]);
      await showOrders(tester, tab: PortfolioCategory.futures);
      await tester.tap(find.byKey(const Key('order-card-abc-123')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('order-cancel')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('cancel-dialog-confirm')));
      await tester.pumpAndSettle();

      expect(portfolio.cancelOrderCalls, ['LIVE:FUTURES:f-local']);
    });

    testWidgets('a failed cancellation is reported, not swallowed', (
      tester,
    ) async {
      seedOrders([liveOrder()]);
      await openConfirm(tester);

      portfolio.paperFailWith = Exception('Order is not cancellable');
      await tester.tap(find.byKey(const Key('cancel-dialog-confirm')));
      await tester.pumpAndSettle();

      expect(portfolio.cancelOrderCalls, ['LIVE:SPOT:local-1']);
      expect(find.textContaining('Cancel failed'), findsWidgets);
    });

    testWidgets('paper accounts can never cancel', (tester) async {
      settings.setAccount(TradingAccount.paper);
      portfolio.openOrders[key(
        PortfolioMode.paper,
        PortfolioCategory.spot,
      )] = PortfolioOrders(
        accountMode: PortfolioMode.paper,
        accountCategory: PortfolioCategory.spot,
        availability: PortfolioAvailability.unsupported,
        source: 'LOCAL_PAPER',
        orders: [
          PortfolioOrder(
            cancelId: 'should-not-be-used',
            accountMode: PortfolioMode.paper,
            accountCategory: PortfolioCategory.spot,
            symbol: 'BTCUSDT',
            orderType: 'LIMIT',
            status: PortfolioOrderStatus.open,
          ),
        ],
      );

      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openSection(tester, 'openOrders');

      final container = ProviderScope.containerOf(
        tester.element(find.byType(PortfolioScreen)),
      );
      final actions = container.read(
        portfolioOrderActionControllerProvider.notifier,
      );

      await expectLater(
        actions.cancel(
          mode: PortfolioMode.paper,
          category: PortfolioCategory.spot,
          cancelId: 'should-not-be-used',
        ),
        throwsA(isA<PortfolioCapitalNotAvailable>()),
      );
      expect(portfolio.cancelOrderCalls, isEmpty);
    });
  });

  // ── Trade detail ─────────────────────────────────────────────

  group('trade detail', () {
    Future<void> openTrade(
      WidgetTester tester, {
      PortfolioCategory tab = PortfolioCategory.spot,
    }) async {
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await tester.tap(
        find.byKey(ValueKey('portfolio-category-${tab.apiValue}')),
      );
      await tester.pumpAndSettle();
      await openSection(tester, 'tradeHistory');
    }

    PortfolioHistoryEntry trade({
      PortfolioCategory category = PortfolioCategory.spot,
      double? realizedPnl,
      String? positionSide,
    }) {
      return PortfolioHistoryEntry(
        entryType: 'TRADE',
        accountMode: PortfolioMode.live,
        accountCategory: category,
        symbol: 'ETHUSDT',
        orderId: 4242,
        tradeId: 777,
        side: 'BUY',
        positionSide: positionSide,
        orderType: 'LIMIT',
        status: 'FILLED',
        price: 2500,
        quantity: 2,
        quoteQuantity: 5000,
        fee: 5,
        feeAsset: 'USDT',
        realizedPnl: realizedPnl,
        occurredAt: DateTime.utc(2024, 1, 2, 8, 30),
      );
    }

    testWidgets('a spot trade opens its detail without futures fields', (
      tester,
    ) async {
      seedHistory([trade()]);
      await openTrade(tester);

      await tester.tap(find.text('ETHUSDT').first);
      await tester.pumpAndSettle();

      expect(find.text('777'), findsOneWidget);
      expect(find.text('4242'), findsOneWidget);
      expect(find.text('2500.00'), findsWidgets);
      expect(find.text('5000.00'), findsWidgets);
      expect(find.text('USDT'), findsOneWidget);
      expect(find.text('Position Side'), findsNothing);
      expect(find.text('Realized P&L'), findsNothing);
    });

    testWidgets('a futures trade shows position side and realized P&L', (
      tester,
    ) async {
      seedHistory([
        trade(
          category: PortfolioCategory.futures,
          realizedPnl: 12.5,
          positionSide: 'SHORT',
        ),
      ]);
      await openTrade(tester, tab: PortfolioCategory.futures);

      await tester.tap(find.text('ETHUSDT').first);
      await tester.pumpAndSettle();

      expect(find.text('FUTURES'), findsWidgets);
      expect(find.text('Position Side'), findsOneWidget);
      expect(find.text('SHORT'), findsWidgets);
      expect(find.text('Realized P&L'), findsOneWidget);
      expect(find.text('12.50'), findsOneWidget);
    });
  });
}
