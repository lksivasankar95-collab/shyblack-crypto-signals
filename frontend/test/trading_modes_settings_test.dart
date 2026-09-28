import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:cryptosignals/core/di/providers.dart';
import 'package:cryptosignals/core/theme/app_theme.dart';
import 'package:cryptosignals/data/models/app_settings_model.dart';
import 'package:cryptosignals/domain/entities/app_settings.dart';
import 'package:cryptosignals/domain/repositories/settings_repository.dart';
import 'package:cryptosignals/presentation/screens/settings/settings_screen.dart';

void main() {
  group('AppSettingsModel multi-mode parsing', () {
    test('parses selectedTradingModes list', () {
      final model = AppSettingsModel.fromBackend(
        {
          'tradingMode': 'SPOT',
          'selectedTradingModes': ['SPOT', 'FUTURES'],
          'accountType': 'PAPER',
        },
        {},
      );
      expect(
        model.settings.selectedTradingModes,
        [TradingMode.spot, TradingMode.futures],
      );
    });

    test('backward compatible: legacy single mode becomes a one-element list', () {
      final model = AppSettingsModel.fromBackend(
        {'tradingMode': 'FUTURES', 'accountType': 'PAPER'},
        {},
      );
      expect(model.settings.selectedTradingModes, [TradingMode.futures]);
    });

    test('deduplicates and drops unknown modes', () {
      final model = AppSettingsModel.fromBackend(
        {
          'tradingMode': 'SPOT',
          'selectedTradingModes': ['SPOT', 'SPOT', 'BOGUS'],
          'accountType': 'PAPER',
        },
        {},
      );
      expect(model.settings.selectedTradingModes, [TradingMode.spot]);
    });

    test('patch body carries selectedTradingModes', () {
      const settings = AppSettings(
        fullName: '',
        email: '',
        phone: '',
        country: '',
        timezone: 'UTC',
        memberId: '',
        memberSince: '',
        membershipTier: 'Standard',
        tradingMode: TradingMode.spot,
        selectedTradingModes: [TradingMode.spot, TradingMode.futures],
        tradingAccount: TradingAccount.paper,
        quoteCurrency: 'USDT',
        riskProfile: RiskProfile.moderate,
        positionSizingMode: PositionSizingMode.fixedPercent,
        defaultLeverageView: '1x',
        themeName: 'dark',
        language: 'English',
      );
      final patch = const AppSettingsModel(settings).toSettingsPatch();
      expect(patch['selectedTradingModes'], ['SPOT', 'FUTURES']);
    });
  });

  group('Settings screen multi-select', () {
    late _RecordingSettingsRepository repo;

    Future<void> pump(WidgetTester tester) async {
      SharedPreferences.setMockInitialValues({});
      repo = _RecordingSettingsRepository();
      final theme = AppTheme.dark();
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            settingsRepositoryProvider.overrideWith((ref) => repo),
          ],
          child: MaterialApp(
            theme: theme,
            darkTheme: theme,
            themeMode: ThemeMode.dark,
            home: const SettingsScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();
    }

    testWidgets('selecting a second mode persists both modes', (tester) async {
      await pump(tester);
      expect(repo.lastSaved, isNull);

      await tester.tap(find.text('Futures'));
      await tester.pumpAndSettle();

      expect(repo.lastSaved, isNotNull);
      expect(
        repo.lastSaved!.selectedTradingModes,
        [TradingMode.spot, TradingMode.futures],
      );
    });

    testWidgets('deselecting one mode keeps at least one selected',
        (tester) async {
      await pump(tester);

      // Add Futures, then remove Spot -> Futures remains.
      await tester.tap(find.text('Futures'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Spot'));
      await tester.pumpAndSettle();

      expect(repo.lastSaved!.selectedTradingModes, [TradingMode.futures]);

      // Removing the last remaining mode is refused (snackbar, no save).
      final savesBefore = repo.saveCount;
      await tester.tap(find.text('Futures'));
      await tester.pumpAndSettle();
      expect(repo.saveCount, savesBefore);
      expect(find.text('Select at least one trading mode'), findsOneWidget);
    });
  });
}

class _RecordingSettingsRepository implements SettingsRepository {
  AppSettings _settings = AppSettings.defaults;
  AppSettings? lastSaved;
  int saveCount = 0;

  @override
  Future<AppSettings> load() async => _settings;

  @override
  Future<void> save(AppSettings settings) async {
    _settings = settings;
    lastSaved = settings;
    saveCount++;
  }

  @override
  Future<List<Map<String, dynamic>>> listExchanges() async => [];

  @override
  Future<Map<String, dynamic>> connectExchange(Map<String, dynamic> body) async => {};

  @override
  Future<Map<String, dynamic>> testExchangeConnection(String id) async => {'ok': true};

  @override
  Future<void> deleteExchange(String id) async {}

  @override
  Future<Map<String, dynamic>> getNotificationPrefs() async => {};

  @override
  Future<Map<String, dynamic>> updateNotificationPrefs(Map<String, dynamic> body) async => {};

  @override
  Future<List<Map<String, dynamic>>> listDeviceTokens() async => [];
}
