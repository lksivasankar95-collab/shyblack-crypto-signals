import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../domain/entities/paper_account.dart';
import '../../../domain/entities/paper_performance.dart';
import '../../../domain/entities/paper_position.dart';
import '../../providers/paper_trading_controller.dart';
import '../../providers/market_prices_controller.dart';

class PaperTradingScreen extends ConsumerWidget {
  const PaperTradingScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncView = ref.watch(paperTradingControllerProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: asyncView.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, _) => _ErrorPanel(
            message: 'Could not load paper trading',
            onRetry: () => ref.invalidate(paperTradingControllerProvider),
          ),
          data: (view) => RefreshIndicator(
            onRefresh: () =>
                ref.read(paperTradingControllerProvider.notifier).refresh(silent: false),
            child: DefaultTabController(
              length: 3,
              child: Column(
                children: [
                  _AccountHeader(
                    account: view.account,
                    openPositions: view.openPositions.length,
                  ),
                  const TabBar(
                    labelColor: AppColors.accent,
                    unselectedLabelColor: AppColors.muted,
                    indicatorColor: AppColors.accent,
                    tabs: [
                      Tab(text: 'Open'),
                      Tab(text: 'History'),
                      Tab(text: 'Stats'),
                    ],
                  ),
                  Expanded(
                    child: TabBarView(
                      children: [
                        _OpenPositionsTab(view: view),
                        _HistoryTab(view: view),
                        _StatsTab(performance: view.performance),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _AccountHeader extends ConsumerWidget {
  const _AccountHeader({required this.account, required this.openPositions});
  final PaperAccount account;
  final int openPositions;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final pnl = account.realizedPnl + account.unrealizedPnl;
    final pnlColor = pnl >= 0 ? AppColors.profit : AppColors.loss;
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: AppColors.card,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: AppColors.accent.withValues(alpha: 0.3)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                const Text(
                  'Paper Equity',
                  style: TextStyle(
                    color: AppColors.muted,
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 1.1,
                  ),
                ),
                const Spacer(),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: AppColors.accent.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(6),
                    border: Border.all(color: AppColors.accent.withValues(alpha: 0.4)),
                  ),
                  child: const Text(
                    'SIMULATION',
                    style: TextStyle(
                      color: AppColors.accent,
                      fontSize: 10,
                      fontWeight: FontWeight.w800,
                      letterSpacing: 1,
                    ),
                  ),
                ),
                IconButton(
                  visualDensity: VisualDensity.compact,
                  icon: const Icon(Icons.restart_alt, color: AppColors.muted),
                  tooltip: 'Reset paper account',
                  onPressed: () => _confirmReset(context, ref),
                ),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              '${account.equity.toStringAsFixed(2)} ${account.quoteCurrency}',
              style: const TextStyle(
                color: AppColors.onBackground,
                fontSize: 26,
                fontWeight: FontWeight.w800,
              ),
            ),
            const SizedBox(height: 6),
            Row(
              children: [
                Text(
                  '${pnl >= 0 ? '+' : ''}${pnl.toStringAsFixed(2)} P&L',
                  style: TextStyle(color: pnlColor, fontWeight: FontWeight.w700),
                ),
                const SizedBox(width: 12),
                Text(
                  '${account.returnPct >= 0 ? '+' : ''}${account.returnPct.toStringAsFixed(2)}%',
                  style: TextStyle(color: pnlColor, fontWeight: FontWeight.w700),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                _Kpi(label: 'Available', value: account.availableBalance.toStringAsFixed(2)),
                _Kpi(label: 'Invested', value: account.invested.toStringAsFixed(2)),
                _Kpi(label: 'Trades', value: '${account.totalTrades}'),
                _Kpi(label: 'Win %', value: account.winRatePct.toStringAsFixed(1)),
              ],
            ),
            const SizedBox(height: 10),
            const Divider(height: 1, color: Color(0xFF2A2A2A)),
            const SizedBox(height: 4),
            Row(
              children: [
                const Text('Initial Capital',
                    style: TextStyle(color: AppColors.muted, fontSize: 11)),
                const SizedBox(width: 8),
                Text(
                  '${account.initialBalance.toStringAsFixed(2)} ${account.quoteCurrency}',
                  style: const TextStyle(
                      color: AppColors.onBackground,
                      fontWeight: FontWeight.w700,
                      fontSize: 13),
                ),
                const Spacer(),
                TextButton.icon(
                  key: const Key('paper_capital_edit'),
                  onPressed: () => _editCapital(context, ref),
                  icon: const Icon(Icons.edit, size: 16),
                  label: const Text('Edit'),
                  style: TextButton.styleFrom(
                    foregroundColor: AppColors.accent,
                    visualDensity: VisualDensity.compact,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _confirmReset(BuildContext context, WidgetRef ref) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (dialogCtx) => AlertDialog(
        backgroundColor: AppColors.card,
        title: const Text('Reset paper account?'),
        content: const Text(
          'This closes all open paper positions and restores your initial balance. '
          'Trade history is preserved.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogCtx, false), child: const Text('Cancel')),
          TextButton(
            onPressed: () => Navigator.pop(dialogCtx, true),
            child: const Text('RESET', style: TextStyle(color: AppColors.loss)),
          ),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ref.read(paperTradingControllerProvider.notifier).resetAccount();
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Paper account reset')),
      );
    } catch (e) {
      debugPrint('paper account reset failed: $e');
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Unable to reset the paper account. Please try again.'),
          backgroundColor: AppColors.loss,
        ),
      );
    }
  }

  /// True when the account already has trading activity, so the backend will (by
  /// design) refuse a direct initial-capital change.
  bool get _hasTradingActivity =>
      account.totalTrades > 0 ||
      openPositions > 0 ||
      account.realizedPnl != 0 ||
      account.invested > 0;

  static const _capitalLockedMessage =
      "Initial capital can't be changed after trading activity.\n\n"
      'Reset the paper account first to start with a new capital amount.';

  static bool _isCapitalValidationError(Object error) =>
      error is DioException && error.response?.statusCode == 400;

  Future<void> _editCapital(BuildContext context, WidgetRef ref) async {
    // Backend validation is authoritative; this is only an early, friendly guard.
    if (_hasTradingActivity) {
      await _showCapitalLockedDialog(context, ref);
      return;
    }

    final entered = await showDialog<String>(
      context: context,
      builder: (_) => _CapitalDialog(
        initial: account.initialBalance,
        currency: account.quoteCurrency,
      ),
    );
    if (entered == null) return;

    final value = double.tryParse(entered);
    if (value == null || !value.isFinite || value <= 0) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Enter a positive amount'), backgroundColor: AppColors.loss),
      );
      return;
    }
    try {
      await ref.read(paperTradingControllerProvider.notifier).updateInitialCapital(value);
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Initial capital updated')),
      );
    } catch (e) {
      // Keep the raw cause in debug logs only — never render it to the user.
      debugPrint('paper capital update failed: $e');
      if (!context.mounted) return;
      if (_isCapitalValidationError(e)) {
        // Known backend rule: trading activity already exists.
        await _showCapitalLockedDialog(context, ref);
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Unable to update initial capital. Please try again.'),
            backgroundColor: AppColors.loss,
          ),
        );
      }
    }
  }

  Future<void> _showCapitalLockedDialog(BuildContext context, WidgetRef ref) async {
    final reset = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.card,
        title: const Text("Can't change initial capital"),
        content: const Text(_capitalLockedMessage),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Reset Account'),
          ),
        ],
      ),
    );
    if (reset != true) return;
    try {
      await ref.read(paperTradingControllerProvider.notifier).resetAccount();
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Paper account reset — set your new initial capital')),
      );
    } catch (e) {
      debugPrint('paper account reset failed: $e');
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Unable to reset the paper account. Please try again.'),
          backgroundColor: AppColors.loss,
        ),
      );
    }
  }
}

/// Owns its [TextEditingController] so disposal happens only after the dialog
/// route is fully removed (not during its exit transition).
class _CapitalDialog extends StatefulWidget {
  const _CapitalDialog({required this.initial, required this.currency});

  final double initial;
  final String currency;

  @override
  State<_CapitalDialog> createState() => _CapitalDialogState();
}

class _CapitalDialogState extends State<_CapitalDialog> {
  late final TextEditingController _controller =
      TextEditingController(text: widget.initial.toStringAsFixed(2));

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: AppColors.card,
      title: const Text('Initial Capital'),
      content: TextField(
        controller: _controller,
        autofocus: true,
        keyboardType: const TextInputType.numberWithOptions(decimal: true),
        decoration: InputDecoration(
          hintText: 'e.g. 100.00',
          suffixText: widget.currency,
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
        TextButton(
          onPressed: () => Navigator.pop(context, _controller.text.trim()),
          child: const Text('Save'),
        ),
      ],
    );
  }
}

class _Kpi extends StatelessWidget {
  const _Kpi({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: const TextStyle(color: AppColors.muted, fontSize: 11)),
          const SizedBox(height: 2),
          Text(value,
              style: const TextStyle(
                  color: AppColors.onBackground,
                  fontSize: 14,
                  fontWeight: FontWeight.w700)),
        ],
      ),
    );
  }
}

class _OpenPositionsTab extends ConsumerWidget {
  const _OpenPositionsTab({required this.view});
  final PaperTradingViewData view;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (view.openPositions.isEmpty) {
      return const _EmptyState(
        icon: Icons.hourglass_empty,
        message: 'No open paper positions.\nA new signal will open one automatically.',
      );
    }
    return ListView.separated(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
      itemCount: view.openPositions.length,
      separatorBuilder: (_, _) => const SizedBox(height: 10),
      itemBuilder: (context, i) => _PositionCard(
        position: view.openPositions[i],
        onClose: () => _confirmClose(context, ref, view.openPositions[i]),
      ),
    );
  }

  Future<void> _confirmClose(BuildContext context, WidgetRef ref, PaperPosition p) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (dialogCtx) => AlertDialog(
        backgroundColor: AppColors.card,
        title: Text('Close ${p.symbol}?'),
        content: const Text('The position will close at the current market price.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogCtx, false), child: const Text('Cancel')),
          TextButton(
            onPressed: () => Navigator.pop(dialogCtx, true),
            child: const Text('CLOSE', style: TextStyle(color: AppColors.loss)),
          ),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ref.read(paperTradingControllerProvider.notifier).closePosition(p.id);
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('${p.symbol} closed')),
      );
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed: $e'), backgroundColor: AppColors.loss),
      );
    }
  }
}

class _HistoryTab extends StatelessWidget {
  const _HistoryTab({required this.view});
  final PaperTradingViewData view;

  @override
  Widget build(BuildContext context) {
    if (view.history.isEmpty) {
      return const _EmptyState(
        icon: Icons.history,
        message: 'No trades yet.\nHistory appears once positions close.',
      );
    }
    return ListView.separated(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
      itemCount: view.history.length,
      separatorBuilder: (_, _) => const SizedBox(height: 10),
      itemBuilder: (context, i) => _PositionCard(position: view.history[i]),
    );
  }
}

class _StatsTab extends StatelessWidget {
  const _StatsTab({required this.performance});
  final PaperPerformance performance;

  @override
  Widget build(BuildContext context) {
    final pnl = performance.totalNetPnl;
    final pnlColor = pnl >= 0 ? AppColors.profit : AppColors.loss;
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
      children: [
        _StatCard(rows: [
          _StatRow(label: 'Total trades', value: '${performance.totalTrades}'),
          _StatRow(label: 'Winning', value: '${performance.winningTrades}'),
          _StatRow(label: 'Losing', value: '${performance.losingTrades}'),
          _StatRow(label: 'Win rate', value: '${performance.winRatePct.toStringAsFixed(1)}%'),
        ]),
        const SizedBox(height: 10),
        _StatCard(rows: [
          _StatRow(
              label: 'Net P&L',
              value: '${pnl >= 0 ? '+' : ''}${pnl.toStringAsFixed(2)}',
              color: pnlColor),
          _StatRow(label: 'Average win', value: performance.averageWin.toStringAsFixed(2)),
          _StatRow(label: 'Average loss', value: performance.averageLoss.toStringAsFixed(2)),
          _StatRow(label: 'Profit factor', value: performance.profitFactor.toStringAsFixed(2)),
        ]),
        const SizedBox(height: 10),
        _StatCard(rows: [
          _StatRow(label: 'Best trade', value: performance.bestTradePnl.toStringAsFixed(2)),
          _StatRow(label: 'Worst trade', value: performance.worstTradePnl.toStringAsFixed(2)),
          _StatRow(label: 'Total fees', value: performance.totalFees.toStringAsFixed(2)),
          _StatRow(label: 'Return', value: '${performance.returnPct.toStringAsFixed(2)}%'),
        ]),
      ],
    );
  }
}

class _StatCard extends StatelessWidget {
  const _StatCard({required this.rows});
  final List<_StatRow> rows;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(children: rows),
    );
  }
}

class _StatRow extends StatelessWidget {
  const _StatRow({required this.label, required this.value, this.color});
  final String label;
  final String value;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Text(label, style: const TextStyle(color: AppColors.muted, fontSize: 13)),
          const Spacer(),
          Text(value,
              style: TextStyle(
                color: color ?? AppColors.onCard,
                fontWeight: FontWeight.w700,
                fontSize: 14,
              )),
        ],
      ),
    );
  }
}

class _PositionCard extends ConsumerWidget {
  const _PositionCard({required this.position, this.onClose});
  final PaperPosition position;
  final VoidCallback? onClose;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isOpen = position.status == PaperPositionStatus.open;
    // Live price comes from the shared market stream (no per-position socket, no polling).
    //
    // Resolved on the position's own market. The Markets browser shows one market at a time, so
    // looking a position up there would price a futures position off whatever market the user
    // happens to be viewing. The shared book keeps both markets live, which a mixed spot/futures
    // portfolio needs. When no quote is available the backend's price is used: that value was
    // resolved on this position's own market server-side.
    final livePrice = ref.watch(
      marketPricesControllerProvider.select(
        (async) => async.value?.priceFor(position.effectiveTradingMode, position.symbol),
      ),
    );
    final liveConnected = ref.watch(
      marketPricesControllerProvider.select(
        (async) => async.value?.isConnected(position.effectiveTradingMode) ?? false,
      ),
    );
    final current = livePrice ?? position.currentPrice;
    final entry = position.entryPrice;
    final qty = position.quantity;
    final double unrealized = (current == null)
        ? position.unrealizedPnl
        : (position.side == PaperPositionSide.long ? current - entry : entry - current) * qty;
    final pnl = isOpen ? unrealized : position.realizedPnl;
    final pnlPct = !isOpen
        ? 0.0
        : (position.notional != null && position.notional != 0
            ? (unrealized / position.notional!) * 100
            : position.unrealizedPnlPct);
    final pnlColor = pnl >= 0 ? AppColors.profit : AppColors.loss;
    final marketType = (position.marketType ?? '—').toUpperCase();
    final isFutures = marketType == 'FUTURES';

    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Text(
                position.symbol,
                style: const TextStyle(
                  color: AppColors.onBackground,
                  fontWeight: FontWeight.w800,
                  fontSize: 15,
                ),
              ),
              const SizedBox(width: 8),
              _Badge(text: marketType, color: isFutures ? AppColors.loss : AppColors.accent),
              const SizedBox(width: 6),
              _Badge(
                text: position.side == PaperPositionSide.long ? 'LONG' : 'SHORT',
                color: AppColors.accent,
              ),
              const Spacer(),
              _Badge(
                text: isOpen ? 'OPEN' : 'CLOSED',
                color: isOpen ? AppColors.profit : AppColors.muted,
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            'Strategy: ${position.strategyName ?? '—'}',
            style: const TextStyle(color: AppColors.muted, fontSize: 11),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              _Meta(label: 'Opened', value: _openedLabel(position.openedAt)),
              if (isOpen)
                _Meta(
                  label: 'Price Feed',
                  value: liveConnected ? '● LIVE' : '○ OFFLINE',
                  valueColor: liveConnected ? AppColors.profit : AppColors.muted,
                ),
            ],
          ),
          const SizedBox(height: 4),
          Row(
            children: [
              _Meta(label: 'Entry', value: entry.toStringAsFixed(4)),
              _Meta(
                label: isOpen ? 'Current' : 'Exit',
                value: (isOpen ? current : position.exitPrice)?.toStringAsFixed(4) ?? '—',
              ),
              _Meta(label: 'Qty', value: qty.toStringAsFixed(4)),
            ],
          ),
          const SizedBox(height: 4),
          Row(
            children: [
              _Meta(label: 'SL', value: position.stopLoss?.toStringAsFixed(4) ?? '—'),
              _Meta(label: 'TP1', value: position.takeProfit1?.toStringAsFixed(4) ?? '—'),
              _Meta(
                label: isOpen ? 'Notional' : 'Reason',
                value: isOpen
                    ? position.notional?.toStringAsFixed(2) ?? '—'
                    : _reasonLabel(position.closeReason),
              ),
            ],
          ),
          if (isOpen) ...[
            const SizedBox(height: 4),
            Row(
              children: [
                _Meta(label: 'TP2', value: position.takeProfit2?.toStringAsFixed(4) ?? '—'),
                _Meta(label: 'TP3', value: position.takeProfit3?.toStringAsFixed(4) ?? '—'),
                _Meta(
                  label: 'Unrealized PnL',
                  value: '${pnl >= 0 ? '+' : ''}${pnl.toStringAsFixed(2)}'
                      ' (${pnlPct >= 0 ? '+' : ''}${pnlPct.toStringAsFixed(2)}%)',
                  valueColor: pnlColor,
                ),
              ],
            ),
          ],
          if (isOpen && onClose != null) ...[
            const SizedBox(height: 8),
            Align(
              alignment: Alignment.centerRight,
              child: OutlinedButton(
                onPressed: onClose,
                style: OutlinedButton.styleFrom(
                  foregroundColor: AppColors.loss,
                  side: const BorderSide(color: AppColors.loss),
                  minimumSize: const Size(0, 32),
                ),
                child: const Text('CLOSE',
                    style: TextStyle(fontWeight: FontWeight.w800, letterSpacing: 0.8)),
              ),
            ),
          ],
        ],
      ),
    );
  }

  static String _openedLabel(DateTime? d) {
    if (d == null) return '—';
    final local = d.toLocal();
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    final h24 = local.hour;
    final h12 = h24 % 12 == 0 ? 12 : h24 % 12;
    final ampm = h24 < 12 ? 'AM' : 'PM';
    String two(int n) => n.toString().padLeft(2, '0');
    return '${local.day} ${months[local.month - 1]} ${local.year}, '
        '${two(h12)}:${two(local.minute)}:${two(local.second)} $ampm';
  }

  static String _reasonLabel(PaperCloseReason? r) {
    return switch (r) {
      PaperCloseReason.stopLoss => 'SL',
      PaperCloseReason.takeProfit => 'TP',
      PaperCloseReason.manual => 'MANUAL',
      PaperCloseReason.signalExpired => 'EXPIRED',
      PaperCloseReason.reset => 'RESET',
      PaperCloseReason.system => 'SYSTEM',
      null => '—',
    };
  }
}

class _Badge extends StatelessWidget {
  const _Badge({required this.text, required this.color});
  final String text;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(text,
          style: TextStyle(color: color, fontSize: 10, fontWeight: FontWeight.w800)),
    );
  }
}

class _Meta extends StatelessWidget {
  const _Meta({required this.label, required this.value, this.valueColor});
  final String label;
  final String value;
  final Color? valueColor;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: const TextStyle(color: AppColors.muted, fontSize: 10)),
          Text(value,
              style: TextStyle(
                  color: valueColor ?? AppColors.onCard,
                  fontWeight: FontWeight.w600,
                  fontSize: 12)),
        ],
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState({required this.icon, required this.message});
  final IconData icon;
  final String message;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: AppColors.muted, size: 48),
          const SizedBox(height: 12),
          Text(message,
              textAlign: TextAlign.center,
              style: const TextStyle(color: AppColors.muted, height: 1.5)),
        ],
      ),
    );
  }
}

class _ErrorPanel extends StatelessWidget {
  const _ErrorPanel({required this.message, required this.onRetry});
  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(message, style: const TextStyle(color: AppColors.muted)),
          const SizedBox(height: 12),
          TextButton(onPressed: onRetry, child: const Text('Retry')),
        ],
      ),
    );
  }
}
