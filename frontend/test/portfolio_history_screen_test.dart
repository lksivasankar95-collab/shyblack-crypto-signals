import 'package:cryptosignals/core/di/providers.dart';
import 'package:cryptosignals/domain/entities/app_settings.dart';
import 'package:cryptosignals/domain/entities/portfolio_account.dart';
import 'package:cryptosignals/presentation/screens/portfolio/portfolio_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'fake_settings_repository.dart';
import 'portfolio_fake_repository.dart';

/// Holdings, history-window and freshness behaviour of the Binance-style Portfolio.
///
/// These cover the claims the screen makes to the user: wallet assets are labelled as
/// assets rather than positions, a partial history window is admitted, a stale scope is
/// labelled stale, and no scope's records appear under another scope's heading.
///
/// The account mode is driven from Settings, so every test states which account it is
/// looking at instead of tapping a Portfolio control.
void main() {
  late FakePortfolioRepository repository;

  PortfolioHoldings liveSpotHoldings() => PortfolioHoldings(
        accountMode: PortfolioMode.live,
        accountCategory: PortfolioCategory.spot,
        availability: PortfolioAvailability.available,
        source: 'EXCHANGE',
        holdings: const [
          PortfolioHolding(
            asset: 'BTC',
            free: 0.1,
            locked: 0.02,
            total: 0.12,
          ),
        ],
      );

  PortfolioHistory ordersFor(PortfolioMode mode, PortfolioCategory category) =>
      PortfolioHistory(
        accountMode: mode,
        accountCategory: category,
        availability: PortfolioAvailability.available,
        source: mode == PortfolioMode.paper ? 'LOCAL_PAPER' : 'EXCHANGE',
        entryType: 'ORDER',
        windowFrom: DateTime.utc(2026, 1, 1),
        windowTo: DateTime.utc(2026, 1, 8),
        entries: [
          PortfolioHistoryEntry(
            entryType: 'ORDER',
            accountMode: mode,
            accountCategory: category,
            symbol: 'BTCUSDT',
            orderId: 42,
            side: 'BUY',
            status: 'FILLED',
            price: 100,
            quantity: 2,
            occurredAt: DateTime.utc(2026, 1, 2),
          ),
        ],
      );

  Future<void> pumpScreen(
    WidgetTester tester, {
    TradingAccount account = TradingAccount.paper,
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
  }

  /// Selects a market-account tab by its widget key rather than by label text, because
  /// the account badge and the order rows also contain those words.
  Future<void> selectCategory(WidgetTester tester, PortfolioCategory category) async {
    await tester.tap(find.byKey(ValueKey('portfolio-category-${category.apiValue}')));
    await tester.pumpAndSettle();
  }

  /// The sections below the fold are not built until scrolled into view, which is
  /// correct lazy-list behaviour. This drags the list in steps until the target appears,
  /// rather than asserting against an unbuilt widget.
  ///
  /// It stops as soon as the target is found and gives up after a bounded number of
  /// steps, so a genuinely missing widget produces a clear assertion failure instead of
  /// a hang.
  Future<void> scrollTo(WidgetTester tester, Finder target) async {
    for (var step = 0; step < 12; step++) {
      if (target.evaluate().isNotEmpty) {
        await tester.pumpAndSettle();
        return;
      }
      await tester.drag(
        find.byType(ListView).first,
        const Offset(0, -180),
      );
      await tester.pumpAndSettle();
    }
  }

  setUp(() {
    repository = FakePortfolioRepository(
      holdings: {
        FakePortfolioRepository.key(PortfolioMode.live, PortfolioCategory.spot):
            liveSpotHoldings(),
      },
      history: {
        for (final category in PortfolioCategory.values) ...{
          FakePortfolioRepository.key(PortfolioMode.live, category):
              ordersFor(PortfolioMode.live, category),
          FakePortfolioRepository.key(PortfolioMode.paper, category):
              ordersFor(PortfolioMode.paper, category),
        },
      },
      syncStatus: {
        FakePortfolioRepository.key(PortfolioMode.live, PortfolioCategory.spot):
            PortfolioSyncStatus(
          accountMode: PortfolioMode.live,
          accountCategory: PortfolioCategory.spot,
          availability: PortfolioAvailability.stale,
          connectionStatus: 'CONNECTED',
          lastRestSync: DateTime.utc(2026, 1, 2, 3, 4, 5),
          stale: true,
        ),
      },
    );
  });

  testWidgets('wallet assets are shown as assets, not positions', (tester) async {
    await pumpScreen(tester, account: TradingAccount.live);

    await scrollTo(tester, find.text('WALLET / ASSETS'));

    expect(find.text('WALLET / ASSETS'), findsOneWidget);
    expect(find.text('BTC'), findsOneWidget);
    // PortfolioKpi upper-cases its label, so the rendered text is uppercase.
    expect(find.text('AVAILABLE'), findsWidgets);
    expect(find.text('LOCKED'), findsOneWidget);
    expect(find.text('TOTAL'), findsWidgets);
    // Spot must never be given a leveraged position section.
    expect(find.text('OPEN POSITIONS'), findsNothing);
  });

  testWidgets('a paper account shows no wallet-assets capability', (tester) async {
    await pumpScreen(tester);

    await scrollTo(tester, find.text('WALLET / ASSETS'));

    expect(find.text('WALLET / ASSETS'), findsOneWidget);
    // A simulated account holds capital, not exchange assets, and that must be stated
    // rather than shown as an empty wallet.
    expect(find.text('No wallet assets'), findsOneWidget);
  });

  testWidgets('a stale scope is labelled stale', (tester) async {
    await pumpScreen(tester, account: TradingAccount.live);
    await scrollTo(tester, find.text('SYNC STATUS'));

    expect(find.text('SYNC STATUS'), findsOneWidget);
    expect(find.text('Data is stale'), findsOneWidget);
    expect(find.textContaining('must not be read as current'), findsOneWidget);
  });

  testWidgets('a partial history window is admitted, not presented as complete',
      (tester) async {
    repository.history[FakePortfolioRepository.key(
      PortfolioMode.live,
      PortfolioCategory.spot,
    )] = PortfolioHistory(
      accountMode: PortfolioMode.live,
      accountCategory: PortfolioCategory.spot,
      availability: PortfolioAvailability.available,
      source: 'EXCHANGE',
      entryType: 'ORDER',
      windowFrom: DateTime.utc(2026, 1, 1),
      windowTo: DateTime.utc(2026, 1, 8),
      complete: false,
      statusMessage: 'Reached the record cap of 200; this window is partial.',
      entries: const [],
    );

    await pumpScreen(tester, account: TradingAccount.live);
    await scrollTo(tester, find.text('HISTORY'));

    expect(find.text('Partial window'), findsOneWidget);
    expect(find.textContaining('this window is partial'), findsOneWidget);
  });

  testWidgets('switching record type requests the matching type', (tester) async {
    await pumpScreen(tester, account: TradingAccount.live);

    expect(repository.historyCalls, contains('LIVE:SPOT:ORDER:-'));

    await scrollTo(tester, find.text('TRADE'));
    await tester.tap(find.text('TRADE').first);
    await tester.pumpAndSettle();

    expect(repository.historyCalls, contains('LIVE:SPOT:TRADE:-'));
  });

  testWidgets('a spot symbol is requested explicitly once entered', (tester) async {
    await pumpScreen(tester, account: TradingAccount.live);
    await scrollTo(tester, find.byType(TextField));

    await tester.enterText(find.byType(TextField).first, 'ethusdt');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();

    expect(
      repository.historyCalls,
      contains('LIVE:SPOT:ORDER:ETHUSDT'),
      reason: 'spot history is per symbol, so the symbol must reach the API',
    );
  });

  testWidgets('a HISTORY failure does not blank the account figures',
      (tester) async {
    await pumpScreen(tester, account: TradingAccount.live);

    await scrollTo(tester, find.text('WALLET / ASSETS'));
    // The figures are on screen before the history request fails.
    expect(find.text('BTC'), findsOneWidget);

    // Only history fails: the holdings and sync-status reads must be unaffected, which
    // is the whole point of them being separate providers.
    repository.historyFailWith = Exception('exchange unreachable');
    await pumpScreen(tester, account: TradingAccount.live);

    // The account figures survive a history failure, because history is a separate
    // provider from the account and holdings reads.
    //
    // The assertion is deliberately about what survives rather than about the exact
    // error widget: Riverpod automatically retries a failed provider, so the error
    // state is transient by design and asserting it would make this test depend on
    // retry timing rather than on isolation.
    await scrollTo(tester, find.text('WALLET / ASSETS'));
    expect(find.text('BTC'), findsOneWidget);
  });

  testWidgets('a history response for another scope is never painted',
      (tester) async {
    // The payload served for the LIVE SPOT request declares FUTURES. The screen must
    // refuse to paint it, which is the guard against records appearing under the wrong
    // scope's heading.
    repository.history[FakePortfolioRepository.key(
      PortfolioMode.live,
      PortfolioCategory.spot,
    )] = ordersFor(PortfolioMode.live, PortfolioCategory.futures);

    await pumpScreen(tester, account: TradingAccount.live);
    await scrollTo(tester, find.text('HISTORY'));

    expect(find.text('order 42'), findsNothing);
  });

  testWidgets('the history section exposes no trading control', (tester) async {
    await pumpScreen(tester, account: TradingAccount.live);
    await scrollTo(tester, find.text('HISTORY'));

    // A record may legitimately display BUY or SELL as its side, so the check is for
    // actual actionable affordances rather than for those words. A read-only screen
    // offers no action of any kind.
    for (final label in ['CLOSE', 'CANCEL', 'PLACE ORDER', 'BUY NOW', 'SELL NOW']) {
      expect(
        find.text(label),
        findsNothing,
        reason: 'the Portfolio is read-only, so $label must not be offered',
      );
    }
    expect(
      find.byType(FloatingActionButton),
      findsNothing,
      reason: 'a floating action button would imply an action exists',
    );
  });

  testWidgets('renders every section without overflow on a narrow screen',
      (tester) async {
    tester.view.physicalSize = const Size(360, 640);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await pumpScreen(tester, account: TradingAccount.live);
    // The futures tab is the densest layout, so it is the one worth checking.
    await selectCategory(tester, PortfolioCategory.futures);
    await scrollTo(tester, find.text('HISTORY'));

    expect(tester.takeException(), isNull);
    expect(find.text('OPEN POSITIONS'), findsOneWidget);
    expect(find.text('HISTORY'), findsOneWidget);
  });
}