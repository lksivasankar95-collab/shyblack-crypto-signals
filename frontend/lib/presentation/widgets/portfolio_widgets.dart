import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../domain/entities/portfolio_account.dart';

/// Renders a financial value without ever inventing one.
///
/// A null value renders as an em dash, which is visually distinct from a real
/// zero. Nothing here uses `value ?? 0`.
class PortfolioValue extends StatelessWidget {
  const PortfolioValue({
    super.key,
    required this.value,
    this.suffix,
    this.signed = false,
    this.emphasise = false,
  });

  /// Null means the source does not provide this value. It is not a zero.
  final double? value;
  final String? suffix;

  /// Renders a leading + or - for profit and loss figures.
  final bool signed;
  final bool emphasise;

  /// Placeholder shown for a value the source does not provide.
  static const String missing = '—';

  static String format(double? value, {String? suffix, bool signed = false}) {
    if (value == null) return missing;
    final sign = signed && value > 0 ? '+' : '';
    return '$sign${value.toStringAsFixed(2)}${suffix == null || suffix.isEmpty ? '' : ' $suffix'}';
  }

  @override
  Widget build(BuildContext context) {
    final text = format(value, suffix: suffix, signed: signed);
    if (value == null) {
      return Text(
        text,
        style: TextStyle(
          color: AppColors.muted,
          fontSize: emphasise ? 20 : 13,
          fontWeight: FontWeight.w600,
        ),
      );
    }
    final color = signed && value! < 0 ? AppColors.loss : AppColors.onCard;
    return Text(
      text,
      style: TextStyle(
        color: emphasise ? (signed && value! < 0 ? AppColors.loss : AppColors.accent) : color,
        fontSize: emphasise ? 20 : 13,
        fontWeight: emphasise ? FontWeight.w800 : FontWeight.w600,
      ),
    );
  }
}

/// A small labelled figure, used across the account summary.
class PortfolioKpi extends StatelessWidget {
  const PortfolioKpi({super.key, required this.label, required this.value});

  final String label;
  final Widget value;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          label.toUpperCase(),
          style: const TextStyle(
            color: AppColors.muted,
            fontSize: 10,
            fontWeight: FontWeight.w800,
            letterSpacing: 0.6,
          ),
        ),
        const SizedBox(height: 4),
        value,
      ],
    );
  }
}

/// Coloured status chip describing availability, so an unavailable scope is
/// never visually identical to an empty one.
class PortfolioAvailabilityChip extends StatelessWidget {
  const PortfolioAvailabilityChip({super.key, required this.availability});

  final PortfolioAvailability availability;

  @override
  Widget build(BuildContext context) {
    final (Color color, String label) = switch (availability) {
      PortfolioAvailability.available => (AppColors.accent, 'CONNECTED'),
      PortfolioAvailability.syncing => (AppColors.muted, 'SYNCING'),
      PortfolioAvailability.stale => (AppColors.muted, 'STALE'),
      PortfolioAvailability.notConnected => (AppColors.muted, 'NOT CONNECTED'),
      PortfolioAvailability.disconnected => (AppColors.loss, 'DISCONNECTED'),
      PortfolioAvailability.unavailable => (AppColors.muted, 'UNAVAILABLE'),
      PortfolioAvailability.unsupported => (AppColors.muted, 'NOT SUPPORTED'),
      PortfolioAvailability.error => (AppColors.loss, 'ERROR'),
    };

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.14),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: color,
          fontSize: 10,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }
}

/// Shared card surface, matching the existing ShyBlack trading screens.
class PortfolioCard extends StatelessWidget {
  const PortfolioCard({super.key, required this.child, this.padding});

  final Widget child;
  final EdgeInsetsGeometry? padding;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: padding ?? const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(14),
      ),
      child: child,
    );
  }
}

/// Explicit state panel for a scope that cannot show figures.
///
/// Used for unsupported Options, an unavailable main wallet and any error, so
/// those three cases are visually distinct from a supported but empty account.
class PortfolioStatePanel extends StatelessWidget {
  const PortfolioStatePanel({
    super.key,
    required this.title,
    this.message,
    this.icon = Icons.info_outline,
    this.isError = false,
    this.action,
  });

  final String title;
  final String? message;
  final IconData icon;

  /// Errors use the loss colour; unsupported and unavailable stay neutral.
  final bool isError;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    return PortfolioCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            children: [
              Icon(
                icon,
                color: isError ? AppColors.loss : AppColors.muted,
                size: 18,
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  title,
                  style: TextStyle(
                    color: isError ? AppColors.loss : AppColors.onCard,
                    fontSize: 14,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
            ],
          ),
          if (message != null && message!.isNotEmpty) ...[
            const SizedBox(height: 8),
            Text(
              message!,
              style: const TextStyle(color: AppColors.muted, fontSize: 12, height: 1.4),
            ),
          ],
          if (action != null) ...[
            const SizedBox(height: 12),
            action!,
          ],
        ],
      ),
    );
  }
}

/// PAPER / LIVE selector. Visually obvious so one mode is never mistaken for
/// the other.
class PortfolioModeSelector extends StatelessWidget {
  const PortfolioModeSelector({
    super.key,
    required this.selected,
    required this.onChanged,
  });

  final PortfolioMode selected;
  final ValueChanged<PortfolioMode> onChanged;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: PortfolioMode.values.map((mode) {
        final active = mode == selected;
        return Expanded(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4),
            child: GestureDetector(
              key: ValueKey('portfolio-mode-${mode.apiValue}'),
              onTap: () => onChanged(mode),
              child: Container(
                padding: const EdgeInsets.symmetric(vertical: 10),
                decoration: BoxDecoration(
                  color: active
                      ? AppColors.accent.withValues(alpha: 0.16)
                      : AppColors.card,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(
                    color: active ? AppColors.accent : Colors.transparent,
                    width: 1.2,
                  ),
                ),
                child: Center(
                  child: Text(
                    mode == PortfolioMode.paper ? 'PAPER' : 'LIVE',
                    style: TextStyle(
                      color: active ? AppColors.accent : AppColors.muted,
                      fontSize: 13,
                      fontWeight: FontWeight.w800,
                      letterSpacing: 0.8,
                    ),
                  ),
                ),
              ),
            ),
          ),
        );
      }).toList(),
    );
  }
}

/// MAIN / SPOT / FUTURES / OPTIONS selector.
///
/// Scrollable rather than a fixed four-column row, so the tabs stay usable on a
/// narrow phone without horizontal overflow.
class PortfolioCategoryTabs extends StatelessWidget {
  const PortfolioCategoryTabs({
    super.key,
    required this.selected,
    required this.onChanged,
  });

  final PortfolioCategory selected;
  final ValueChanged<PortfolioCategory> onChanged;

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        children: PortfolioCategory.values.map((category) {
          final active = category == selected;
          return Padding(
            padding: const EdgeInsets.only(right: 8),
            child: GestureDetector(
              key: ValueKey('portfolio-category-${category.apiValue}'),
              onTap: () => onChanged(category),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                decoration: BoxDecoration(
                  color: active ? AppColors.accent : AppColors.card,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(
                  category.apiValue,
                  style: TextStyle(
                    color: active ? AppColors.background : AppColors.muted,
                    fontSize: 11,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 0.6,
                  ),
                ),
              ),
            ),
          );
        }).toList(),
      ),
    );
  }
}
