import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:cryptosignals/core/theme/app_theme.dart';
import 'package:cryptosignals/presentation/screens/auth/login_screen.dart';

/// Verifies the responsive login redesign: bespoke branding panel on desktop,
/// compact single-column on mobile/tablet. Authentication widgets (and their
/// exact labels) must remain present on every breakpoint.
void main() {
  Widget scaffold() {
    final theme = AppTheme.dark();
    return ProviderScope(
      child: MaterialApp(
        theme: theme,
        darkTheme: theme,
        themeMode: ThemeMode.dark,
        home: const LoginScreen(),
      ),
    );
  }

  Future<void> pumpAt(WidgetTester tester, Size size) async {
    tester.view.physicalSize = size;
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(scaffold());
    await tester.pumpAndSettle();
  }

  testWidgets('desktop: two-column branding panel + login card', (tester) async {
    await pumpAt(tester, const Size(1440, 900));

    // Login card (unchanged functional text).
    expect(find.text('Welcome Back'), findsOneWidget);
    expect(find.text('Sign In'), findsOneWidget);
    expect(find.text('Continue with Google'), findsOneWidget);

    // Branding panel.
    expect(find.text('Real-Time Signals'), findsOneWidget);
    expect(find.text('Advanced Strategies'), findsOneWidget);
    expect(find.text('Market News'), findsOneWidget);
    expect(find.text('Secure & Reliable'), findsOneWidget);

    expect(tester.takeException(), isNull);
  });

  testWidgets('tablet: compact single column, no overflow', (tester) async {
    await pumpAt(tester, const Size(834, 1112));

    expect(find.text('Welcome Back'), findsOneWidget);
    expect(find.text('Sign In'), findsOneWidget);
    expect(find.text('Continue with Google'), findsOneWidget);
    // Branding features are compacted away on small widths.
    expect(find.text('Real-Time Signals'), findsNothing);

    expect(tester.takeException(), isNull);
  });

  testWidgets('mobile: full-width card, branding compact', (tester) async {
    await pumpAt(tester, const Size(390, 844));

    expect(find.text('Welcome Back'), findsOneWidget);
    expect(find.text('Sign In'), findsOneWidget);
    expect(find.text('Continue with Google'), findsOneWidget);
    expect(find.text('Remember Me'), findsOneWidget);
    expect(find.text('Forgot Password?'), findsOneWidget);
    expect(find.text('Real-Time Signals'), findsNothing);

    expect(tester.takeException(), isNull);
  });
}
