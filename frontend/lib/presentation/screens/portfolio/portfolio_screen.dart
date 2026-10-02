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
          const SizedBox(height: 18),
          const _HoldingsSection(),
          const SizedBox(height: 18),
          const _SyncStatusSection(),
          const SizedBox(height: 18),
          const _HistorySection(),
        ],
      ),
    );
  }
}

/// Per-asset wallet holdings. Only LIVE spot has them, and they are deliberately
/// labelled as holdings: the spot API has no open-position concept.
class _HoldingsSection extends ConsumerWidget {
  const _HoldingsSection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final scope = ref.watch(portfolioSelectionProvider);
    final holdings = ref.watch(portfolioHoldingsProvider);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'HOLDINGS',
          style: TextStyle(
            color: AppColors.muted,
            fontSize: 11,
            fontWeight: FontWeight.w800,
            letterSpacing: 0.8,
          ),
        ),
        const SizedBox(height: 10),
        holdings.when(
          loading: () => const PortfolioCard(
            child: Text(
              'Loading holdings...',
              style: TextStyle(color: AppColors.muted, fontSize: 12),
            ),
          ),
          error: (error, stack) => PortfolioStatePanel(
            title: 'Holdings unavailable',
            message:
                'Wallet balances could not be read. This is a transport or server problem, not an '
                'empty wallet.',
            icon: Icons.cloud_off,
            isError: true,
          ),
          data: (data) {
            // Holdings are a property of the mode's spot wallet, not of the
            // selected category, so only the mode is matched here. The category
            // is deliberately not compared: a FUTURES selection must still be
            // able to see that the spot wallet holds nothing, rather than a
            // response silently disappearing.
            if (data.accountMode != scope.mode) {
              return const SizedBox.shrink();
            }
            if (data.isEmptyBecauseUnsupported) {
              return PortfolioStatePanel(
                title: data.availability.isUnsupported
                    ? 'No wallet holdings'
                    : 'Holdings unavailable',
                message: data.statusMessage ??
                    'This scope does not expose per-asset wallet balances.',
                icon: Icons.info_outline,
              );
            }
            if (data.holdings.isEmpty) {
              return const PortfolioCard(
                child: Text(
                  'No assets held in this wallet.',
                  style: TextStyle(color: AppColors.muted, fontSize: 12),
                ),
              );
            }
            return Column(
              children: data.holdings
                  .map((h) => Padding(
                        padding: const EdgeInsets.only(bottom: 8),
                        child: _HoldingRow(holding: h),
                      ))
                  .toList(),
            );
          },
        ),
      ],
    );
  }
}

class _HoldingRow extends StatelessWidget {
  const _HoldingRow({required this.holding});

  final PortfolioHolding holding;

  @override
  Widget build(BuildContext context) {
    return PortfolioCard(
      padding: const EdgeInsets.all(12),
      // Wrap rather than Row: three labelled figures plus the asset name do not
      // fit a narrow phone, and an overflow would be worse than a wrapped row.
      child: Wrap(
        spacing: 18,
        runSpacing: 12,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          SizedBox(
            width: 88,
            child: Text(
              holding.asset,
              style: const TextStyle(
                color: AppColors.onCard,
                fontSize: 13,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
          PortfolioKpi(
            label: 'Free',
            value: PortfolioValue(value: holding.free),
          ),
          PortfolioKpi(
            label: 'Locked',
            value: PortfolioValue(value: holding.locked),
          ),
          PortfolioKpi(
            label: 'Total',
            value: PortfolioValue(value: holding.total, emphasise: true),
          ),
        ],
      ),
    );
  }
}

/// Connection state, freshness and why a scope is not current. A stale scope is
/// always labelled as stale; the figures above it are never silently presented as
/// current.
class _SyncStatusSection extends ConsumerWidget {
  const _SyncStatusSection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final scope = ref.watch(portfolioSelectionProvider);
    final status = ref.watch(portfolioSyncStatusControllerProvider);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'SYNC STATUS',
          style: TextStyle(
            color: AppColors.muted,
            fontSize: 11,
            fontWeight: FontWeight.w800,
            letterSpacing: 0.8,
          ),
        ),
        const SizedBox(height: 10),
        status.when(
          loading: () => const SizedBox.shrink(),
          error: (error, stack) => PortfolioStatePanel(
            title: 'Sync status unavailable',
            message: 'The synchronization state of this scope could not be read.',
            icon: Icons.cloud_off,
            isError: true,
          ),
          data: (data) {
            if (data.accountMode != scope.mode ||
                data.accountCategory != scope.category) {
              return const SizedBox.shrink();
            }
            final rows = <Widget>[
              if (data.connectionStatus != null)
                _StatusRow(label: 'Connection', value: data.connectionStatus!),
              if (data.lastRestSync != null)
                _StatusRow(
                  label: 'Last REST sync',
                  value: _stamp(data.lastRestSync!),
                ),
              if (data.lastEvent != null)
                _StatusRow(
                  label: 'Last stream event',
                  value: _stamp(data.lastEvent!),
                ),
            ];

            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (data.isStale)
                  const Padding(
                    padding: EdgeInsets.only(bottom: 8),
                    child: PortfolioStatePanel(
                      title: 'Data is stale',
                      message:
                          'The values above are the last known ones and are past the freshness '
                          'window. They must not be read as current.',
                      icon: Icons.history_toggle_off,
                      isError: true,
                    ),
                  ),
                if (rows.isEmpty &&
                    data.message != null &&
                    data.message!.isNotEmpty)
                  PortfolioStatePanel(
                    title: 'Not synchronised',
                    message: data.message!,
                    icon: Icons.info_outline,
                  )
                else if (rows.isNotEmpty)
                  PortfolioCard(
                    padding: const EdgeInsets.all(12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: rows,
                    ),
                  ),
              ],
            );
          },
        ),
      ],
    );
  }

  static String _stamp(DateTime value) =>
      '${value.toIso8601String().substring(0, 19)}Z';
}

class _StatusRow extends StatelessWidget {
  const _StatusRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      // The value is the long part here (a full UTC timestamp), so it takes the
      // remaining width and wraps instead of forcing the row to overflow.
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: const TextStyle(color: AppColors.muted, fontSize: 11),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              value,
              textAlign: TextAlign.right,
              style: const TextStyle(
                color: AppColors.onCard,
                fontSize: 11,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Exchange history for the selected scope: orders, fills or income over an
/// explicit window.
class _HistorySection extends ConsumerWidget {
  const _HistorySection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final query = ref.watch(portfolioHistorySelectionProvider);
    final history = ref.watch(portfolioHistoryControllerProvider);
    final scope = ref.watch(portfolioSelectionProvider);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const Text(
              'HISTORY',
              style: TextStyle(
                color: AppColors.muted,
                fontSize: 11,
                fontWeight: FontWeight.w800,
                letterSpacing: 0.8,
              ),
            ),
            const Spacer(),
            // The count is read only from a value already in hand, so it never
            // renders for a scope other than the one on screen.
            if (history.asData?.value case final loaded?
                when loaded.entries.isNotEmpty)
              Text(
                '${loaded.entries.length} records',
                style: const TextStyle(color: AppColors.muted, fontSize: 11),
              ),
          ],
        ),
        const SizedBox(height: 8),
        _HistoryTypeTabs(
          selected: query.type,
          onChanged: (type) => ref
              .read(portfolioHistorySelectionProvider.notifier)
              .selectType(type),
        ),
        const SizedBox(height: 8),
        // Spot order and fill history is per symbol on the exchange, so the
        // symbol is requested explicitly rather than silently omitted.
        if (scope.category == PortfolioCategory.spot)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: _SymbolField(
              initial: query.symbol,
              onSubmitted: (value) => ref
                  .read(portfolioHistorySelectionProvider.notifier)
                  .setSymbol(value),
            ),
          ),
        const SizedBox(height: 4),
        history.when(
          loading: () => const PortfolioCard(
            child: Text(
              'Loading history...',
              style: TextStyle(color: AppColors.muted, fontSize: 12),
            ),
          ),
          error: (error, stack) => PortfolioStatePanel(
            title: 'History unavailable',
            message:
                'The exchange history could not be read. This is a transport or server problem, '
                'not an account with no activity.',
            icon: Icons.cloud_off,
            isError: true,
          ),
          data: (data) {
            if (data.accountMode != scope.mode ||
                data.accountCategory != scope.category) {
              return const SizedBox.shrink();
            }
            if (data.isEmptyBecauseUnsupported) {
              return PortfolioStatePanel(
                title: 'No history available',
                message: data.statusMessage ??
                    'This scope does not expose exchange history.',
                icon: Icons.info_outline,
              );
            }
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (data.isPartial)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: PortfolioStatePanel(
                      title: 'Partial window',
                      message: data.statusMessage ??
                          'The record cap was reached, so this is not the whole period.',
                      icon: Icons.warning_amber_rounded,
                      isError: true,
                    ),
                  ),
                if (data.windowFrom != null && data.windowTo != null)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: Text(
                      '${_stamp(data.windowFrom!)} to ${_stamp(data.windowTo!)}',
                      style: const TextStyle(color: AppColors.muted, fontSize: 10),
                    ),
                  ),
                if (data.entries.isEmpty)
                  const PortfolioCard(
                    child: Text(
                      'No records in this window.',
                      style: TextStyle(color: AppColors.muted, fontSize: 12),
                    ),
                  )
                else
                  ...data.entries.map(
                    (entry) => Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: _HistoryCard(entry: entry),
                    ),
                  ),
              ],
            );
          },
        ),
      ],
    );
  }

  static String _stamp(DateTime value) =>
      value.toIso8601String().substring(0, 19);
}

class _HistoryTypeTabs extends StatelessWidget {
  const _HistoryTypeTabs({required this.selected, required this.onChanged});

  final PortfolioHistoryType selected;
  final ValueChanged<PortfolioHistoryType> onChanged;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: PortfolioHistoryType.values.map((type) {
        final active = type == selected;
        return Expanded(
          child: Padding(
            padding: const EdgeInsets.only(right: 6),
            child: InkWell(
              onTap: () => onChanged(type),
              child: Container(
                padding: const EdgeInsets.symmetric(vertical: 7),
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: active
                      ? AppColors.accent.withValues(alpha: 0.16)
                      : Colors.transparent,
                  borderRadius: BorderRadius.circular(6),
                  border: Border.all(
                    color: active ? AppColors.accent : const Color(0xFF2A2A2A),
                  ),
                ),
                child: Text(
                  type.apiValue,
                  style: TextStyle(
                    color: active ? AppColors.accent : AppColors.muted,
                    fontSize: 10,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 0.6,
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

class _SymbolField extends StatefulWidget {
  const _SymbolField({required this.initial, required this.onSubmitted});

  final String? initial;
  final ValueChanged<String?> onSubmitted;

  @override
  State<_SymbolField> createState() => _SymbolFieldState();
}

class _SymbolFieldState extends State<_SymbolField> {
  late final TextEditingController _controller =
      TextEditingController(text: widget.initial ?? '');

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: _controller,
      style: const TextStyle(color: AppColors.onCard, fontSize: 12),
      textInputAction: TextInputAction.search,
      onSubmitted: (value) {
        final trimmed = value.trim();
        widget.onSubmitted(trimmed.isEmpty ? null : trimmed.toUpperCase());
      },
      decoration: InputDecoration(
        isDense: true,
        hintText: 'Symbol, e.g. BTCUSDT',
        hintStyle: const TextStyle(color: AppColors.muted, fontSize: 12),
        prefixIcon: const Icon(Icons.search, color: AppColors.muted, size: 18),
        enabledBorder: OutlineInputBorder(
          borderSide: const BorderSide(color: Color(0xFF2A2A2A)),
          borderRadius: BorderRadius.circular(6),
        ),
        focusedBorder: OutlineInputBorder(
          borderSide: const BorderSide(color: AppColors.accent),
          borderRadius: BorderRadius.circular(6),
        ),
      ),
    );
  }
}

class _HistoryCard extends StatelessWidget {
  const _HistoryCard({required this.entry});

  final PortfolioHistoryEntry entry;

  @override
  Widget build(BuildContext context) {
    return PortfolioCard(
      padding: const EdgeInsets.all(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text(
                entry.symbol ?? entry.entryType,
                style: const TextStyle(
                  color: AppColors.onCard,
                  fontSize: 13,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(width: 8),
              if (entry.side != null)
                Text(
                  entry.side!,
                  style: const TextStyle(
                    color: AppColors.muted,
                    fontSize: 9,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              const Spacer(),
              if (entry.status != null)
                Text(
                  entry.status!,
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
                label: 'Price',
                value: PortfolioValue(value: entry.price),
              ),
              PortfolioKpi(
                label: 'Quantity',
                value: PortfolioValue(value: entry.quantity),
              ),
              if (entry.fee != null)
                PortfolioKpi(
                  label: 'Fee${entry.feeAsset != null ? ' (${entry.feeAsset})' : ''}',
                  value: PortfolioValue(value: entry.fee),
                ),
              if (entry.realizedPnl != null)
                PortfolioKpi(
                  label: 'Realized P&L',
                  value: PortfolioValue(value: entry.realizedPnl, signed: true),
                ),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              if (entry.orderId != null)
                Text(
                  'order ${entry.orderId}',
                  style: const TextStyle(color: AppColors.muted, fontSize: 9),
                ),
              if (entry.tradeId != null) ...[
                const SizedBox(width: 8),
                Text(
                  'trade ${entry.tradeId}',
                  style: const TextStyle(color: AppColors.muted, fontSize: 9),
                ),
              ],
              const Spacer(),
              if (entry.occurredAt != null)
                Text(
                  _stamp(entry.occurredAt!),
                  style: const TextStyle(color: AppColors.muted, fontSize: 9),
                ),
            ],
          ),
        ],
      ),
    );
  }

  static String _stamp(DateTime value) =>
      value.toIso8601String().substring(0, 19).replaceFirst('T', ' ');
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
