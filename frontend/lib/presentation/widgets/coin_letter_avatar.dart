import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';

abstract final class MarketFormat {
  static String price(double value) {
    if (value >= 1000) {
      return value.toStringAsFixed(2);
    }
    if (value >= 1) {
      return value.toStringAsFixed(4);
    }
    return value.toStringAsFixed(6);
  }

  static String compact(double value) {
    final abs = value.abs();
    if (abs >= 1e9) {
      return '${(value / 1e9).toStringAsFixed(2)}B';
    }
    if (abs >= 1e6) {
      return '${(value / 1e6).toStringAsFixed(2)}M';
    }
    if (abs >= 1e3) {
      return '${(value / 1e3).toStringAsFixed(2)}K';
    }
    return price(value);
  }

  static String signedPercent(double value) {
    final sign = value >= 0 ? '+' : '';
    return '$sign${value.toStringAsFixed(2)}%';
  }

  static Color changeColor(double value) => value >= 0 ? AppColors.accent : AppColors.loss;

  static Color avatarColor(String symbol) {
    final hue = (symbol.hashCode.abs() % 360).toDouble();
    return HSVColor.fromAHSV(1, hue, 0.42, 0.28).toColor();
  }

  static String quoteName(String symbol) {
    final upper = symbol.toUpperCase();
    if (upper.endsWith('USDT')) {
      return 'Tether';
    }
    if (upper.endsWith('USD')) {
      return 'US Dollar';
    }
    return 'USDT';
  }
}

class CoinLetterAvatar extends StatelessWidget {
  const CoinLetterAvatar({super.key, required this.symbol, required this.name, this.radius = 20});

  final String symbol;
  final String name;
  final double radius;

  @override
  Widget build(BuildContext context) {
    final raw = name.isNotEmpty ? name : symbol;
    final letter = raw.isEmpty ? '?' : raw.characters.first.toUpperCase();
    return CircleAvatar(
      radius: radius,
      backgroundColor: MarketFormat.avatarColor(symbol),
      child: Text(
        letter,
        style: TextStyle(
          color: AppColors.onBackground,
          fontWeight: FontWeight.w800,
          fontSize: radius * 0.85,
        ),
      ),
    );
  }
}
