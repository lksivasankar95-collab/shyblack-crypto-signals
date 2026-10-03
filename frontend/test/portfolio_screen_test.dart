import 'package:cryptosignals/core/di/providers.dart';
import 'package:cryptosignals/domain/entities/app_settings.dart';
import 'package:cryptosignals/domain/entities/portfolio_account.dart';
import 'package:cryptosignals/presentation/providers/settings_controller.dart';
import 'package:cryptosignals/presentation/screens/portfolio/portfolio_screen.dart';
import 'package:cryptosignals/presentation/widgets/portfolio_widgets.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'fake_settings_repository.dart';
import 'portfolio_fake_repository.dart';

/// The Portfolio is driven by Settings, never by a Portfolio control.
///
/// Every test here therefore sets up two things: a Settings account mode, and a
/// portfolio repository seeded per scope. What is asserted is that the account mode
/// comes from Settings, that the screen exposes no PAPER/LIVE selector of its own, and
/// that one account mode's data can never be painted under the other's label.
void main() {
  late FakePortfolioRepository repository;
  late FakeSettingsRepository settings;

  PortfolioAccount account({
    required PortfolioMode mode,
    required PortfolioCategory category,
    PortfolioAvailability availability = PortfolioAvailability.available,
    double? equity,
    double? availableBalance,
    double? invested,
    double? realizedPnl,
    double? unrealizedPnl,
    int? openPositionCount,
    String? exchange,
    String? statusMessage,
  }) {
    return PortfolioAccount(
      accountMode: mode,
      accountCategory: category,
      availability: availability,
      exchange: exchange,
      equity: equity,
      availableBalance: availableBalance,
      invested: invested,
      realizedPnl: realizedPnl,
      unrealizedPnl: unrealizedPnl,
      openPositionCount: openPositionCount,
      statusMessage: statusMessage,
    );
  }

  PortfolioPositions positions({
    required PortfolioMode mode,
    required PortfolioCategory category,
    PortfolioAvailability availability = PortfolioAvailability.available,
    List<PortfolioPosition> items = const [],
    String? statusMessage,
  }) {
    return PortfolioPositions(
      accountMode: mode,
      accountCategory: category,
      availability: availability,
      positions: items,
      statusMessage: statusMessage,
    );
  }

  void seedSpot({
    required PortfolioMode mode,
    PortfolioAvailability availability = PortfolioAvailability.available,
    double? equity,
    double? availableBalance,
  }) {
    repository.accounts[FakePortfolioRepository.key(
      mode,
      PortfolioCategory.spot,
    )] = account(
      mode: mode,
      category: PortfolioCategory.spot,
      availability: availability,
      equity: equity,
      availableBalance: availableBalance,
      exchange: mode == PortfolioMode.live ? 'BINANCE' : null,
    );
  }

  /// Drags the page list in bounded steps until [target] is built.
  ///
  /// Sections below the fold are only built once scrolled into view, which is correct
  /// lazy-list behaviour. It drags [ListView] explicitly rather than `Scrollable`,
  /// because the first `Scrollable` in the tree is one of the horizontal filter rows,
  /// which scroll on the wrong axis.
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

  Future<void> pumpScreen(
    WidgetTester tester, {
    TradingAccount account = TradingAccount.paper,
  }) async {
    settings = FakeSettingsRepository(account: account);
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
  }

  setUp(() {
    repository = FakePortfolioRepository();
  });

  // ------------------------------------------------ A. Settings drives the mode

  testWidgets('Settings PAPER makes the Portfolio read the PAPER scope', (
    tester,
  ) async {
    seedSpot(mode: PortfolioMode.paper, equity: 111.11);
    seedSpot(mode: PortfolioMode.live, equity: 999999);

    await pumpScreen(tester);

    expect(repository.accountCalls, contains('PAPER:SPOT'));
    expect(repository.accountCalls, isNot(contains('LIVE:SPOT')));
    expect(find.text('PAPER ACCOUNT'), findsOneWidget);
    expect(find.text('LIVE ACCOUNT'), findsNothing);
    expect(find.text('Paper Spot Account'), findsOneWidget);
    expect(find.text('111.11'), findsOneWidget);
    expect(find.text('999999.00'), findsNothing);
  });

  testWidgets('Settings LIVE makes the Portfolio read the LIVE scope', (
    tester,
  ) async {
    seedSpot(mode: PortfolioMode.paper, equity: 111.11);
    seedSpot(mode: PortfolioMode.live, equity: 999999);

    await pumpScreen(tester, account: TradingAccount.live);

    expect(repository.accountCalls, contains('LIVE:SPOT'));
    expect(repository.accountCalls, isNot(contains('PAPER:SPOT')));
    expect(find.text('LIVE ACCOUNT'), findsOneWidget);
    expect(find.text('Binance Spot Account'), findsOneWidget);
    expect(find.text('999999.00'), findsOneWidget);
    expect(find.text('111.11'), findsNothing);
  });

  testWidgets('changing the account mode in Settings re-reads the Portfolio', (
    tester,
  ) async {
    seedSpot(mode: PortfolioMode.paper, equity: 111.11);
    seedSpot(mode: PortfolioMode.live, equity: 999999);

    await pumpScreen(tester);
    expect(find.text('111.11'), findsOneWidget);

    // Exactly what the Settings screen does on a mode change: patch the notifier, which
    // writes through the settings repository.
    final container = ProviderScope.containerOf(
      tester.element(find.byType(PortfolioScreen)),
    );
    await container
        .read(settingsControllerProvider.notifier)
        .setTradingAccount(TradingAccount.live);
    await tester.pumpAndSettle();

    expect(repository.accountCalls, contains('LIVE:SPOT'));
    expect(find.text('999999.00'), findsOneWidget);
    expect(find.text('111.11'), findsNothing);
    expect(find.text('LIVE ACCOUNT'), findsOneWidget);
  });

  testWidgets(
    'an unresolved account mode renders nothing rather than a paper fallback',
    (tester) async {
      seedSpot(mode: PortfolioMode.paper, equity: 111.11);
      settings = FakeSettingsRepository(account: TradingAccount.live)
        ..failLoad = Exception('settings unavailable');

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

      expect(find.text('Account mode unavailable'), findsOneWidget);
      // The critical assertion: no account was requested at all, so a live-selected user
      // can never be shown a simulated balance.
      expect(repository.accountCalls, isEmpty);
      expect(find.text('111.11'), findsNothing);
    },
  );

  // ------------------------------- B. no PAPER/LIVE selector inside Portfolio

  testWidgets('exposes no PAPER / LIVE selector of its own', (tester) async {
    seedSpot(mode: PortfolioMode.paper, equity: 500);
    await pumpScreen(tester);

    expect(find.byKey(const ValueKey('portfolio-mode-PAPER')), findsNothing);
    expect(find.byKey(const ValueKey('portfolio-mode-LIVE')), findsNothing);

    // The account is named, but only as a non-interactive badge: tapping it must not
    // change the account mode, because that happens in Settings and nowhere else.
    expect(find.byType(PortfolioAccountBadge), findsOneWidget);
    final badge = tester.widget<PortfolioAccountBadge>(
      find.byType(PortfolioAccountBadge),
    );
    expect(badge.mode, PortfolioMode.paper);
    expect(
      find.descendant(
        of: find.byType(PortfolioAccountBadge),
        matching: find.byType(GestureDetector),
      ),
      findsNothing,
    );
    expect(
      find.descendant(
        of: find.byType(PortfolioAccountBadge),
        matching: find.byType(InkWell),
      ),
      findsNothing,
    );
  });

  testWidgets('only SPOT, FUTURES and OPTIONS are offered as tabs', (
    tester,
  ) async {
    await pumpScreen(tester);

    expect(PortfolioCategory.portfolioTabs, [
      PortfolioCategory.spot,
      PortfolioCategory.futures,
      PortfolioCategory.options,
    ]);
    // MAIN is the backend aggregate scope, not an account a user owns.
    expect(find.byKey(const ValueKey('portfolio-category-MAIN')), findsNothing);
    expect(
      find.byKey(const ValueKey('portfolio-category-SPOT')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('portfolio-category-FUTURES')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('portfolio-category-OPTIONS')),
      findsOneWidget,
    );
  });

  // ---------------------------------------------- C. category isolation

  testWidgets('each tab requests its own market scope', (tester) async {
    await pumpScreen(tester);

    for (final category in ['SPOT', 'FUTURES', 'OPTIONS']) {
      await tester.tap(find.byKey(ValueKey('portfolio-category-$category')));
      await tester.pumpAndSettle();
      expect(repository.accountCalls, contains('PAPER:$category'));
    }
  });

  testWidgets('SPOT and FUTURES keep their own positions', (tester) async {
    repository.positions[FakePortfolioRepository.key(
      PortfolioMode.paper,
      PortfolioCategory.futures,
    )] = positions(
      mode: PortfolioMode.paper,
      category: PortfolioCategory.futures,
      items: const [
        PortfolioPosition(
          accountMode: PortfolioMode.paper,
          accountCategory: PortfolioCategory.futures,
          symbol: 'ETHUSDT',
          side: 'SHORT',
          quantity: 2,
        ),
      ],
    );

    await pumpScreen(tester);
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('portfolio-category-FUTURES')));
    await tester.pumpAndSettle();
    expect(find.text('ETHUSDT'), findsOneWidget);
    // A futures position must not leak into the spot wallet view.
    await tester.tap(find.byKey(const ValueKey('portfolio-category-SPOT')));
    await tester.pumpAndSettle();
    expect(find.text('ETHUSDT'), findsNothing);
  });

  testWidgets(
    'spot shows wallet assets and never a leveraged positions section',
    (tester) async {
      seedSpot(mode: PortfolioMode.live, equity: 500);
      repository.holdings[FakePortfolioRepository.key(
        PortfolioMode.live,
        PortfolioCategory.spot,
      )] = PortfolioHoldings(
        accountMode: PortfolioMode.live,
        accountCategory: PortfolioCategory.spot,
        availability: PortfolioAvailability.available,
        source: 'EXCHANGE',
        holdings: const [
          PortfolioHolding(asset: 'BTC', free: 0.5, locked: 0.1, total: 0.6),
        ],
      );

      await pumpScreen(tester, account: TradingAccount.live);

      expect(find.text('WALLET / ASSETS'), findsOneWidget);
      expect(find.text('BTC'), findsOneWidget);
      // Spot has no open-position concept, so no position heading may be shown.
      expect(find.text('OPEN POSITIONS'), findsNothing);
      expect(find.text('CLOSED POSITIONS'), findsNothing);
    },
  );

  testWidgets('futures shows open and closed positions sections', (
    tester,
  ) async {
    await pumpScreen(tester);
    await tester.tap(find.byKey(const ValueKey('portfolio-category-FUTURES')));
    await tester.pumpAndSettle();

    // The market sections are one-at-a-time tabs, so each is visited in turn
    // rather than asserted simultaneously.
    await openSection(tester, 'positions');
    expect(find.text('OPEN POSITIONS'), findsOneWidget);
    expect(find.text('CLOSED POSITIONS'), findsOneWidget);

    await openSection(tester, 'openOrders');
    expect(find.text('OPEN ORDERS'), findsOneWidget);

    await openSection(tester, 'tradeHistory');
    for (final heading in ['HISTORY', 'TRANSACTION HISTORY', 'FUNDING FEES']) {
      await scrollTo(tester, find.text(heading));
      expect(
        find.text(heading),
        findsOneWidget,
        reason: 'the futures account must expose a $heading section',
      );
    }
  });

  testWidgets('spot has no funding-fee section, because spot pays no funding', (
    tester,
  ) async {
    await pumpScreen(tester);
    expect(find.text('FUNDING FEES'), findsNothing);
  });

  // ------------------------------------------------------ D. Options

  testWidgets('Options shows an explicit unsupported state with no figures', (
    tester,
  ) async {
    await pumpScreen(tester);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('portfolio-category-OPTIONS')));
    await tester.pumpAndSettle();

    expect(find.text('Options coming soon'), findsOneWidget);
    expect(find.text('0.00'), findsNothing);
    // No balance, position, order or history section may be rendered for options.
    expect(find.text('OPEN ORDERS'), findsNothing);
    expect(find.text('OPEN POSITIONS'), findsNothing);
    expect(find.text('TRANSACTION HISTORY'), findsNothing);
  });

  // --------------------------------------- E. live connection unavailable

  testWidgets('an unavailable live account says so and never shows paper data', (
    tester,
  ) async {
    seedSpot(
      mode: PortfolioMode.live,
      availability: PortfolioAvailability.notConnected,
      equity: null,
    );
    // Paper has a perfectly good balance; none of it may appear here.
    seedSpot(mode: PortfolioMode.paper, equity: 4242);

    await pumpScreen(tester, account: TradingAccount.live);

    // Two 'LIVE ACCOUNT' texts are expected and correct: the read-only badge in the
    // header, and the title of the connection-unavailable panel. Neither is a control.
    expect(find.text('LIVE ACCOUNT'), findsNWidgets(2));

    await scrollTo(tester, find.textContaining('Connection unavailable'));
    // The panel must explain that nothing is substituted for the unreachable account.
    expect(find.textContaining('no simulated data is shown'), findsOneWidget);
    expect(find.text('4242.00'), findsNothing);
    expect(find.text('0.00'), findsNothing);
  });

  // --------------------------------------- F. nulls, zeros and availability

  testWidgets('null values render as a dash and never as zero', (tester) async {
    seedSpot(mode: PortfolioMode.paper);
    await pumpScreen(tester);

    expect(find.text(PortfolioValue.missing), findsWidgets);
    expect(find.text('0.00'), findsNothing);
  });

  testWidgets('an explicit zero is rendered as a zero', (tester) async {
    seedSpot(mode: PortfolioMode.paper, equity: 0, availableBalance: 0);
    await pumpScreen(tester);

    expect(find.text('0.00'), findsWidgets);
  });

  testWidgets('the format helper never turns null into zero', (tester) async {
    expect(PortfolioValue.format(null), PortfolioValue.missing);
    expect(PortfolioValue.format(0), isNot(PortfolioValue.missing));
    expect(PortfolioValue.format(null, signed: true), PortfolioValue.missing);
  });

  // ------------------------------------------------- G. errors and auth

  testWidgets('a transport failure shows an error state with retry', (
    tester,
  ) async {
    repository.failWith = Exception('network down');
    await pumpScreen(tester);

    expect(find.text('Could not load this account'), findsOneWidget);
    expect(find.text('RETRY'), findsOneWidget);
    expect(find.text('No positions in this account.'), findsNothing);
  });

  testWidgets('no credential or listen key is ever displayed', (tester) async {
    seedSpot(mode: PortfolioMode.live, equity: 900);
    await pumpScreen(tester, account: TradingAccount.live);

    for (final forbidden in [
      'apiKey',
      'apiSecret',
      'listenKey',
      'X-MBX-APIKEY',
      'signature',
    ]) {
      expect(find.textContaining(forbidden), findsNothing);
    }
  });

  // ------------------------------------- H. stale state across account modes

  testWidgets('a response for another scope is never painted', (tester) async {
    // Deliberately return a LIVE payload while PAPER is selected.
    repository.accounts[FakePortfolioRepository.key(
      PortfolioMode.paper,
      PortfolioCategory.spot,
    )] = account(
      mode: PortfolioMode.live,
      category: PortfolioCategory.spot,
      equity: 424242,
    );
    await pumpScreen(tester);

    expect(
      find.text('424242.00'),
      findsNothing,
      reason: 'a mismatched scope must be discarded, not displayed',
    );
  });

  // --------------------------------------- I. layout and no trade actions

  testWidgets('renders without overflow on a narrow screen', (tester) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    seedSpot(mode: PortfolioMode.paper, equity: 1000);
    await pumpScreen(tester);

    expect(tester.takeException(), isNull);
    expect(find.text('PORTFOLIO'), findsOneWidget);
  });

  testWidgets('exposes no trading controls', (tester) async {
    await pumpScreen(tester);

    for (final label in [
      'BUY',
      'SELL',
      'CLOSE',
      'CANCEL',
      'EXECUTE',
      'AUTO TRADE',
    ]) {
      expect(find.text(label), findsNothing);
    }
  });
}
