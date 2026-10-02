import 'package:cryptosignals/core/di/providers.dart';
import 'package:cryptosignals/domain/entities/portfolio_account.dart';
import 'package:cryptosignals/presentation/providers/portfolio_controller.dart';
import 'package:cryptosignals/presentation/screens/portfolio/portfolio_screen.dart';
import 'package:cryptosignals/presentation/widgets/portfolio_widgets.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'portfolio_fake_repository.dart';

void main() {
  late FakePortfolioRepository repository;

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
    String? statusMessage,
  }) {
    return PortfolioAccount(
      accountMode: mode,
      accountCategory: category,
      availability: availability,
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

  void seedPaperMain() {
    repository.accounts[FakePortfolioRepository.key(
      PortfolioMode.paper,
      PortfolioCategory.main,
    )] = account(
      mode: PortfolioMode.paper,
      category: PortfolioCategory.main,
      equity: 1000,
      availableBalance: 750,
      invested: 250,
      realizedPnl: 25,
      unrealizedPnl: -5,
      openPositionCount: 1,
    );
  }

  Future<void> pumpScreen(WidgetTester tester) async {

    await tester.pumpWidget(
      ProviderScope(
        overrides: [portfolioRepositoryProvider.overrideWithValue(repository)],
        child: const MaterialApp(home: PortfolioScreen()),
      ),
    );
    await tester.pump();
  }

  setUp(() {
    repository = FakePortfolioRepository();
  });

  // ------------------------------------------------- A. default state

  testWidgets('defaults to PAPER and MAIN', (tester) async {
    seedPaperMain();
    await pumpScreen(tester);
    await tester.pumpAndSettle();

    final container = ProviderScope.containerOf(
      tester.element(find.byType(PortfolioScreen)),
    );
    expect(container.read(portfolioSelectionProvider).mode, PortfolioMode.paper);
    expect(container.read(portfolioSelectionProvider).category, PortfolioCategory.main);
    expect(find.text('SIMULATION'), findsOneWidget);
    expect(repository.accountCalls, contains('PAPER:MAIN'));
  });

  // --------------------------------------------- B. mode switching

  testWidgets('switching PAPER to LIVE requests the LIVE scope', (tester) async {
    seedPaperMain();
    repository.accounts[FakePortfolioRepository.key(
      PortfolioMode.live,
      PortfolioCategory.main,
    )] = account(
      mode: PortfolioMode.live,
      category: PortfolioCategory.main,
      availability: PortfolioAvailability.unavailable,
    );
    await pumpScreen(tester);
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('portfolio-mode-LIVE')));
    await tester.pumpAndSettle();

    expect(repository.accountCalls, contains('LIVE:MAIN'));
    expect(find.text('LIVE'), findsWidgets);
    // The LIVE main wallet is unavailable and must be said so, not shown as 0.
    expect(find.text('Summary unavailable'), findsOneWidget);
  });

  testWidgets('LIVE figures never appear under a PAPER label', (tester) async {
    // Distinct values per mode so a cross-mode leak is visible.
    repository.accounts[FakePortfolioRepository.key(
      PortfolioMode.paper,
      PortfolioCategory.spot,
    )] = account(
      mode: PortfolioMode.paper,
      category: PortfolioCategory.spot,
      equity: 111.11,
    );
    repository.accounts[FakePortfolioRepository.key(
      PortfolioMode.live,
      PortfolioCategory.spot,
    )] = account(
      mode: PortfolioMode.live,
      category: PortfolioCategory.spot,
      equity: 999999,
    );

    await pumpScreen(tester);
    await tester.pumpAndSettle();

    // Start on PAPER, switch to SPOT: only the paper figure may appear.
    await tester.tap(find.byKey(const ValueKey('portfolio-category-SPOT')));
    await tester.pumpAndSettle();
    expect(find.text('111.11'), findsOneWidget);
    expect(find.text('999999.00'), findsNothing);

    // Switch to LIVE and back to SPOT: now only the live figure may appear.
    await tester.tap(find.byKey(const ValueKey('portfolio-mode-LIVE')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('portfolio-category-SPOT')));
    await tester.pumpAndSettle();
    expect(find.text('999999.00'), findsOneWidget);
    expect(find.text('111.11'), findsNothing);

    // And back to PAPER: the live figure must disappear again.
    await tester.tap(find.byKey(const ValueKey('portfolio-mode-PAPER')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('portfolio-category-SPOT')));
    await tester.pumpAndSettle();
    expect(find.text('111.11'), findsOneWidget);
    expect(find.text('999999.00'), findsNothing);
  });

  // ------------------------------------------- C. category switching

  testWidgets('each category tab requests its own scope', (tester) async {
    seedPaperMain();
    await pumpScreen(tester);
    await tester.pumpAndSettle();

    for (final category in ['SPOT', 'FUTURES', 'OPTIONS']) {
      await tester.tap(find.byKey(ValueKey('portfolio-category-$category')));
      await tester.pumpAndSettle();
      expect(repository.accountCalls, contains('PAPER:$category'));
    }
  });

  testWidgets('SPOT and FUTURES keep their own positions', (tester) async {
    repository.positions[FakePortfolioRepository.key(
      PortfolioMode.paper,
      PortfolioCategory.spot,
    )] = positions(
      mode: PortfolioMode.paper,
      category: PortfolioCategory.spot,
      items: const [
        PortfolioPosition(
          accountMode: PortfolioMode.paper,
          accountCategory: PortfolioCategory.spot,
          symbol: 'BTCUSDT',
          side: 'LONG',
          quantity: 1,
        ),
      ],
    );
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

    await tester.tap(find.byKey(const ValueKey('portfolio-category-SPOT')));
    await tester.pumpAndSettle();
    expect(find.text('BTCUSDT'), findsOneWidget);
    expect(find.text('ETHUSDT'), findsNothing);

    await tester.tap(find.byKey(const ValueKey('portfolio-category-FUTURES')));
    await tester.pumpAndSettle();
    expect(find.text('ETHUSDT'), findsOneWidget);
    expect(find.text('BTCUSDT'), findsNothing);
  });

  // ------------------------------------------------ D. API integration

  testWidgets('requests exactly the documented endpoints and modes',
      (tester) async {
    seedPaperMain();
    await pumpScreen(tester);
    await tester.pumpAndSettle();

    expect(repository.accountCalls.first, 'PAPER:MAIN');
    expect(repository.positionCalls.first, 'PAPER:MAIN');
  });

  // ---------------------------------------- E/F. nulls and availability

  testWidgets('null values render as a dash and never as zero', (tester) async {
    repository.accounts[FakePortfolioRepository.key(
      PortfolioMode.paper,
      PortfolioCategory.spot,
    )] = account(
      mode: PortfolioMode.paper,
      category: PortfolioCategory.spot,
      // Paper SPOT has no balance because the wallet is shared at MAIN.
      equity: null,
      availableBalance: null,
      realizedPnl: null,
    );
    await pumpScreen(tester);
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('portfolio-category-SPOT')));
    await tester.pumpAndSettle();

    expect(find.text(PortfolioValue.missing), findsWidgets);
    expect(find.text('0.00'), findsNothing);
  });

  testWidgets('an explicit zero is rendered as a zero', (tester) async {
    repository.accounts[FakePortfolioRepository.key(
      PortfolioMode.paper,
      PortfolioCategory.main,
    )] = account(
      mode: PortfolioMode.paper,
      category: PortfolioCategory.main,
      equity: 0,
      availableBalance: 0,
    );
    await pumpScreen(tester);
    await tester.pumpAndSettle();

    expect(find.text('0.00'), findsWidgets);
  });

  testWidgets('the format helper never turns null into zero', (tester) async {
    expect(PortfolioValue.format(null), PortfolioValue.missing);
    expect(PortfolioValue.format(0), isNot(PortfolioValue.missing));
    expect(PortfolioValue.format(null, signed: true), PortfolioValue.missing);
  });

  // ------------------------------------------------------- G. Options

  testWidgets('Options shows an explicit unsupported state', (tester) async {
    repository.accounts[FakePortfolioRepository.key(
      PortfolioMode.paper,
      PortfolioCategory.options,
    )] = account(
      mode: PortfolioMode.paper,
      category: PortfolioCategory.options,
      availability: PortfolioAvailability.unsupported,
      statusMessage: 'Options is a reserved capability.',
    );
    repository.positions[FakePortfolioRepository.key(
      PortfolioMode.paper,
      PortfolioCategory.options,
    )] = positions(
      mode: PortfolioMode.paper,
      category: PortfolioCategory.options,
      availability: PortfolioAvailability.unsupported,
      statusMessage: 'Options is a reserved capability.',
    );

    await pumpScreen(tester);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('portfolio-category-OPTIONS')));
    await tester.pumpAndSettle();

    expect(find.text('Options not supported'), findsOneWidget);
    expect(find.text('0.00'), findsNothing);
  });

  // ----------------------------------------------- J/K. errors and auth

  testWidgets('a transport failure shows an error state with retry',
      (tester) async {
    repository.failWith = Exception('network down');
    await pumpScreen(tester);
    await tester.pumpAndSettle();

    expect(find.text('Could not load this account'), findsOneWidget);
    expect(find.text('RETRY'), findsOneWidget);
    // An error must never be rendered as an empty or zero account.
    expect(find.text('No positions in this account.'), findsNothing);
  });

  // -------------------------------------------------- L. security

  testWidgets('no credential or listen key is ever displayed', (tester) async {
    seedPaperMain();
    await pumpScreen(tester);
    await tester.pumpAndSettle();

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

  // ------------------------------------- M. stale state across modes

  testWidgets('a response for another scope is never painted', (tester) async {
    // Deliberately return a LIVE payload while PAPER is selected.
    repository.accounts[FakePortfolioRepository.key(
      PortfolioMode.paper,
      PortfolioCategory.main,
    )] = account(
      mode: PortfolioMode.live,
      category: PortfolioCategory.main,
      equity: 424242,
    );
    await pumpScreen(tester);
    await tester.pumpAndSettle();

    expect(
      find.text('424242.00'),
      findsNothing,
      reason: 'a mismatched scope must be discarded, not displayed',
    );
  });

  // --------------------------------------- N. layout and no trade actions

  testWidgets('renders without overflow on a narrow screen', (tester) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    seedPaperMain();
    await pumpScreen(tester);
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.text('PORTFOLIO'), findsOneWidget);
  });

  testWidgets('exposes no trading controls', (tester) async {
    await pumpScreen(tester);
    await tester.pumpAndSettle();

    for (final label in ['BUY', 'SELL', 'CLOSE', 'CANCEL', 'EXECUTE', 'AUTO TRADE']) {
      expect(find.text(label), findsNothing);
    }
  });
}

