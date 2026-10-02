import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../domain/entities/portfolio_account.dart';
import '../../providers/portfolio_controller.dart';
import '../../widgets/portfolio_widgets.dart';

/// Unified, read-only Portfolio.
///
/// Shows one account mode (PAPER or LIVE) and one account category (MAIN,
/// SPOT, FUTURES, OPTIONS) at a time. The two selectors are independent, so a
/// SPOT selection never leaks into a FUTURES view, and switching mode never
/// shows the previous mode's figures under the new label.
///
/// This screen is a view layer only. It contains no order, close or cancel
/// control: LIVE execution lives on the dedicated live/futures screens and PAPER
/// execution on the paper screen, reachable from Settings.
class PortfolioScreen extends ConsumerWidget {
  const PortfolioScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final scope = ref.watch(portfolioSelectionProvider);
    final async = ref.watch(portfolioControllerProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: Column(
          children: [
            _Selectors(scope: scope),
            const Divider(color: Color(0xFF2A2A2A), height: 1),
            Expanded(
              child: async.when(
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (error, stack) => _ErrorState(
                  onRetry: () => ref.invalidate(portfolioControllerProvider),
                ),
                data: (data) => _ScopeBody(data: data, scope: scope),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Selectors extends ConsumerWidget {
  const _Selectors({required this.scope});

  final PortfolioScope scope;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final notifier = ref.read(portfolioSelectionProvider.notifier);
    return Padding(
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Text(
                'PORTFOLIO',
                style: TextStyle(
                  color: AppColors.accent,
                  fontSize: 15,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 1.2,
                ),
              ),
              const Spacer(),
              // PAPER is simulated; LIVE is the real exchange account. The badge
              // makes it impossible to mistake one for the other.
              Text(
                scope.mode == PortfolioMode.paper ? 'SIMULATION' : 'LIVE',
                style: TextStyle(
                  color: scope.mode == PortfolioMode.paper
                      ? AppColors.muted
                      : AppColors.loss,
                  fontSize: 10,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 1.0,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          PortfolioModeSelector(
            selected: scope.mode,
            onChanged: (mode) => notifier.selectMode(mode),
          ),
          const SizedBox(height: 10),
          PortfolioCategoryTabs(
            selected: scope.category,
            onChanged: (category) => notifier.selectCategory(category),
          ),
        ],
      ),
    );
  }
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.onRetry});

  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(14),
      children: [
        const PortfolioStatePanel(
          title: 'Could not load this account',
          message:
              'The portfolio could not be read. This is a transport or server problem, not an '
              'empty account.',
          icon: Icons.cloud_off,
          isError: true,
        ),
        const SizedBox(height: 12),
        OutlinedButton(
          onPressed: onRetry,
          style: OutlinedButton.styleFrom(
            foregroundColor: AppColors.accent,
            side: const BorderSide(color: AppColors.accent),
            minimumSize: const Size.fromHeight(44),
          ),
          child: const Text('RETRY'),
        ),
      ],
    );
  }
}

class _ScopeBody extends ConsumerWidget {
  const _ScopeBody({required this.data, required this.scope});

  final PortfolioViewData data;
  final PortfolioScope scope;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // A response that does not belong to the scope on screen is discarded rather
    // than painted, which is the guard against one mode's values appearing under
    // another mode's label. Normally unreachable, because changing the selection
    // rebuilds the request; shown as a static, non-animating state rather than a
    // spinner so it can never become an endless loading indicator.
    if (!data.matchesScope) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Text(
            'Switching account scope...',
            style: TextStyle(color: AppColors.muted, fontSize: 12),
          ),
        ),
      );
    }

    final account = data.account;

    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(portfolioControllerProvider);
      },
      child: ListView(
        padding: const EdgeInsets.fromLTRB(14, 14, 14, 32),
        children: [
          _Summary(account: account),
          const SizedBox(height: 12),
          if (account.availability.isUnsupported)
            PortfolioStatePanel(
              title: 'Options not supported',
              message: account.statusMessage ??
                  'Options trading is a reserved capability. No engine, account or exchange '
                      'options API is integrated, so no data can be shown.',
              icon: Icons.block,
            )
          else ...[
            if (account.availability == PortfolioAvailability.unavailable)
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: PortfolioStatePanel(
                  title: 'Summary unavailable',
                  message: account.statusMessage ??
                      'The exchange provides no single main-wallet balance for this account, so '
                          'no figure can be shown without inventing one.',
                  icon: Icons.info_outline,
                ),
              ),
            _PositionsSection(data: data),
          ],
        ],
      ),
    );
  }
}

class _Summary extends StatelessWidget {
  const _Summary({required this.account});

  final PortfolioAccount account;

  @override
  Widget build(BuildContext context) {
    final quote = account.quoteCurrency;
    return PortfolioCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text(
                account.accountCategory.apiValue,
                style: const TextStyle(
                  color: AppColors.onCard,
                  fontSize: 13,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(width: 8),
              PortfolioAvailabilityChip(availability: account.availability),
              const Spacer(),
              if (account.exchange != null)
                Text(
                  account.exchange!,
                  style: const TextStyle(
                    color: AppColors.muted,
                    fontSize: 10,
                    fontWeight: FontWeight.w700,
                  ),
                ),
            ],
          ),
          const SizedBox(height: 14),
          Wrap(
            spacing: 18,
            runSpacing: 14,
            children: [
              PortfolioKpi(
                label: 'Equity',
                value: PortfolioValue(
                  value: account.equity,
                  suffix: quote,
                  emphasise: true,
                ),
              ),
              PortfolioKpi(
                label: 'Available',
                value: PortfolioValue(
                  value: account.availableBalance,
                  suffix: quote,
                ),
              ),
              PortfolioKpi(
                label: 'Invested',
                value: PortfolioValue(
                  value: account.invested,
                  suffix: quote,
                ),
              ),
              PortfolioKpi(
                label: 'Realized P&L',
                value: PortfolioValue(
                  value: account.realizedPnl,
                  suffix: quote,
                  signed: true,
                ),
              ),
              PortfolioKpi(
                label: 'Unrealized P&L',
                value: PortfolioValue(
                  value: account.unrealizedPnl,
                  suffix: quote,
                  signed: true,
                ),
              ),
            ],
          ),
          if (account.lastSyncedAt != null) ...[
            const SizedBox(height: 12),
            Text(
              'Last sync ${account.lastSyncedAt!.toIso8601String().substring(0, 19)}Z',
              style: const TextStyle(color: AppColors.muted, fontSize: 10),
            ),
          ],
          if (account.statusMessage != null &&
              account.statusMessage!.isNotEmpty &&
              account.availability.isAvailable) ...[
            const SizedBox(height: 10),
            Text(
              account.statusMessage!,
              style: const TextStyle(color: AppColors.muted, fontSize: 11, height: 1.4),
            ),
          ],
        ],
      ),
    );
  }
}

class _PositionsSection extends StatelessWidget {
  const _PositionsSection({required this.data});

  final PortfolioViewData data;

  @override
  Widget build(BuildContext context) {
    final positions = data.positions;
    final account = data.account;

    // A scope with no position concept is not the same as a supported account
    // holding nothing, and is never rendered as a count of zero.
    if (positions.isEmptyBecauseUnsupported) {
      return PortfolioStatePanel(
        title: positions.availability.isUnsupported
            ? 'No position concept'
            : 'Positions unavailable',
        message: positions.statusMessage ??
            'This scope does not expose positions, so none are shown.',
        icon: Icons.info_outline,
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const Text(
              'POSITIONS',
              style: TextStyle(
                color: AppColors.muted,
                fontSize: 11,
                fontWeight: FontWeight.w800,
                letterSpacing: 0.8,
              ),
            ),
            const Spacer(),
            if (account.openPositionCount != null)
              Text(
                '${account.openPositionCount} open',
                style: const TextStyle(
                  color: AppColors.muted,
                  fontSize: 11,
                ),
              ),
          ],
        ),
        const SizedBox(height: 10),
        if (positions.positions.isEmpty)
          PortfolioCard(
            child: Text(
              account.accountCategory == PortfolioCategory.main
                  ? 'No positions in this account.'
                  : 'No open positions in this scope.',
              style: const TextStyle(color: AppColors.muted, fontSize: 12),
            ),
          )
        else
          ...positions.positions.map((p) => Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: _PositionCard(position: p),
              )),
      ],
    );
  }
}

class _PositionCard extends StatelessWidget {
  const _PositionCard({required this.position});

  final PortfolioPosition position;

  @override
  Widget build(BuildContext context) {
    final quote = _quoteFor(position);
    final isLong = position.side == 'LONG';

    return PortfolioCard(
      padding: const EdgeInsets.all(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text(
                position.symbol,
                style: const TextStyle(
                  color: AppColors.onCard,
                  fontSize: 14,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(width: 8),
              if (position.side != null)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: isLong
                        ? AppColors.accent.withValues(alpha: 0.16)
                        : AppColors.loss.withValues(alpha: 0.16),
                    borderRadius: BorderRadius.circular(5),
                  ),
                  child: Text(
                    position.side!,
                    style: TextStyle(
                      color: isLong ? AppColors.accent : AppColors.loss,
                      fontSize: 9,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
              if (position.leverage != null) ...[
                const SizedBox(width: 6),
                Text(
                  '${position.leverage}x',
                  style: const TextStyle(
                    color: AppColors.muted,
                    fontSize: 10,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
              const Spacer(),
              if (position.status != null)
                Text(
                  position.status!,
                  style: const TextStyle(color: AppColors.muted, fontSize: 9),
                ),
            ],
          ),
          const SizedBox(height: 10),
          Wrap(
            spacing: 16,
            runSpacing: 10,
            children: [
              PortfolioKpi(
                label: 'Quantity',
                value: PortfolioValue(value: position.quantity),
              ),
              PortfolioKpi(
                label: 'Entry',
                value: PortfolioValue(value: position.entryPrice, suffix: quote),
              ),
              PortfolioKpi(
                label: 'Mark',
                value: PortfolioValue(value: position.currentPrice, suffix: quote),
              ),
              PortfolioKpi(
                label: 'Unrealized',
                value: PortfolioValue(
                  value: position.unrealizedPnl,
                  suffix: quote,
                  signed: true,
                ),
              ),
              if (position.liquidationPrice != null)
                PortfolioKpi(
                  label: 'Liq.',
                  value: PortfolioValue(
                    value: position.liquidationPrice,
                    suffix: quote,
                  ),
                ),
              // SL and the TP ladder are only rendered when the backend actually
              // supplies them. Nothing is derived or defaulted here.
              PortfolioKpi(
                label: 'SL',
                value: PortfolioValue(value: position.stopLoss, suffix: quote),
              ),
              PortfolioKpi(
                label: 'TP1',
                value: PortfolioValue(value: position.takeProfit1, suffix: quote),
              ),
              if (position.takeProfit2 != null || position.takeProfit3 != null) ...[
                PortfolioKpi(
                  label: 'TP2',
                  value: PortfolioValue(value: position.takeProfit2, suffix: quote),
                ),
                PortfolioKpi(
                  label: 'TP3',
                  value: PortfolioValue(value: position.takeProfit3, suffix: quote),
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }

  /// Quote currency is not carried on a position, so the common exchange quote is
  /// used for labelling only; no value is computed from it.
  String? _quoteFor(PortfolioPosition position) =>
      position.accountMode == PortfolioMode.paper ? null : 'USDT';
}
