import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../domain/entities/portfolio_account.dart';
import '../../presentation/providers/portfolio_controller.dart';

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
        color: emphasise
            ? (signed && value! < 0 ? AppColors.loss : AppColors.accent)
            : color,
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
              style: const TextStyle(
                color: AppColors.muted,
                fontSize: 12,
                height: 1.4,
              ),
            ),
          ],
          if (action != null) ...[const SizedBox(height: 12), action!],
        ],
      ),
    );
  }
}

/// SPOT / FUTURES / OPTIONS market-account tabs.
///
/// MAIN is deliberately absent: it is the backend's aggregate read-model scope, not an
/// account the user owns, and offering it as a tab would blur the wallet summary with a
/// real market account.
///
/// Scrollable rather than a fixed row, so the tabs stay usable on a narrow phone
/// without horizontal overflow.
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
        children: PortfolioCategory.portfolioTabs.map((category) {
          final active = category == selected;
          return Padding(
            padding: const EdgeInsets.only(right: 8),
            child: GestureDetector(
              key: ValueKey('portfolio-category-${category.apiValue}'),
              onTap: () => onChanged(category),
              child: Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 14,
                  vertical: 8,
                ),
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

/// Read-only badge naming the account a scope belongs to.
///
/// This reports which account the data comes from; it is **not** a control. The
/// account mode is chosen in Settings and cannot be changed here.
class PortfolioAccountBadge extends StatelessWidget {
  const PortfolioAccountBadge({super.key, required this.mode});

  final PortfolioMode mode;

  @override
  Widget build(BuildContext context) {
    final live = mode == PortfolioMode.live;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: (live ? AppColors.loss : AppColors.muted).withValues(
          alpha: 0.14,
        ),
        borderRadius: BorderRadius.circular(6),
        border: Border.all(
          color: (live ? AppColors.loss : AppColors.muted).withValues(
            alpha: 0.5,
          ),
        ),
      ),
      child: Text(
        live ? 'LIVE ACCOUNT' : 'PAPER ACCOUNT',
        // Shrink rather than overflow: this badge shares a row with the title
        // and, on a paper account, the capital-management control, so on a narrow
        // phone the available width is genuinely tight.
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: TextStyle(
          color: live ? AppColors.loss : AppColors.muted,
          fontSize: 9,
          fontWeight: FontWeight.w800,
          letterSpacing: 0.8,
        ),
      ),
    );
  }
}

/// A section heading with an optional right-hand count.
///
/// The label takes the space it needs and the trailing count yields, so a long heading
/// on a narrow screen ellipsises the label rather than overflowing the row.
class PortfolioSectionHeader extends StatelessWidget {
  const PortfolioSectionHeader({super.key, required this.label, this.trailing});

  final String label;
  final String? trailing;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Flexible(
          child: Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              color: AppColors.muted,
              fontSize: 11,
              fontWeight: FontWeight.w800,
              letterSpacing: 0.8,
            ),
          ),
        ),
        if (trailing != null) ...[
          const SizedBox(width: 8),
          Text(
            trailing!,
            style: const TextStyle(color: AppColors.muted, fontSize: 11),
          ),
        ],
      ],
    );
  }
}

/// Compact status pill for an exchange order state.
///
/// An [PortfolioOrderStatus.unknown] order is styled as unresolved rather than as a
/// terminal outcome, so an order whose state could not be read is never visually
/// presented as filled or successfully closed.
class PortfolioOrderStatusChip extends StatelessWidget {
  const PortfolioOrderStatusChip({super.key, required this.status});

  final PortfolioOrderStatus status;

  @override
  Widget build(BuildContext context) {
    final (Color color, String label) = switch (status) {
      PortfolioOrderStatus.open => (AppColors.muted, 'NEW'),
      PortfolioOrderStatus.partiallyFilled => (AppColors.accent, 'PART FILLED'),
      PortfolioOrderStatus.filled => (AppColors.accent, 'FILLED'),
      PortfolioOrderStatus.canceled => (AppColors.muted, 'CANCELED'),
      PortfolioOrderStatus.rejected => (AppColors.loss, 'REJECTED'),
      PortfolioOrderStatus.expired => (AppColors.muted, 'EXPIRED'),
      PortfolioOrderStatus.unknown => (AppColors.loss, 'UNKNOWN'),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.14),
        borderRadius: BorderRadius.circular(5),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: color,
          fontSize: 9,
          fontWeight: FontWeight.w800,
          letterSpacing: 0.4,
        ),
      ),
    );
  }
}

/// Horizontally scrollable section strip shown below the market-account tabs.
///
/// Only one section is rendered at a time, so this strip is the sole navigation
/// between a wallet/position book, the resting-order book and the trade history.
/// Built for a phone: it scrolls rather than shrinking labels to fit, and a label
/// never wraps onto a second line.
class PortfolioSectionTabs extends StatelessWidget {
  const PortfolioSectionTabs({
    super.key,
    required this.sections,
    required this.selected,
    required this.onChanged,
  });

  final List<PortfolioSection> sections;
  final PortfolioSection selected;
  final ValueChanged<PortfolioSection> onChanged;

  @override
  Widget build(BuildContext context) {
    if (sections.isEmpty) return const SizedBox.shrink();
    return SizedBox(
      key: const Key('portfolio-section-strip'),
      height: 34,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 14),
        itemCount: sections.length,
        separatorBuilder: (_, _) => const SizedBox(width: 8),
        itemBuilder: (context, index) {
          final section = sections[index];
          final active = section == selected;
          return GestureDetector(
            key: Key('section-tab-${section.name}'),
            onTap: () => onChanged(section),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: active
                    ? AppColors.accent.withValues(alpha: 0.16)
                    : Colors.transparent,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(
                  color: active ? AppColors.accent : const Color(0xFF2A2A2A),
                ),
              ),
              child: Text(
                section.label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  color: active ? AppColors.accent : AppColors.muted,
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}
