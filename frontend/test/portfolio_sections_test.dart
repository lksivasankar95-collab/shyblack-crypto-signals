// Binance-style section behaviour for the redesigned Portfolio: open orders, closed
// positions, transaction and funding history, and — most importantly — that an UNKNOWN
// exchange state stays UNKNOWN all the way to the screen instead of being widened to
// FILLED or closed.
import 'package:cryptosignals/core/di/providers.dart';
import 'package:cryptosignals/domain/entities/app_settings.dart';
import 'package:cryptosignals/domain/entities/portfolio_account.dart';
import 'package:cryptosignals/presentation/screens/portfolio/portfolio_screen.dart';
import 'package:cryptosignals/presentation/widgets/portfolio_widgets.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'fake_settings_repository.dart';
import 'portfolio_fake_repository.dart';

/// Binance-style section behaviour: open orders, closed positions, transaction and
/// funding history, and — most importantly — that an UNKNOWN exchange state stays UNKNOWN
/// all the way to the screen instead of being widened to FILLED or closed.
void main() {
  late FakePortfolioRepository repository;

  Future<void> pumpScreen(
    WidgetTester tester, {
    TradingAccount account = TradingAccount.live,
    PortfolioCategory tab = PortfolioCategory.spot,
  }) async {
    final settings = FakeSettingsRepository(account: account);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          portfolioRepositoryProvider.overrideWithValue(repository),
          settingsRepositoryProvider.overrideWith((ref) => settings),
        ],
        child: const MaterialApp(home: PortfolioScreen()),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(ValueKey('portfolio-category-${tab.apiValue}')),
    );
    await tester.pumpAndSettle();
  }

  String key(PortfolioMode mode, PortfolioCategory category) =>
      FakePortfolioRepository.key(mode, category);

  /// The sections below the fold are not built until scrolled into view, which is correct
  /// lazy-list behaviour. This drags the page list in bounded steps until the target
  /// appears, so a genuinely missing widget produces a clear assertion failure rather
  /// than a hang or a silent no-op.
  ///
  /// It drags [ListView] explicitly rather than `Scrollable`, because the first
  /// `Scrollable` in the tree is one of the horizontal filter rows, which scroll on the
  /// wrong axis.
  /// Selects a market section tab and settles.
  ///
  /// Exactly one section is mounted at a time, so a test that asserts on a
  /// section must open it first. This is navigation, not a weakening of the
  /// assertion: the same figures are still required once the tab is active.
  Future<void> openSection(WidgetTester tester, String section) async {
    final target = find.byKey(Key('section-tab-$section'));
    // The strip scrolls horizontally and builds lazily, so on a narrow phone a
    // later tab is neither measurable nor tappable until it is scrolled into
    // reach. Drag until the tab exists and sits inside the strip.
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

  Future<void> scrollTo(WidgetTester tester, Finder target) async {
    for (var step = 0; step < 16; step++) {
      if (target.evaluate().isNotEmpty) {
        await tester.pumpAndSettle();
        return;
      }
      await tester.drag(find.byType(ListView).first, const Offset(0, -200));
      await tester.pumpAndSettle();
    }
  }

  /// Counts only the status chips on order cards, never the same words used as filter
  /// labels, so an assertion about a rendered order state is unambiguous.
  Finder statusChip(String label) => find.descendant(
    of: find.byType(PortfolioOrderStatusChip),
    matching: find.text(label),
  );

  setUp(() {
    repository = FakePortfolioRepository();
    repository.accounts[key(
      PortfolioMode.live,
      PortfolioCategory.spot,
    )] = PortfolioAccount(
      accountMode: PortfolioMode.live,
      accountCategory: PortfolioCategory.spot,
      availability: PortfolioAvailability.available,
      equity: 1000,
      availableBalance: 900,
      exchange: 'BINANCE',
    );
    repository.accounts[key(
      PortfolioMode.live,
      PortfolioCategory.futures,
    )] = PortfolioAccount(
      accountMode: PortfolioMode.live,
      accountCategory: PortfolioCategory.futures,
      availability: PortfolioAvailability.available,
      equity: 5000,
      availableBalance: 4200,
      invested: 800,
      unrealizedPnl: 120.5,
      exchange: 'BINANCE',
    );
  });

  // ------------------------------------------------ A. open orders

  testWidgets('renders Binance-style open-order fields', (tester) async {
    repository.openOrders[key(
      PortfolioMode.live,
      PortfolioCategory.spot,
    )] = PortfolioOrders(
      accountMode: PortfolioMode.live,
      accountCategory: PortfolioCategory.spot,
      availability: PortfolioAvailability.available,
      source: 'EXCHANGE',
      orders: const [
        PortfolioOrder(
          accountMode: PortfolioMode.live,
          accountCategory: PortfolioCategory.spot,
          symbol: 'BTCUSDT',
          side: 'BUY',
          orderType: 'STOP_LOSS_LIMIT',
          status: PortfolioOrderStatus.open,
          price: 60000,
          stopPrice: 59000,
          originalQuantity: 0.5,
          executedQuantity: 0.2,
          remainingQuantity: 0.3,
          orderId: 555,
          clientOrderId: 'abc-123',
          createdAt: null,
        ),
      ],
    );

    await pumpScreen(tester);
    await openSection(tester, 'openOrders');

    expect(find.text('OPEN ORDERS'), findsOneWidget);
    expect(find.text('BTCUSDT'), findsOneWidget);
    expect(find.text('STOP_LOSS_LIMIT'), findsOneWidget);
    expect(statusChip('NEW'), findsOneWidget);
    expect(find.text('order 555'), findsOneWidget);
    expect(find.text('client abc-123'), findsOneWidget);
    // Stop price and the fill split are shown as the exchange reported them.
    expect(find.text('59000.00'), findsOneWidget);
    expect(find.text('0.20'), findsOneWidget);
    expect(find.text('0.30'), findsOneWidget);
  });

  testWidgets('an undeterminable order state renders as UNKNOWN, never FILLED', (
    tester,
  ) async {
    repository.openOrders[key(
      PortfolioMode.live,
      PortfolioCategory.futures,
    )] = PortfolioOrders(
      accountMode: PortfolioMode.live,
      accountCategory: PortfolioCategory.futures,
      availability: PortfolioAvailability.available,
      source: 'EXCHANGE',
      orders: const [
        PortfolioOrder(
          accountMode: PortfolioMode.live,
          accountCategory: PortfolioCategory.futures,
          symbol: 'ETHUSDT',
          side: 'SELL',
          positionSide: 'LONG',
          orderType: 'MARKET',
          status: PortfolioOrderStatus.unknown,
          orderId: 1,
        ),
        PortfolioOrder(
          accountMode: PortfolioMode.live,
          accountCategory: PortfolioCategory.futures,
          symbol: 'SOLUSDT',
          side: 'BUY',
          orderType: 'LIMIT',
          status: PortfolioOrderStatus.filled,
          orderId: 2,
        ),
      ],
    );

    await pumpScreen(tester, tab: PortfolioCategory.futures);
    await openSection(tester, 'openOrders');

    expect(statusChip('UNKNOWN'), findsOneWidget);
    // Exactly one FILLED chip on an order card: the order the exchange confirmed. The
    // unknown one was never promoted to filled.
    expect(statusChip('FILLED'), findsOneWidget);
  });

  testWidgets(
    'an unsupported open-order capability is not shown as "no open orders"',
    (tester) async {
      await pumpScreen(tester, account: TradingAccount.paper);
      await openSection(tester, 'openOrders');

      expect(find.text('No open-order book'), findsOneWidget);
      expect(find.text('No open orders.'), findsNothing);
    },
  );

  // -------------------------------------------- B. closed positions

  testWidgets('renders closed positions with proven fields only', (
    tester,
  ) async {
    repository.closedPositions[key(
      PortfolioMode.live,
      PortfolioCategory.futures,
    )] = PortfolioClosedPositions(
      accountMode: PortfolioMode.live,
      accountCategory: PortfolioCategory.futures,
      availability: PortfolioAvailability.available,
      source: 'EXCHANGE',
      positions: const [
        PortfolioClosedPosition(
          accountMode: PortfolioMode.live,
          accountCategory: PortfolioCategory.futures,
          symbol: 'BTCUSDT',
          side: 'LONG',
          entryPrice: 60000,
          exitPrice: 62000,
          quantity: 0.5,
          realizedPnl: 1000,
          fees: 12.5,
          // Funding has no position attribution upstream, so it stays unavailable.
          funding: null,
          openedAt: null,
          closedAt: null,
          duration: Duration(hours: 2, minutes: 15),
          orderIds: [11, 12],
          tradeIds: [21, 22],
        ),
      ],
    );

    await pumpScreen(tester, tab: PortfolioCategory.futures);

    expect(find.text('CLOSED POSITIONS'), findsOneWidget);
    expect(find.text('62000.00'), findsOneWidget);
    expect(find.text('+1000.00'), findsOneWidget);
    expect(find.text('2h 15m'), findsOneWidget);
    expect(find.text('orders 11, 12'), findsOneWidget);
    expect(find.text('trades 21, 22'), findsOneWidget);
    // Funding is rendered as unavailable rather than as zero.
    expect(find.text('0.00'), findsNothing);
  });

  testWidgets(
    'a partial reconstruction is labelled rather than silently filled in',
    (tester) async {
      repository.closedPositions[key(
        PortfolioMode.live,
        PortfolioCategory.futures,
      )] = PortfolioClosedPositions(
        accountMode: PortfolioMode.live,
        accountCategory: PortfolioCategory.futures,
        availability: PortfolioAvailability.available,
        source: 'EXCHANGE',
        partial: true,
        statusMessage:
            'Some round trips were only partly inside the requested window.',
        positions: const [
          PortfolioClosedPosition(
            accountMode: PortfolioMode.live,
            accountCategory: PortfolioCategory.futures,
            symbol: 'BTCUSDT',
            side: 'SHORT',
            exitPrice: 58000,
            realizedPnl: -250,
          ),
        ],
      );

      await pumpScreen(tester, tab: PortfolioCategory.futures);

      expect(find.text('Partial reconstruction'), findsOneWidget);
      // Entry price is unobservable, so it shows as unavailable and never as 0.00.
      expect(find.text('-250.00'), findsOneWidget);
      expect(find.text('0.00'), findsNothing);
    },
  );

  // ------------------------------------- C. transactions and funding

  testWidgets('spot transaction history reports it is not available', (
    tester,
  ) async {
    await pumpScreen(tester);
    await openSection(tester, 'tradeHistory');

    await scrollTo(tester, find.text('TRANSACTION HISTORY'));
    expect(find.text('TRANSACTION HISTORY'), findsOneWidget);
    // The reason must be explicit: the endpoint does not exist for spot, so nothing is
    // invented in its place and no empty ledger is shown.
    expect(find.text('Not available'), findsOneWidget);
    expect(find.textContaining('publishes no account income'), findsOneWidget);
    expect(find.text('No transactions in this window.'), findsNothing);
  });

  testWidgets('futures transaction history keeps the exchange income type', (
    tester,
  ) async {
    repository.transactions[key(
      PortfolioMode.live,
      PortfolioCategory.futures,
    )] = PortfolioHistory(
      accountMode: PortfolioMode.live,
      accountCategory: PortfolioCategory.futures,
      availability: PortfolioAvailability.available,
      source: 'EXCHANGE',
      entryType: 'INCOME',
      entries: const [
        PortfolioHistoryEntry(
          entryType: 'INCOME',
          accountMode: PortfolioMode.live,
          accountCategory: PortfolioCategory.futures,
          symbol: 'BTCUSDT',
          status: 'REALIZED_PNL',
          realizedPnl: 88.25,
          feeAsset: 'USDT',
          tradeId: 900,
          occurredAt: null,
        ),
      ],
    );

    await pumpScreen(tester, tab: PortfolioCategory.futures);
    await openSection(tester, 'tradeHistory');

    await scrollTo(tester, find.text('REALIZED_PNL'));
    expect(find.text('REALIZED_PNL'), findsOneWidget);
    expect(find.text('+88.25'), findsOneWidget);
    expect(find.text('trade 900'), findsOneWidget);
  });

  testWidgets('funding fees render as their own income rows', (tester) async {
    repository.fundingFees[key(
      PortfolioMode.live,
      PortfolioCategory.futures,
    )] = PortfolioHistory(
      accountMode: PortfolioMode.live,
      accountCategory: PortfolioCategory.futures,
      availability: PortfolioAvailability.available,
      source: 'EXCHANGE',
      entryType: 'FUNDING_FEE',
      entries: const [
        PortfolioHistoryEntry(
          entryType: 'FUNDING_FEE',
          accountMode: PortfolioMode.live,
          accountCategory: PortfolioCategory.futures,
          symbol: 'BTCUSDT',
          status: 'FUNDING_FEE',
          realizedPnl: -3.5,
          feeAsset: 'USDT',
          tradeId: 700,
          occurredAt: null,
        ),
      ],
    );

    await pumpScreen(tester, tab: PortfolioCategory.futures);
    await openSection(tester, 'tradeHistory');

    await scrollTo(tester, find.text('FUNDING FEES'));
    expect(find.text('FUNDING FEES'), findsOneWidget);
    expect(find.text('-3.50'), findsOneWidget);
  });

  // ------------------------------------------- D. order status parsing

  test('an unknown status string never parses as filled', () {
    expect(PortfolioOrderStatus.parse('FILLED'), PortfolioOrderStatus.filled);
    expect(PortfolioOrderStatus.parse('NEW'), PortfolioOrderStatus.open);
    expect(
      PortfolioOrderStatus.parse('PARTIALLY_FILLED'),
      PortfolioOrderStatus.partiallyFilled,
    );
    expect(
      PortfolioOrderStatus.parse('CANCELED'),
      PortfolioOrderStatus.canceled,
    );
    expect(PortfolioOrderStatus.parse('EXPIRED'), PortfolioOrderStatus.expired);
    // Anything the build does not recognise, including an HTTP-200-with-no-status, stays
    // unknown rather than defaulting to a terminal outcome.
    expect(
      PortfolioOrderStatus.parse('SOMETHING_NEW'),
      PortfolioOrderStatus.unknown,
    );
    expect(PortfolioOrderStatus.parse(null), PortfolioOrderStatus.unknown);
    expect(PortfolioOrderStatus.parse(''), PortfolioOrderStatus.unknown);
  });

  test('only an exchange-confirmed filled order reports itself as filled', () {
    expect(PortfolioOrderStatus.filled.isFilled, isTrue);
    expect(PortfolioOrderStatus.partiallyFilled.isFilled, isFalse);
    expect(PortfolioOrderStatus.unknown.isFilled, isFalse);
    // An unknown order is not open either: it must be reconciled, not assumed resting.
    expect(PortfolioOrderStatus.unknown.isOpen, isFalse);
    expect(PortfolioOrderStatus.open.isOpen, isTrue);
  });

  // ------------------------------------------------------- E. filters

  testWidgets('a bounded date range is offered, never an unbounded request', (
    tester,
  ) async {
    await pumpScreen(tester, tab: PortfolioCategory.futures);
    await openSection(tester, 'tradeHistory');

    expect(find.text('Today'), findsOneWidget);
    expect(find.text('7 Days'), findsOneWidget);
    expect(find.text('30 Days'), findsOneWidget);
    expect(find.text('All time'), findsNothing);
    expect(find.text('All'), findsNothing);
  });
}
