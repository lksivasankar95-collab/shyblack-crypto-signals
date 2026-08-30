import 'package:flutter/material.dart';

import 'app_colors.dart';

abstract final class AppTheme {
  static ThemeData dark() {
    const scheme = ColorScheme.dark(
      surface: AppColors.background,
      onSurface: AppColors.onBackground,
      primary: AppColors.accent,
      onPrimary: Color(0xFF003318),
      secondary: AppColors.card,
      onSecondary: AppColors.onCard,
      error: AppColors.loss,
      onError: AppColors.onBackground,
      outline: Color(0xFF2A2A2A),
    );

    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      colorScheme: scheme,
      scaffoldBackgroundColor: AppColors.background,
      canvasColor: AppColors.background,
      cardColor: AppColors.card,
      dividerColor: scheme.outline,
      appBarTheme: const AppBarTheme(
        backgroundColor: AppColors.background,
        foregroundColor: AppColors.onBackground,
        elevation: 0,
        centerTitle: false,
      ),
      cardTheme: CardThemeData(
        color: AppColors.card,
        elevation: 0,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.card,
        hintStyle: const TextStyle(color: AppColors.muted),
        labelStyle: const TextStyle(color: AppColors.muted),
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: AppColors.accent, width: 1.2),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: AppColors.accent, width: 1.8),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: AppColors.loss),
        ),
        focusedErrorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: AppColors.loss, width: 1.8),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.accent,
          foregroundColor: const Color(0xFF003318),
          elevation: 0,
          minimumSize: const Size.fromHeight(52),
          textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800, letterSpacing: 0.4),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: AppColors.onBackground,
          minimumSize: const Size.fromHeight(52),
          side: const BorderSide(color: AppColors.accent),
          textStyle: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(foregroundColor: AppColors.accent),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: AppColors.card,
        indicatorColor: AppColors.accent.withValues(alpha: 0.18),
        labelTextStyle: WidgetStateProperty.resolveWith((states) {
          final selected = states.contains(WidgetState.selected);
          return TextStyle(
            fontSize: 12,
            fontWeight: selected ? FontWeight.w600 : FontWeight.w500,
            color: selected ? AppColors.accent : AppColors.muted,
          );
        }),
        iconTheme: WidgetStateProperty.resolveWith((states) {
          final selected = states.contains(WidgetState.selected);
          return IconThemeData(
            color: selected ? AppColors.accent : AppColors.muted,
          );
        }),
      ),
      textTheme: const TextTheme(
        headlineSmall: TextStyle(color: AppColors.onBackground, fontWeight: FontWeight.w600),
        titleMedium: TextStyle(color: AppColors.onBackground, fontWeight: FontWeight.w600),
        bodyMedium: TextStyle(color: AppColors.onCard),
        bodySmall: TextStyle(color: AppColors.muted),
      ),
    );
  }
}
