import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/constants/app_constants.dart';
import 'core/theme/app_colors.dart';
import 'core/theme/app_theme.dart';
import 'presentation/providers/auth_session.dart';
import 'presentation/screens/auth/login_screen.dart';
import 'presentation/screens/auth/splash_screen.dart';
import 'presentation/shell/main_shell.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ProviderScope(child: ShyBlackApp()));
}

class ShyBlackApp extends ConsumerWidget {
  const ShyBlackApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = AppTheme.dark();
    final session = ref.watch(authSessionProvider);

    return MaterialApp(
      title: AppConstants.appName,
      debugShowCheckedModeBanner: false,
      theme: theme,
      darkTheme: theme,
      themeMode: ThemeMode.dark,
      color: AppColors.background,
      builder: (context, child) {
        return ColoredBox(
          color: AppColors.background,
          child: child ?? const SizedBox.expand(),
        );
      },
      home: session.when(
        loading: () => const SplashScreen(),
        error: (_, _) => const LoginScreen(),
        data: (status) => switch (status) {
          AuthStatus.authenticated => const MainShell(),
          AuthStatus.unauthenticated => const LoginScreen(),
        },
      ),
    );
  }
}
