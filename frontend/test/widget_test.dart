import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:cryptosignals/core/constants/app_constants.dart';
import 'package:cryptosignals/core/theme/app_theme.dart';
import 'package:cryptosignals/main.dart';
import 'package:cryptosignals/presentation/screens/auth/login_screen.dart';
import 'package:cryptosignals/presentation/screens/settings/settings_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  testWidgets('splash shows branding then opens login', (WidgetTester tester) async {
    await tester.pumpWidget(const ProviderScope(child: ShyBlackApp()));

    expect(find.text(AppConstants.appName), findsOneWidget);
    expect(find.text(AppConstants.tagline), findsOneWidget);

    await tester.pump(const Duration(seconds: 2));
    await tester.pumpAndSettle();

    expect(find.text('LOGIN'), findsOneWidget);
    expect(find.text('Continue with Google'), findsOneWidget);
    expect(find.text('Welcome Back!'), findsOneWidget);
  });

  testWidgets('login fits iPhone SE height without scrolling', (WidgetTester tester) async {
    tester.view.physicalSize = const Size(375, 667);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final theme = AppTheme.dark();
    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(
          theme: theme,
          darkTheme: theme,
          themeMode: ThemeMode.dark,
          home: const LoginScreen(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.text('Welcome Back!'), findsOneWidget);
    expect(find.text('LOGIN'), findsOneWidget);
    expect(find.text('Continue with Google'), findsOneWidget);

    final scrollable = tester.state<ScrollableState>(find.byType(Scrollable).first);
    expect(
      scrollable.position.maxScrollExtent,
      0,
      reason: 'Login should fit on a 375x667 screen without scrolling',
    );
  });

  testWidgets('settings shows profile and trading mode options', (WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({});
    final theme = AppTheme.dark();
    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(
          theme: theme,
          darkTheme: theme,
          themeMode: ThemeMode.dark,
          home: const SettingsScreen(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Ada Trader'), findsOneWidget);
    expect(find.text('Spot'), findsOneWidget);
    expect(find.text('Futures'), findsOneWidget);
    expect(find.text('Options'), findsOneWidget);
    expect(find.text('Paper Trading Account'), findsOneWidget);
    expect(find.text('LOGOUT'), findsOneWidget);

    await tester.tap(find.text('Futures'));
    await tester.pumpAndSettle();
    expect(find.text('Active'), findsWidgets);
  });
}
