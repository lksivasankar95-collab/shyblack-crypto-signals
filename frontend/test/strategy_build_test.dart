import 'package:cryptosignals/core/di/providers.dart';
import 'package:cryptosignals/core/theme/app_theme.dart';
import 'package:cryptosignals/domain/entities/trading_strategy.dart';
import 'package:cryptosignals/domain/repositories/trading_strategy_repository.dart';
import 'package:cryptosignals/presentation/providers/strategy_providers.dart';
import 'package:cryptosignals/presentation/screens/settings/strategy_build_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

/// Fake repository with independently switchable list/active failures.
class _FakeStrategyRepository implements TradingStrategyRepository {
  _FakeStrategyRepository({this.activeFails = false, this.listFails = false});

  final bool activeFails;
  final bool listFails;

  @override
  Future<List<TradingStrategy>> listStrategies(StrategyTradingMode mode) async {
    if (listFails) {
      throw Exception('list failed');
    }
    return [
      TradingStrategy(
        id: 'sys-1',
        name: mode == StrategyTradingMode.futures
            ? 'EMA + RSI Futures v1'
            : 'Trend Pullback',
        tradingMode: mode,
        strategyType: StrategyType.system,
        version: 1,
        status: StrategyStatus.active,
        deletable: false,
        editable: false,
        engineKey: mode == StrategyTradingMode.spot ? 'TREND_PULLBACK' : null,
      ),
    ];
  }

  @override
  Future<ActiveStrategyInfo> getActiveStrategy(StrategyTradingMode mode) async {
    if (activeFails) {
      // Mirrors GET /api/v1/strategies/active?mode=SPOT returning HTTP 500.
      throw Exception('active lookup failed (500)');
    }
    return ActiveStrategyInfo(
      tradingMode: mode,
      strategyId: 'sys-1',
      strategyName: 'Trend Pullback',
      strategyVersion: 1,
      hasActiveStrategy: true,
    );
  }

  @override
  Future<TradingStrategy> createStrategy({
    required String name,
    String? description,
    required StrategyTradingMode tradingMode,
    StrategyConfig? config,
    String? engineKey,
  }) async => throw UnimplementedError();

  @override
  Future<void> deleteStrategy(String id) async {}

  @override
  Future<ActiveStrategyInfo> setActiveStrategy(
    StrategyTradingMode mode,
    String strategyId,
  ) async => throw UnimplementedError();
}

void main() {
  test(
    'strategy list loads even when the active-strategy lookup fails',
    () async {
      final container = ProviderContainer(
        overrides: [
          strategyRepositoryProvider.overrideWith(
            (ref) => _FakeStrategyRepository(activeFails: true),
          ),
        ],
      );
      addTearDown(container.dispose);

      final state = await container.read(spotStrategyTabProvider.future);

      expect(state.strategies, isNotEmpty);
      expect(state.strategies.first.name, 'Trend Pullback');
      // Supplementary active info is simply absent, not fatal.
      expect(state.activeInfo, isNull);
    },
  );

  test('active info is surfaced when the lookup succeeds', () async {
    final container = ProviderContainer(
      overrides: [
        strategyRepositoryProvider.overrideWith(
          (ref) => _FakeStrategyRepository(),
        ),
      ],
    );
    addTearDown(container.dispose);

    final state = await container.read(spotStrategyTabProvider.future);

    expect(state.strategies, isNotEmpty);
    expect(state.activeInfo?.strategyId, 'sys-1');
  });

  test('a genuine list failure still surfaces as an error', () async {
    final container = ProviderContainer(
      overrides: [
        strategyRepositoryProvider.overrideWith(
          (ref) => _FakeStrategyRepository(listFails: true),
        ),
      ],
    );
    addTearDown(container.dispose);

    // Keep the provider alive so its build can settle to an error state.
    final sub = container.listen(spotStrategyTabProvider, (_, _) {});
    addTearDown(sub.close);

    var value = container.read(spotStrategyTabProvider);
    for (var i = 0; i < 100 && value.isLoading; i++) {
      await Future<void>.delayed(const Duration(milliseconds: 10));
      value = container.read(spotStrategyTabProvider);
    }

    expect(value.hasError, isTrue);
  });

  testWidgets('Strategy Build SPOT tab renders strategies when /active 500s', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          strategyRepositoryProvider.overrideWith(
            (ref) => _FakeStrategyRepository(activeFails: true),
          ),
        ],
        child: MaterialApp(
          theme: AppTheme.dark(),
          darkTheme: AppTheme.dark(),
          themeMode: ThemeMode.dark,
          home: const StrategyBuildScreen(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Could not load strategies'), findsNothing);
    expect(find.text('All Strategies'), findsOneWidget);
    expect(find.text('Trend Pullback'), findsOneWidget);
  });
}
