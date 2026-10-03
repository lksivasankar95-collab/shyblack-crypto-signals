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

/// Market sub-tabs, position addressing, and the detail / close / stop-target flow.
///
/// The load-bearing properties:
///  * exactly one market section is mounted at a time;
///  * actions are keyed on the published position id, never a list index;
///  * a destructive action never fires without its confirmation;
///  * a row that cannot be addressed offers no action at all.
void main() {
  late FakePortfolioRepository portfolio;
  late FakeSettingsRepository settings;

  String key(PortfolioMode mode, PortfolioCategory category) =>
      '${mode.apiValue}:${category.apiValue}';

  PortfolioPosition paperPosition({
    String id = 'pos-1',
    String symbol = 'BTCUSDT',
    String side = 'LONG',
    double entry = 100,
    double stop = 90,
    double target = 120,
  }) {
    return PortfolioPosition(
      positionId: id,
      accountMode: PortfolioMode.paper,
      accountCategory: PortfolioCategory.spot,
      symbol: symbol,
      side: side,
      quantity: 2,
      entryPrice: entry,
      currentPrice: 105,
      stopLoss: stop,
      takeProfit1: target,
      notional: 210,
      unrealizedPnl: 10,
      status: 'OPEN',
    );
  }

  Widget harness() => ProviderScope(
    overrides: [
      portfolioRepositoryProvider.overrideWithValue(portfolio),
      settingsRepositoryProvider.overrideWithValue(settings),
    ],
    child: const MaterialApp(home: PortfolioScreen()),
  );

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

  setUp(() {
    portfolio = FakePortfolioRepository();
    settings = FakeSettingsRepository();
  });

  void seedSpotPositions(List<PortfolioPosition> positions) {
    portfolio.positions[key(
      PortfolioMode.paper,
      PortfolioCategory.spot,
    )] = PortfolioPositions(
      accountMode: PortfolioMode.paper,
      accountCategory: PortfolioCategory.spot,
      availability: PortfolioAvailability.available,
      positions: positions,
    );
  }

  // ── Sub-tabs ──────────────────────────────────────────────────

  group('market sub-tabs', () {
    testWidgets('spot offers holdings, open orders and trade history', (
      tester,
    ) async {
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('section-tab-holdings')), findsOneWidget);
      expect(find.byKey(const Key('section-tab-openOrders')), findsOneWidget);
      expect(find.byKey(const Key('section-tab-tradeHistory')), findsOneWidget);
    });

    testWidgets('futures offers positions, open orders and trade history', (
      tester,
    ) async {
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await tester.tap(
        find.byKey(const ValueKey('portfolio-category-FUTURES')),
      );
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('section-tab-positions')), findsOneWidget);
      expect(find.byKey(const Key('section-tab-openOrders')), findsOneWidget);
      expect(find.byKey(const Key('section-tab-tradeHistory')), findsOneWidget);
      // A spot wallet holding is an owned asset, not an open position.
      expect(find.byKey(const Key('section-tab-holdings')), findsNothing);
    });

    testWidgets('only the selected section is mounted', (tester) async {
      seedSpotPositions([paperPosition()]);
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();

      // Holdings is the spot default.
      // PAPER has no resting-order book at all, so no orders header is expected
      // before the tab is opened; what matters is that holdings are mounted.
      expect(find.text('CURRENT HOLDINGS'), findsOneWidget);

      // Moving to Open Orders unmounts the holdings list. PAPER's own orders
      // state is an honest "no order book" panel, so what is asserted is that
      // holdings are gone and orders is the selected section — not that an
      // order list was fabricated.
      await openSection(tester, 'openOrders');
      expect(
        find.text('CURRENT HOLDINGS'),
        findsNothing,
        reason:
            'the holdings section is unmounted once another tab is selected',
      );
      expect(find.byKey(const Key('section-tab-openOrders')), findsOneWidget);

      await openSection(tester, 'tradeHistory');
      expect(find.text('CURRENT HOLDINGS'), findsNothing);
      expect(find.text('HISTORY'), findsOneWidget);
    });

    testWidgets('options keeps its honest state and offers no sections', (
      tester,
    ) async {
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await tester.tap(
        find.byKey(const ValueKey('portfolio-category-OPTIONS')),
      );
      await tester.pumpAndSettle();

      expect(find.textContaining('Options'), findsWidgets);
      expect(find.byKey(const Key('portfolio-section-strip')), findsNothing);
    });

    testWidgets('changing market resets to that market default section', (
      tester,
    ) async {
      seedSpotPositions([paperPosition()]);
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();

      // Move away from the spot default…
      await openSection(tester, 'openOrders');
      expect(find.byKey(const Key('section-tab-openOrders')), findsOneWidget);

      // …then switch market: futures must not stay on the orders section.
      await tester.tap(
        find.byKey(const ValueKey('portfolio-category-FUTURES')),
      );
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('section-tab-positions')), findsOneWidget);
    });
  });

  // ── Detail ────────────────────────────────────────────────────

  group('detail', () {
    testWidgets('tapping a position opens its detail for that position', (
      tester,
    ) async {
      seedSpotPositions([
        paperPosition(id: 'pos-1', symbol: 'BTCUSDT'),
        paperPosition(id: 'pos-2', symbol: 'ETHUSDT', side: 'SHORT'),
      ]);
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('position-card-pos-2')));
      await tester.pumpAndSettle();

      expect(find.text('ETHUSDT'), findsWidgets);
      expect(find.text('SHORT'), findsWidgets);
      expect(find.text('PAPER'), findsWidgets);
      expect(find.text('SPOT'), findsWidgets);
      expect(find.byKey(const Key('detail-update-sltp')), findsOneWidget);
      expect(find.byKey(const Key('detail-close-position')), findsOneWidget);
    });

    testWidgets('detail shows the risk levels already on the row', (
      tester,
    ) async {
      seedSpotPositions([paperPosition(stop: 91, target: 130)]);
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('position-card-pos-1')));
      await tester.pumpAndSettle();

      expect(find.text('Stop Loss'), findsOneWidget);
      expect(find.text('91.00'), findsWidgets);
      expect(find.text('Take Profit'), findsOneWidget);
      expect(find.text('130.00'), findsWidgets);
    });

    testWidgets('a row with no addressable id offers no action', (
      tester,
    ) async {
      // No positionId: the scope has no record the actions could address, so
      // offering them would produce a control that always fails.
      seedSpotPositions([
        PortfolioPosition(
          accountMode: PortfolioMode.paper,
          accountCategory: PortfolioCategory.spot,
          symbol: 'BTCUSDT',
          side: 'LONG',
          quantity: 1,
          entryPrice: 100,
          status: 'OPEN',
        ),
      ]);
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('position-card-BTCUSDT')));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('detail-update-sltp')), findsNothing);
      expect(find.byKey(const Key('detail-close-position')), findsNothing);
      expect(find.text('No actions available'), findsOneWidget);
    });
  });

  // ── Close ─────────────────────────────────────────────────────

  group('close', () {
    Future<void> openCloseDialog(WidgetTester tester, String id) async {
      await tester.tap(find.byKey(Key('position-card-' + id)));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('detail-close-position')));
      await tester.pumpAndSettle();
    }

    testWidgets('never executes from the first tap', (tester) async {
      const id = 'pos-1';
      seedSpotPositions([paperPosition(id: id)]);
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openCloseDialog(tester, id);

      expect(find.text('Close BTCUSDT?'), findsOneWidget);
      expect(
        portfolio.positionCloseCalls,
        isEmpty,
        reason: 'the confirmation must be answered before anything executes',
      );
    });

    testWidgets('the confirmation shows the figures needed to not mis-tap', (
      tester,
    ) async {
      const id = 'pos-1';
      seedSpotPositions([paperPosition(id: id)]);
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openCloseDialog(tester, id);

      // Scoped to the dialog: the detail sheet behind it repeats some labels.
      final inDialog = find.byWidgetPredicate((w) => w is AlertDialog);
      for (final label in [
        'Side',
        'Quantity',
        'Estimated Value',
        'Estimated P&L',
        'PAPER',
      ]) {
        expect(
          find.descendant(of: inDialog, matching: find.text(label)),
          findsOneWidget,
          reason: 'the close confirmation must state $label',
        );
      }
    });

    testWidgets('cancelling closes nothing', (tester) async {
      const id = 'pos-1';
      seedSpotPositions([paperPosition(id: id)]);
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openCloseDialog(tester, id);

      await tester.tap(find.byKey(const Key('close-cancel')));
      await tester.pumpAndSettle();

      expect(portfolio.positionCloseCalls, isEmpty);
    });

    testWidgets('confirming closes the position named by its own id', (
      tester,
    ) async {
      const id = 'pos-abc';
      seedSpotPositions([paperPosition(id: id)]);
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openCloseDialog(tester, id);

      await tester.tap(find.byKey(const Key('close-confirm')));
      await tester.pumpAndSettle();

      expect(
        portfolio.positionCloseCalls,
        ['pos-abc'],
        reason: 'the action must address the record, not the list slot',
      );
    });

    testWidgets('a failed close reports failure and keeps the position', (
      tester,
    ) async {
      const id = 'pos-1';
      seedSpotPositions([paperPosition()]);
      portfolio.paperFailWith = Exception(
        'no live price available for BTCUSDT',
      );
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openCloseDialog(tester, id);

      await tester.tap(find.byKey(const Key('close-confirm')));
      await tester.pumpAndSettle();

      expect(find.textContaining('Close failed'), findsOneWidget);
      expect(
        portfolio.positionCloseCalls,
        ['pos-1'],
        reason: 'the attempt was made; the failure is what is asserted',
      );
    });
  });

  // ── Stop / target ─────────────────────────────────────────────

  group('stop and target', () {
    Future<void> openRisk(WidgetTester tester, String id) async {
      await tester.tap(find.byKey(Key('position-card-' + id)));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('detail-update-sltp')));
      await tester.pumpAndSettle();
    }

    testWidgets('the sheet opens pre-filled with the current levels', (
      tester,
    ) async {
      const id = 'pos-1';
      seedSpotPositions([paperPosition(id: id, stop: 90, target: 120)]);
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openRisk(tester, id);

      expect(find.byKey(const Key('risk-stop')), findsOneWidget);
      expect(find.byKey(const Key('risk-target')), findsOneWidget);
      expect(find.text('90.00'), findsWidgets);
      expect(find.text('120.00'), findsWidgets);
    });

    testWidgets('a long stop at or above the entry is rejected locally', (
      tester,
    ) async {
      const id = 'pos-1';
      seedSpotPositions([paperPosition(id: id, entry: 100)]);
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openRisk(tester, id);

      await tester.enterText(find.byKey(const Key('risk-stop')), '105');
      await tester.tap(find.byKey(const Key('risk-save')));
      await tester.pumpAndSettle();

      expect(find.text('A LONG stop must be below the entry'), findsOneWidget);
      expect(portfolio.positionRiskCalls, isEmpty);
    });

    testWidgets('a long target at or below the entry is rejected locally', (
      tester,
    ) async {
      const id = 'pos-1';
      seedSpotPositions([paperPosition(id: id, entry: 100)]);
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openRisk(tester, id);

      await tester.enterText(find.byKey(const Key('risk-target')), '95');
      await tester.tap(find.byKey(const Key('risk-save')));
      await tester.pumpAndSettle();

      expect(
        find.text('A LONG target must be above the entry'),
        findsOneWidget,
      );
      expect(portfolio.positionRiskCalls, isEmpty);
    });

    testWidgets('a short stop below the entry is rejected locally', (
      tester,
    ) async {
      const id = 'pos-1';
      seedSpotPositions([paperPosition(id: id, side: 'SHORT', entry: 100)]);
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openRisk(tester, id);

      await tester.enterText(find.byKey(const Key('risk-stop')), '95');
      await tester.tap(find.byKey(const Key('risk-save')));
      await tester.pumpAndSettle();

      expect(find.text('A SHORT stop must be above the entry'), findsOneWidget);
    });

    testWidgets('a non-numeric level is rejected', (tester) async {
      const id = 'pos-1';
      seedSpotPositions([paperPosition()]);
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openRisk(tester, id);

      await tester.enterText(find.byKey(const Key('risk-stop')), 'abc');
      await tester.tap(find.byKey(const Key('risk-save')));
      await tester.pumpAndSettle();

      expect(find.text('Enter a valid number'), findsOneWidget);
      expect(portfolio.positionRiskCalls, isEmpty);
    });

    testWidgets('a valid update is sent against the row position id', (
      tester,
    ) async {
      const id = 'pos-xyz';
      seedSpotPositions([paperPosition(id: id)]);
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openRisk(tester, id);

      await tester.enterText(find.byKey(const Key('risk-stop')), '95');
      await tester.tap(find.byKey(const Key('risk-save')));
      await tester.pumpAndSettle();

      expect(portfolio.positionRiskCalls, ['pos-xyz:95.0:120.0']);
    });

    testWidgets('cancelling sends nothing', (tester) async {
      const id = 'pos-1';
      seedSpotPositions([paperPosition(id: id)]);
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openRisk(tester, id);

      await tester.enterText(find.byKey(const Key('risk-stop')), '95');
      await tester.tap(find.byKey(const Key('risk-cancel')));
      await tester.pumpAndSettle();

      expect(portfolio.positionRiskCalls, isEmpty);
    });

    testWidgets('a server rejection surfaces instead of reporting success', (
      tester,
    ) async {
      const id = 'pos-1';
      seedSpotPositions([paperPosition(id: id)]);
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openRisk(tester, id);

      portfolio.paperFailWith = Exception('Position is not open');
      await tester.enterText(find.byKey(const Key('risk-stop')), '95');
      await tester.tap(find.byKey(const Key('risk-save')));
      await tester.pumpAndSettle();

      expect(portfolio.positionRiskCalls, isNotEmpty);
      expect(find.textContaining('Could not update'), findsOneWidget);
    });
  });

  // ── PAPER / LIVE separation ───────────────────────────────────

  group('account separation', () {
    testWidgets('paper position actions are refused on a live account', (
      tester,
    ) async {
      settings.setAccount(TradingAccount.live);
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();

      final container = ProviderScope.containerOf(
        tester.element(find.byType(PortfolioScreen)),
      );
      final actions = container.read(
        portfolioPositionActionControllerProvider.notifier,
      );

      await expectLater(
        actions.close('pos-1'),
        throwsA(isA<PortfolioCapitalNotAvailable>()),
      );
      await expectLater(
        actions.updateRisk('pos-1', stopLoss: 95),
        throwsA(isA<PortfolioCapitalNotAvailable>()),
      );
      expect(portfolio.positionCloseCalls, isEmpty);
      expect(portfolio.positionRiskCalls, isEmpty);
    });
  });
}
