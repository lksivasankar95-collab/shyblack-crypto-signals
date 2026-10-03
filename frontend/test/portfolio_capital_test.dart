import 'package:cryptosignals/core/di/providers.dart';
import 'package:cryptosignals/domain/entities/app_settings.dart';
import 'package:cryptosignals/domain/repositories/portfolio_repository.dart';
import 'package:cryptosignals/presentation/providers/portfolio_controller.dart';
import 'package:cryptosignals/presentation/providers/settings_controller.dart';
import 'package:cryptosignals/presentation/screens/portfolio/portfolio_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'fake_settings_repository.dart';
import 'portfolio_fake_repository.dart';

/// Paper-account capital management reachable from the Portfolio screen.
///
/// The load-bearing property is negative: these controls move simulated funds,
/// so they must be entirely absent on a live screen, and the mutation must never
/// be issued for a live account even if the guard were bypassed.
void main() {
  late FakePortfolioRepository portfolio;
  late FakeSettingsRepository settings;

  Widget harness() {
    return ProviderScope(
      overrides: [
        portfolioRepositoryProvider.overrideWithValue(portfolio),
        settingsRepositoryProvider.overrideWithValue(settings),
      ],
      child: const MaterialApp(home: PortfolioScreen()),
    );
  }

  setUp(() {
    portfolio = FakePortfolioRepository();
    settings = FakeSettingsRepository();
  });

  /// Only simulated-funds *mutations*. Opening the sheet legitimately reads the
  /// ledger, so a raw call list would not distinguish "validated and refused"
  /// from "never attempted".
  List<String> mutations() => portfolio.capitalCalls
      .where(
        (c) => c.startsWith('ADD:') || c.startsWith('REDUCE:') || c == 'RESET',
      )
      .toList();

  group('management icon', () {
    testWidgets('is offered while Settings resolves the account as PAPER', (
      tester,
    ) async {
      settings.setAccount(TradingAccount.paper);
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('paper-capital-management')), findsOneWidget);
    });

    testWidgets('is absent entirely on a LIVE screen', (tester) async {
      settings.setAccount(TradingAccount.live);
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();

      expect(
        find.byKey(const Key('paper-capital-management')),
        findsNothing,
        reason: 'simulated-funds controls must not exist on a live account',
      );
    });

    testWidgets('does not appear before the account mode resolves', (
      tester,
    ) async {
      settings.failLoad = Exception('offline');
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('paper-capital-management')), findsNothing);
    });

    testWidgets('still offers no PAPER/LIVE switch of its own', (tester) async {
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();

      // The account mode lives in Settings only. Nothing on this screen may
      // offer a second, divergent control for it.
      expect(find.textContaining('PAPER / LIVE'), findsNothing);
      expect(find.byKey(const Key('paper-capital-management')), findsOneWidget);
    });
  });

  group('capital sheet', () {
    Future<void> openSheet(WidgetTester tester) async {
      await tester.tap(find.byKey(const Key('paper-capital-management')));
      await tester.pumpAndSettle();
    }

    testWidgets('offers add, reduce, reset and capital history', (
      tester,
    ) async {
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openSheet(tester);

      expect(find.text('Paper Account'), findsOneWidget);
      expect(find.byKey(const Key('capital-add')), findsOneWidget);
      expect(find.byKey(const Key('capital-reduce')), findsOneWidget);
      expect(find.byKey(const Key('capital-reset')), findsOneWidget);
      expect(find.text('CAPITAL HISTORY'), findsOneWidget);
    });

    testWidgets('states that it cannot affect a live account', (tester) async {
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openSheet(tester);

      expect(
        find.textContaining('cannot affect a live account'),
        findsOneWidget,
      );
    });

    testWidgets('shows an empty capital history without inventing a balance', (
      tester,
    ) async {
      portfolio.capitalHistoryRows = const [];
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openSheet(tester);

      expect(find.text('No capital movements yet.'), findsOneWidget);
    });

    testWidgets('lists audited movements with previous and new balance', (
      tester,
    ) async {
      portfolio.capitalHistoryRows = const [
        PaperCapitalEvent(
          id: 'e1',
          type: PaperCapitalEventType.add,
          amount: 500,
          previousBalance: 100,
          newBalance: 600,
          reason: 'deposit',
        ),
        PaperCapitalEvent(
          id: 'e2',
          type: PaperCapitalEventType.reduce,
          amount: -100,
          previousBalance: 600,
          newBalance: 500,
        ),
      ];
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openSheet(tester);

      expect(find.text('ADD'), findsOneWidget);
      expect(find.text('REDUCE'), findsOneWidget);
      expect(find.text('+500.00'), findsOneWidget);
      expect(find.text('-100.00'), findsOneWidget);
      expect(find.text('100.00  ->  600.00'), findsOneWidget);
      expect(find.text('deposit'), findsOneWidget);
    });

    testWidgets('a failed history read is an error, not an empty history', (
      tester,
    ) async {
      portfolio.paperFailWith = Exception('boom');
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openSheet(tester);

      expect(find.textContaining('could not be loaded'), findsOneWidget);
      expect(find.text('No capital movements yet.'), findsNothing);
    });
  });

  group('add capital', () {
    Future<void> openAdd(WidgetTester tester) async {
      await tester.tap(find.byKey(const Key('paper-capital-management')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('capital-add')));
      await tester.pumpAndSettle();
    }

    testWidgets('confirms and sends the amount and reason', (tester) async {
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openAdd(tester);

      await tester.enterText(find.byKey(const Key('capital-amount')), '250');
      await tester.enterText(find.byKey(const Key('capital-reason')), 'top up');
      await tester.tap(find.byKey(const Key('capital-confirm-ADD')));
      await tester.pumpAndSettle();

      expect(mutations(), contains('ADD:250.0:top up'));
    });

    testWidgets('offers quick amounts', (tester) async {
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openAdd(tester);

      expect(find.text('+100'), findsOneWidget);
      expect(find.text('+1000'), findsOneWidget);
      expect(find.text('+5000'), findsOneWidget);
    });

    testWidgets('rejects a non-numeric amount without calling the API', (
      tester,
    ) async {
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openAdd(tester);

      await tester.enterText(find.byKey(const Key('capital-amount')), 'abc');
      await tester.tap(find.byKey(const Key('capital-confirm-ADD')));
      await tester.pumpAndSettle();

      expect(find.text('Enter a valid number'), findsOneWidget);
      expect(mutations(), isEmpty);
    });

    testWidgets('rejects zero and negative amounts', (tester) async {
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openAdd(tester);

      for (final bad in ['0', '-5']) {
        await tester.enterText(find.byKey(const Key('capital-amount')), bad);
        await tester.tap(find.byKey(const Key('capital-confirm-ADD')));
        await tester.pumpAndSettle();
        expect(find.text('Amount must be greater than zero'), findsOneWidget);
      }
      expect(mutations(), isEmpty);
    });

    testWidgets('cancelling sends nothing', (tester) async {
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openAdd(tester);

      await tester.enterText(find.byKey(const Key('capital-amount')), '100');
      await tester.tap(find.text('CANCEL'));
      await tester.pumpAndSettle();

      expect(mutations(), isEmpty);
    });
  });

  group('reduce capital', () {
    Future<void> openReduce(WidgetTester tester) async {
      await tester.tap(find.byKey(const Key('paper-capital-management')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('capital-reduce')));
      await tester.pumpAndSettle();
    }

    testWidgets('sends the withdrawal', (tester) async {
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openReduce(tester);

      await tester.enterText(find.byKey(const Key('capital-amount')), '40');
      await tester.tap(find.byKey(const Key('capital-confirm-REDUCE')));
      await tester.pumpAndSettle();

      expect(mutations(), contains('REDUCE:40.0:'));
    });

    testWidgets('offers no quick top-up amounts', (tester) async {
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openReduce(tester);

      expect(find.text('+1000'), findsNothing);
    });

    testWidgets('surfaces a server rejection instead of a zero balance', (
      tester,
    ) async {
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openReduce(tester);

      // Fail only the mutation, so the assertion is about the rejection being
      // shown rather than about a collapsed history read.
      portfolio.paperFailWith = Exception('only 10 is available');
      await tester.enterText(find.byKey(const Key('capital-amount')), '999');
      await tester.tap(find.byKey(const Key('capital-confirm-REDUCE')));
      await tester.pumpAndSettle();

      expect(mutations(), contains('REDUCE:999.0:'));
      // Keyed rather than matched by text: the same message also surfaces on the
      // history panel, and this assertion is about the mutation error itself.
      expect(find.byKey(const Key('capital-action-error')), findsOneWidget);
      expect(
        find.text('No capital movements yet.'),
        findsNothing,
        reason: 'a rejected withdrawal must never render as a settled balance',
      );
    });
  });

  group('reset', () {
    Future<void> openReset(WidgetTester tester) async {
      await tester.tap(find.byKey(const Key('paper-capital-management')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('capital-reset')));
      await tester.pumpAndSettle();
    }

    testWidgets('requires an explicit confirmation', (tester) async {
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openReset(tester);

      expect(find.text('Reset paper account?'), findsOneWidget);
      expect(find.textContaining('cannot be undone'), findsOneWidget);
      expect(mutations(), isNot(contains('RESET')));
    });

    testWidgets('warns that it cannot affect a live account', (tester) async {
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openReset(tester);

      expect(find.textContaining('cannot affect a live account'), findsWidgets);
    });

    testWidgets('cancelling performs no reset', (tester) async {
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openReset(tester);

      await tester.tap(find.text('CANCEL'));
      await tester.pumpAndSettle();

      expect(mutations(), isNot(contains('RESET')));
    });

    testWidgets('confirming performs the reset', (tester) async {
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      await openReset(tester);

      await tester.tap(find.byKey(const Key('capital-reset-confirm')));
      await tester.pumpAndSettle();

      expect(mutations(), contains('RESET'));
    });
  });

  group('mode isolation', () {
    testWidgets(
      'switching Settings to LIVE removes the control and its effects',
      (tester) async {
        await tester.pumpWidget(harness());
        await tester.pumpAndSettle();
        expect(
          find.byKey(const Key('paper-capital-management')),
          findsOneWidget,
        );

        // The user changes the mode in Settings, which is the only writer. The
        // Portfolio follows it; nothing here can set the mode itself.
        settings.setAccount(TradingAccount.live);
        final container = ProviderScope.containerOf(
          tester.element(find.byType(PortfolioScreen)),
        );
        container.invalidate(settingsControllerProvider);
        await tester.pumpAndSettle();

        expect(find.byKey(const Key('paper-capital-management')), findsNothing);
        expect(mutations(), isEmpty);
      },
    );

    testWidgets('no simulated-funds mutation is ever issued for a live account', (
      tester,
    ) async {
      settings.setAccount(TradingAccount.live);
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();

      // The control is absent, and the guarded providers refuse as well, so there
      // is no second path from a live screen to a simulated balance change.
      final container = ProviderScope.containerOf(
        tester.element(find.byType(PortfolioScreen)),
      );
      final capital = container.read(
        portfolioCapitalControllerProvider.notifier,
      );

      await expectLater(
        capital.add(100),
        throwsA(isA<PortfolioCapitalNotAvailable>()),
      );
      await expectLater(
        capital.reduce(100),
        throwsA(isA<PortfolioCapitalNotAvailable>()),
      );
      await expectLater(
        capital.reset(),
        throwsA(isA<PortfolioCapitalNotAvailable>()),
      );
      expect(mutations(), isEmpty);
    });
  });
}
