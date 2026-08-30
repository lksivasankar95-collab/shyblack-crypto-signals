import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:cryptosignals/core/constants/app_constants.dart';
import 'package:cryptosignals/main.dart';

void main() {
  testWidgets('splash shows branding then opens login', (WidgetTester tester) async {
    await tester.pumpWidget(const ProviderScope(child: ShyBlackApp()));

    expect(find.text(AppConstants.appName), findsOneWidget);
    expect(find.text(AppConstants.tagline), findsOneWidget);

    await tester.pump(const Duration(seconds: 2));
    await tester.pumpAndSettle();

    expect(find.text('Login'), findsOneWidget);
    expect(find.text('Continue with Google'), findsOneWidget);
  });
}
