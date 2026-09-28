import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:cryptosignals/core/di/providers.dart';
import 'package:cryptosignals/core/theme/app_theme.dart';
import 'package:cryptosignals/domain/entities/app_settings.dart';
import 'package:cryptosignals/domain/repositories/settings_repository.dart';
import 'package:cryptosignals/presentation/screens/settings/about_screen.dart';
import 'package:cryptosignals/presentation/screens/settings/exchange_accounts_screen.dart';
import 'package:cryptosignals/presentation/screens/settings/profile_screen.dart';
import 'package:cryptosignals/presentation/screens/settings/security_screen.dart';
import 'package:cryptosignals/presentation/screens/settings/settings_screen.dart';
import 'package:cryptosignals/presentation/screens/settings/subscription_screen.dart';

/// Settings module back-navigation contract: every child screen opens on the
/// existing Navigator stack and must expose a working back affordance that
/// returns to the exact previous Settings screen.
///
/// Uses fixed-duration pumps (not pumpAndSettle) because some child screens
/// legitimately keep an async provider pending, which would never settle.
void main() {
  Future<void> pumpTo(WidgetTester tester) async {
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pump(const Duration(milliseconds: 400));
  }

  Future<void> pumpSettings(WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({});
    // Tall viewport so every Settings nav tile is visible (no scrolling).
    tester.view.physicalSize = const Size(1000, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final theme = AppTheme.dark();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          settingsRepositoryProvider.overrideWith((ref) => _FakeSettingsRepository()),
        ],
        child: MaterialApp(
          theme: theme,
          darkTheme: theme,
          themeMode: ThemeMode.dark,
          home: const SettingsScreen(),
        ),
      ),
    );
    await pumpTo(tester);
  }

  Future<void> tapNav(WidgetTester tester, String label) async {
    await tester.tap(find.text(label));
    await pumpTo(tester);
  }

  Future<void> tapBack(WidgetTester tester) async {
    // `.last` targets the top-most route's app bar when routes are stacked.
    await tester.tap(find.byType(BackButton).last);
    await pumpTo(tester);
  }

  testWidgets('settings root has no back button (root/tab destination)',
      (tester) async {
    await pumpSettings(tester);
    expect(find.text('App settings'), findsOneWidget);
    expect(find.byType(BackButton), findsNothing);
  });

  final children = <String, Finder>{
    'Profile': find.byType(ProfileScreen),
    'Subscription': find.byType(SubscriptionScreen),
    'Security': find.byType(SecurityScreen),
    'Connect Exchange Accounts': find.byType(ExchangeAccountsScreen),
  };

  children.forEach((label, childType) {
    testWidgets('$label opens and back returns to Settings', (tester) async {
      await pumpSettings(tester);
      await tapNav(tester, label);

      expect(childType, findsOneWidget);
      expect(find.byType(BackButton), findsOneWidget);

      await tapBack(tester);

      expect(find.text('App settings'), findsOneWidget);
      expect(childType, findsNothing);
    });
  });

  testWidgets(
      'nested Settings -> About -> policy back returns to About (not Settings)',
      (tester) async {
    await pumpSettings(tester);
    await tapNav(tester, 'About Us');
    expect(find.byType(AboutScreen), findsOneWidget);

    // Open the nested policy route.
    await tester.tap(find.text('Terms of Service'));
    await pumpTo(tester);

    // Back returns to About (not straight to Settings).
    await tapBack(tester);
    expect(find.byType(AboutScreen), findsOneWidget);
    expect(find.text('App settings'), findsNothing);

    // Back again returns to Settings root.
    await tapBack(tester);
    expect(find.text('App settings'), findsOneWidget);
    expect(find.byType(AboutScreen), findsNothing);
  });
}

class _FakeSettingsRepository implements SettingsRepository {
  AppSettings _settings = AppSettings.defaults;

  @override
  Future<AppSettings> load() async => _settings;

  @override
  Future<void> save(AppSettings settings) async => _settings = settings;

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
