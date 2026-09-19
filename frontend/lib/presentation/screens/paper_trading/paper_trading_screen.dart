import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../domain/entities/paper_account.dart';
import '../../../domain/entities/paper_performance.dart';
import '../../../domain/entities/paper_position.dart';
import '../../providers/paper_trading_controller.dart';

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
                  _AccountHeader(account: view.account),
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
  const _AccountHeader({required this.account});
  final PaperAccount account;

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
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Reset failed: $e'), backgroundColor: AppColors.loss),
      );
    }
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

class _PositionCard extends StatelessWidget {
  const _PositionCard({required this.position, this.onClose});
  final PaperPosition position;
  final VoidCallback? onClose;

  @override
  Widget build(BuildContext context) {
    final isOpen = position.status == PaperPositionStatus.open;
    final pnl = isOpen ? position.unrealizedPnl : position.realizedPnl;
    final pnlPct = isOpen ? position.unrealizedPnlPct : 0.0;
    final pnlColor = pnl >= 0 ? AppColors.profit : AppColors.loss;
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
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: AppColors.accent.withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  position.side == PaperPositionSide.long ? 'LONG' : 'SHORT',
                  style: const TextStyle(
                      color: AppColors.accent,
                      fontSize: 10,
                      fontWeight: FontWeight.w800),
                ),
              ),
              const Spacer(),
              Text(
                '${pnl >= 0 ? '+' : ''}${pnl.toStringAsFixed(2)}',
                style: TextStyle(color: pnlColor, fontWeight: FontWeight.w800),
              ),
              if (isOpen) ...[
                const SizedBox(width: 6),
                Text('(${pnlPct >= 0 ? '+' : ''}${pnlPct.toStringAsFixed(2)}%)',
                    style: TextStyle(color: pnlColor, fontSize: 12)),
              ],
            ],
          ),
          const SizedBox(height: 6),
          Row(
            children: [
              _Meta(label: 'Entry', value: position.entryPrice.toStringAsFixed(4)),
              _Meta(
                label: isOpen ? 'Current' : 'Exit',
                value: (isOpen ? position.currentPrice : position.exitPrice)
                        ?.toStringAsFixed(4) ??
                    '—',
              ),
              _Meta(label: 'Qty', value: position.quantity.toStringAsFixed(4)),
            ],
          ),
          const SizedBox(height: 4),
          Row(
            children: [
              _Meta(label: 'SL', value: position.stopLoss?.toStringAsFixed(4) ?? '—'),
              _Meta(label: 'TP', value: position.takeProfit1?.toStringAsFixed(4) ?? '—'),
              _Meta(
                label: isOpen ? 'Notional' : 'Reason',
                value: isOpen
                    ? position.notional?.toStringAsFixed(2) ?? '—'
                    : _reasonLabel(position.closeReason),
              ),
            ],
          ),
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

class _Meta extends StatelessWidget {
  const _Meta({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: const TextStyle(color: AppColors.muted, fontSize: 10)),
          Text(value,
              style: const TextStyle(color: AppColors.onCard, fontWeight: FontWeight.w600, fontSize: 12)),
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
