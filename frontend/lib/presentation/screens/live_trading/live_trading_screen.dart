import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../domain/entities/live_account.dart';
import '../../../domain/entities/live_order.dart';
import '../../providers/live_trading_controller.dart';

/// The user-facing live trading screen. Reachable from Settings → Live Trading.
/// Nothing here can auto-place real orders — that only happens after the user
/// (1) connects an exchange, (2) activates live trading with acknowledgement,
/// (3) has `liveTradingAllowed` set in their user settings, and the backend
/// autoExecute flag is on.
class LiveTradingScreen extends ConsumerWidget {
  const LiveTradingScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncView = ref.watch(liveTradingControllerProvider);
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        title: const Text('Live Trading'),
        centerTitle: false,
      ),
      body: SafeArea(
        child: asyncView.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, _) => _ErrorPanel(
            message: 'Could not load live trading',
            onRetry: () => ref.invalidate(liveTradingControllerProvider),
          ),
          data: (view) => RefreshIndicator(
            onRefresh: () =>
                ref.read(liveTradingControllerProvider.notifier).refresh(silent: false),
            child: view.account == null
                ? _NotConnected(onConnect: () => _connectFlow(context, ref))
                : _ConnectedBody(view: view),
          ),
        ),
      ),
    );
  }

  Future<void> _connectFlow(BuildContext context, WidgetRef ref) async {
    try {
      await ref
          .read(liveTradingControllerProvider.notifier)
          .connect(LiveExchange.binance);
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Exchange connected')),
      );
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed: $e'), backgroundColor: AppColors.loss),
      );
    }
  }
}

class _NotConnected extends StatelessWidget {
  const _NotConnected({required this.onConnect});
  final VoidCallback onConnect;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(24),
      children: [
        const SizedBox(height: 40),
        Icon(Icons.link_off, color: AppColors.muted, size: 56),
        const SizedBox(height: 16),
        const Text(
          'No live exchange connected',
          textAlign: TextAlign.center,
          style: TextStyle(
              color: AppColors.onBackground, fontSize: 18, fontWeight: FontWeight.w700),
        ),
        const SizedBox(height: 8),
        const Text(
          'Connect a Binance API credential from Settings → Connect Exchange Accounts, '
          'then tap the button below to link it to live trading.',
          textAlign: TextAlign.center,
          style: TextStyle(color: AppColors.muted, height: 1.4),
        ),
        const SizedBox(height: 24),
        FilledButton(
          onPressed: onConnect,
          child: const Text('Connect Binance'),
        ),
      ],
    );
  }
}

class _ConnectedBody extends ConsumerWidget {
  const _ConnectedBody({required this.view});
  final LiveTradingViewData view;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final account = view.account!;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _AccountCard(account: account, ref: ref),
        const SizedBox(height: 16),
        _SafetyPanel(account: account, ref: ref),
        const SizedBox(height: 16),
        _SectionHeader('OPEN ORDERS'),
        if (view.openOrders.isEmpty)
          _EmptyRow(text: 'No open live orders')
        else
          ...view.openOrders.map((o) => _OrderCard(order: o, onCancel: () => _confirmCancel(context, ref, o))),
        const SizedBox(height: 16),
        _SectionHeader('HISTORY'),
        if (view.history.isEmpty)
          _EmptyRow(text: 'No orders yet')
        else
          ...view.history.take(20).map((o) => _OrderCard(order: o, onCancel: null)),
        const SizedBox(height: 16),
        _SectionHeader('PERFORMANCE'),
        _PerformanceCard(view: view),
      ],
    );
  }

  Future<void> _confirmCancel(BuildContext context, WidgetRef ref, LiveOrder o) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (dialogCtx) => AlertDialog(
        backgroundColor: AppColors.card,
        title: Text('Cancel ${o.symbol} order?'),
        content: const Text('The exchange will be asked to cancel this order.'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(dialogCtx, false),
              child: const Text('Keep')),
          TextButton(
              onPressed: () => Navigator.pop(dialogCtx, true),
              child: const Text('CANCEL ORDER',
                  style: TextStyle(color: AppColors.loss))),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ref.read(liveTradingControllerProvider.notifier).cancelOrder(o.id);
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Cancel requested for ${o.symbol}')),
      );
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed: $e'), backgroundColor: AppColors.loss),
      );
    }
  }
}

class _AccountCard extends StatelessWidget {
  const _AccountCard({required this.account, required this.ref});
  final LiveAccount account;
  final WidgetRef ref;

  @override
  Widget build(BuildContext context) {
    final bal = account.cachedTotalBalance ?? 0;
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.loss.withValues(alpha: 0.4)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              const Text('Live Balance',
                  style: TextStyle(
                      color: AppColors.muted,
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 1.1)),
              const Spacer(),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: AppColors.loss.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(6),
                  border: Border.all(color: AppColors.loss.withValues(alpha: 0.4)),
                ),
                child: const Text('LIVE',
                    style: TextStyle(
                        color: AppColors.loss,
                        fontSize: 10,
                        fontWeight: FontWeight.w800,
                        letterSpacing: 1)),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text('${bal.toStringAsFixed(2)} ${account.quoteCurrency}',
              style: const TextStyle(
                  color: AppColors.onBackground,
                  fontSize: 26,
                  fontWeight: FontWeight.w800)),
          const SizedBox(height: 8),
          Row(children: [
            _kv('Available', (account.cachedAvailableBalance ?? 0).toStringAsFixed(2)),
            _kv('Max notional', (account.maxNotionalPerTrade ?? 0).toStringAsFixed(2)),
            _kv('Max active', '${account.maxActivePositions}'),
            _kv('Status', _statusLabel(account.connectionStatus)),
          ]),
        ],
      ),
    );
  }

  static String _statusLabel(LiveConnectionStatus s) => switch (s) {
        LiveConnectionStatus.connected => 'Connected',
        LiveConnectionStatus.connecting => 'Connecting…',
        LiveConnectionStatus.failed => 'Failed',
        LiveConnectionStatus.revoked => 'Revoked',
        LiveConnectionStatus.notConnected => 'Not connected',
      };

  Widget _kv(String k, String v) {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(k, style: const TextStyle(color: AppColors.muted, fontSize: 11)),
          Text(v,
              style: const TextStyle(
                  color: AppColors.onBackground,
                  fontWeight: FontWeight.w700,
                  fontSize: 13)),
        ],
      ),
    );
  }
}

class _SafetyPanel extends StatelessWidget {
  const _SafetyPanel({required this.account, required this.ref});
  final LiveAccount account;
  final WidgetRef ref;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(children: [
        _row(
          context,
          title: 'Live trading',
          subtitle: account.enabled ? 'Active — signals may place real orders' : 'Disabled',
          value: account.enabled,
          destructive: !account.enabled,
          onChanged: (want) => want ? _activate(context) : _deactivate(context),
        ),
        _row(
          context,
          title: 'Kill switch',
          subtitle: account.killSwitchActive
              ? 'ACTIVE — new entries are blocked'
              : 'Ready',
          value: account.killSwitchActive,
          destructive: !account.killSwitchActive,
          onChanged: (want) =>
              want ? _trigger(context) : _release(context),
        ),
      ]),
    );
  }

  Widget _row(BuildContext context,
      {required String title,
      required String subtitle,
      required bool value,
      required bool destructive,
      required Future<void> Function(bool) onChanged}) {
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title,
                  style: const TextStyle(
                      color: AppColors.onCard,
                      fontWeight: FontWeight.w700,
                      fontSize: 14)),
              const SizedBox(height: 2),
              Text(subtitle,
                  style: const TextStyle(color: AppColors.muted, fontSize: 12)),
            ],
          ),
        ),
        Switch(
          value: value,
          onChanged: (v) => onChanged(v),
          activeTrackColor: destructive ? AppColors.accent : AppColors.loss,
        ),
      ],
    );
  }

  Future<void> _activate(BuildContext context) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (dialogCtx) => AlertDialog(
        backgroundColor: AppColors.card,
        title: const Text('Enable live trading?'),
        content: const Text(
          'This account will begin placing REAL orders on the exchange when '
          'signals match your trading mode. You are responsible for any funds '
          'lost. Confirm you understand this before continuing.',
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(dialogCtx, false),
              child: const Text('Cancel')),
          TextButton(
              onPressed: () => Navigator.pop(dialogCtx, true),
              child: const Text('I UNDERSTAND — ENABLE',
                  style: TextStyle(color: AppColors.loss))),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ref
          .read(liveTradingControllerProvider.notifier)
          .activate(acknowledged: true);
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Live trading enabled')),
      );
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed: $e'), backgroundColor: AppColors.loss),
      );
    }
  }

  Future<void> _deactivate(BuildContext context) async {
    try {
      await ref.read(liveTradingControllerProvider.notifier).deactivate();
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Live trading disabled')),
      );
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed: $e'), backgroundColor: AppColors.loss),
      );
    }
  }

  Future<void> _trigger(BuildContext context) async {
    try {
      await ref.read(liveTradingControllerProvider.notifier).triggerKillSwitch();
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Kill switch active')),
      );
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed: $e'), backgroundColor: AppColors.loss),
      );
    }
  }

  Future<void> _release(BuildContext context) async {
    try {
      await ref.read(liveTradingControllerProvider.notifier).releaseKillSwitch();
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Kill switch released')),
      );
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed: $e'), backgroundColor: AppColors.loss),
      );
    }
  }
}

class _OrderCard extends StatelessWidget {
  const _OrderCard({required this.order, required this.onCancel});
  final LiveOrder order;
  final VoidCallback? onCancel;

  @override
  Widget build(BuildContext context) {
    final statusColor = switch (order.status) {
      LiveOrderStatus.filled => AppColors.profit,
      LiveOrderStatus.rejected ||
      LiveOrderStatus.failed ||
      LiveOrderStatus.expired =>
        AppColors.loss,
      _ => AppColors.accent,
    };
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(children: [
            Text(order.symbol,
                style: const TextStyle(
                    color: AppColors.onBackground,
                    fontWeight: FontWeight.w800,
                    fontSize: 15)),
            const SizedBox(width: 6),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
              decoration: BoxDecoration(
                color: AppColors.accent.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(6),
              ),
              child: Text(
                  '${order.purpose.name.toUpperCase()} • ${order.type.name.toUpperCase()}',
                  style: const TextStyle(
                      color: AppColors.accent,
                      fontSize: 10,
                      fontWeight: FontWeight.w800)),
            ),
            const Spacer(),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
              decoration: BoxDecoration(
                color: statusColor.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(6),
              ),
              child: Text(order.status.name.toUpperCase(),
                  style: TextStyle(
                      color: statusColor,
                      fontSize: 10,
                      fontWeight: FontWeight.w800)),
            ),
          ]),
          const SizedBox(height: 6),
          Row(children: [
            _kv('Qty', order.executedQuantity.toStringAsFixed(6)),
            _kv('Requested', order.requestedQuantity.toStringAsFixed(6)),
            _kv('Avg fill', (order.avgFillPrice ?? 0).toStringAsFixed(4)),
            _kv('Fees',
                '${(order.fees ?? 0).toStringAsFixed(6)} ${order.feeAsset ?? ''}'),
          ]),
          if (order.rejectReason != null && order.rejectReason!.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Text(order.rejectReason!,
                  style: const TextStyle(color: AppColors.loss, fontSize: 11)),
            ),
          if (onCancel != null && !order.isTerminal)
            Align(
              alignment: Alignment.centerRight,
              child: OutlinedButton(
                onPressed: onCancel,
                style: OutlinedButton.styleFrom(
                  foregroundColor: AppColors.loss,
                  side: const BorderSide(color: AppColors.loss),
                  minimumSize: const Size(0, 32),
                ),
                child: const Text('CANCEL',
                    style: TextStyle(
                        fontWeight: FontWeight.w800, letterSpacing: 0.8)),
              ),
            ),
        ],
      ),
    );
  }

  Widget _kv(String k, String v) {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(k, style: const TextStyle(color: AppColors.muted, fontSize: 10)),
          Text(v,
              style: const TextStyle(
                  color: AppColors.onCard,
                  fontWeight: FontWeight.w600,
                  fontSize: 12)),
        ],
      ),
    );
  }
}

class _PerformanceCard extends StatelessWidget {
  const _PerformanceCard({required this.view});
  final LiveTradingViewData view;

  @override
  Widget build(BuildContext context) {
    final p = view.performance;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
          color: AppColors.card, borderRadius: BorderRadius.circular(12)),
      child: Column(children: [
        _row('Total orders', '${p.totalOrders}'),
        _row('Filled entries', '${p.filledEntries}'),
        _row('Rejections', '${p.rejections}'),
        _row('Fees paid', p.totalFees.toStringAsFixed(6)),
        _row('Total notional', p.totalNotional.toStringAsFixed(2)),
      ]),
    );
  }

  Widget _row(String k, String v) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(children: [
          Text(k, style: const TextStyle(color: AppColors.muted, fontSize: 13)),
          const Spacer(),
          Text(v,
              style: const TextStyle(
                  color: AppColors.onCard,
                  fontWeight: FontWeight.w700,
                  fontSize: 14)),
        ]),
      );
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader(this.label);
  final String label;
  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 8, 4, 6),
      child: Text(label,
          style: const TextStyle(
              color: AppColors.muted,
              fontSize: 12,
              fontWeight: FontWeight.w800,
              letterSpacing: 1.1)),
    );
  }
}

class _EmptyRow extends StatelessWidget {
  const _EmptyRow({required this.text});
  final String text;
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      alignment: Alignment.center,
      decoration: BoxDecoration(
          color: AppColors.card, borderRadius: BorderRadius.circular(12)),
      child: Text(text, style: const TextStyle(color: AppColors.muted)),
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
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        Text(message, style: const TextStyle(color: AppColors.muted)),
        const SizedBox(height: 12),
        TextButton(onPressed: onRetry, child: const Text('Retry')),
      ]),
    );
  }
}
