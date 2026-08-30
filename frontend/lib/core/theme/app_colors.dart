import 'package:flutter/material.dart';

/// Global palette from the ShyBlack master spec. Screens must use these
/// (or [ThemeData]) instead of hardcoding hex colors.
abstract final class AppColors {
  static const Color background = Color(0xFF0D0D0D);
  static const Color card = Color(0xFF1A1A1A);
  static const Color accent = Color(0xFF00E676);
  static const Color loss = Color(0xFFFF3B30);

  static const Color onBackground = Color(0xFFFFFFFF);
  static const Color onCard = Color(0xFFE8E8E8);
  static const Color muted = Color(0xFF9E9E9E);
}
