import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/constants/app_constants.dart';
import 'core/theme/app_theme.dart';
import 'presentation/screens/auth/splash_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ProviderScope(child: ShyBlackApp()));
}

class ShyBlackApp extends StatelessWidget {
  const ShyBlackApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: AppConstants.appName,
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark(),
      themeMode: ThemeMode.dark,
      home: const SplashScreen(),
    );
  }
}
