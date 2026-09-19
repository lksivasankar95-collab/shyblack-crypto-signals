import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../domain/entities/futures_account.dart';
import '../../../domain/entities/futures_order.dart';
import '../../../domain/entities/futures_position.dart';
import '../../providers/futures_trading_controller.dart';

/// USDT-M FUTURES trading screen. Kept separate from Live SPOT so the UI
/// never mixes wallet balances, margin, leverage, or LONG/SHORT semantics.
class FuturesTradingScreen extends ConsumerWidget {
  const FuturesTradingScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncView = ref.watch(futuresTradingControllerProvider);
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        title: const Text('Futures Trading'),
        centerTitle: false,
      ),
      body: SafeArea(
        child: asyncView.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, _) => _ErrorPanel(
            message: 'Could not load futures trading',
            onRetry: () => ref.invalidate(futuresTradingControllerProvider),
          ),
          data: (view) => RefreshIndicator(
            onRefresh: () => ref
                .read(futuresTradingControllerProvider.notifier).refresh(silent: false),
            child: view.account == null
                ? _NotConnected(onConnect: () => _connect(context, ref))
                : _ConnectedBody(view: view),
          ),
        ),
      ),
    );
  }

  Future<void> _connect(BuildContext context, WidgetRef ref) async {
    try {
      await ref.read(futuresTradingControllerProvider.notifier).connect('BINANCE');
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Futures account connected')),
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
        const Icon(Icons.link_off, color: AppColors.muted, size: 56),
        const SizedBox(height: 16),
        const Text('No futures account connected',
            textAlign: TextAlign.center,
            style: TextStyle(color: AppColors.onBackground,
                fontSize: 18, fontWeight: FontWeight.w700)),
        const SizedBox(height: 8),
        const Text(
          'Connect a Binance USDT-M FUTURES credential from Settings first. '
          'Then tap below to link and validate it.',
          textAlign: TextAlign.center,
          style: TextStyle(color: AppColors.muted, height: 1.4),
        ),
        const SizedBox(height: 24),
        FilledButton(onPressed: onConnect, child: const Text('Connect Binance Futures')),
      ],
    );
  }
}

class _ConnectedBody extends ConsumerWidget {
  const _ConnectedBody({required this.view});
  final FuturesTradingViewData view;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final account = view.account!;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _AccountCard(account: account),
        const SizedBox(height: 16),
        _SafetyPanel(account: account, ref: ref),
        const SizedBox(height: 16),
        _SectionHeader('OPEN POSITIONS'),
        if (view.openPositions.isEmpty)
          _Empty('No open futures positions')
        else
          ...view.openPositions.map((p) => _PositionCard(
                position: p,
                onClose: () => _confirmClose(context, ref, p),
              )),
        const SizedBox(height: 12),
        _SectionHeader('OPEN ORDERS'),
        if (view.openOrders.isEmpty)
          _Empty('No open orders')
        else
          ...view.openOrders.map((o) => _OrderCard(
                order: o,
                onCancel: o.isTerminal ? null : () => _confirmCancel(context, ref, o),
              )),
        const SizedBox(height: 12),
        _SectionHeader('ORDER HISTORY'),
        if (view.history.isEmpty)
          _Empty('No orders yet')
        else
          ...view.history.take(20).map((o) => _OrderCard(order: o, onCancel: null)),
      ],
    );
  }

  Future<void> _confirmClose(
      BuildContext context, WidgetRef ref, FuturesPosition p) async {
    final direction = p.positionSide == FuturesSide.long ? 'LONG' : 'SHORT';
    final ok = await showDialog<bool>(
      context: context,
      builder: (dialogCtx) => AlertDialog(
        backgroundColor: AppColors.card,
        title: Text('Close $direction ${p.symbol}?'),
        content: Text(
          'A REDUCE_ONLY '
          '${p.positionSide == FuturesSide.long ? "SELL" : "BUY"} '
          'market order will close the position. Any protective SL will '
          'be cancelled first.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogCtx, false),
              child: const Text('Cancel')),
          TextButton(onPressed: () => Navigator.pop(dialogCtx, true),
              child: const Text('CLOSE POSITION',
                  style: TextStyle(color: AppColors.loss))),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ref.read(futuresTradingControllerProvider.notifier).closePosition(p.id);
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Close submitted for ${p.symbol}')),
      );
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed: $e'), backgroundColor: AppColors.loss),
      );
    }
  }

  Future<void> _confirmCancel(
      BuildContext context, WidgetRef ref, FuturesOrder o) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (dialogCtx) => AlertDialog(
        backgroundColor: AppColors.card,
        title: Text('Cancel ${o.symbol} order?'),
        content: const Text('The exchange will be asked to cancel this order.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogCtx, false),
              child: const Text('Keep')),
          TextButton(onPressed: () => Navigator.pop(dialogCtx, true),
              child: const Text('CANCEL ORDER',
                  style: TextStyle(color: AppColors.loss))),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ref.read(futuresTradingControllerProvider.notifier).cancelOrder(o.id);
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
  const _AccountCard({required this.account});
  final FuturesAccount account;

  @override
  Widget build(BuildContext context) {
    final wallet = account.walletBalance ?? 0;
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
          Row(children: [
            const Text('Futures Wallet',
                style: TextStyle(color: AppColors.muted, fontSize: 12,
                    fontWeight: FontWeight.w700, letterSpacing: 1.1)),
            const Spacer(),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: AppColors.loss.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(6),
                border: Border.all(color: AppColors.loss.withValues(alpha: 0.4)),
              ),
              child: const Text('LIVE • FUTURES',
                  style: TextStyle(color: AppColors.loss, fontSize: 10,
                      fontWeight: FontWeight.w800, letterSpacing: 1)),
            ),
          ]),
          const SizedBox(height: 6),
          Text('${wallet.toStringAsFixed(2)} ${account.marginAsset}',
              style: const TextStyle(color: AppColors.onBackground,
                  fontSize: 26, fontWeight: FontWeight.w800)),
          const SizedBox(height: 8),
          Row(children: [
            _kv('Available', (account.availableBalance ?? 0).toStringAsFixed(2)),
            _kv('Margin used', (account.usedMargin ?? 0).toStringAsFixed(2)),
            _kv('Unreal P&L', (account.unrealizedPnl ?? 0).toStringAsFixed(2)),
            _kv('Leverage', 'up to ${account.maxLeverage}x'),
          ]),
          const SizedBox(height: 6),
          Row(children: [
            _kv('Margin mode', account.marginMode == FuturesMarginMode.isolated
                ? 'ISOLATED' : 'CROSS'),
            _kv('Position mode',
                account.positionMode == FuturesPositionMode.oneWay ? 'ONE-WAY' : 'HEDGE'),
            _kv('Funding', (account.totalFundingPaid ?? 0).toStringAsFixed(4)),
            _kv('Daily P&L', (account.realizedPnlToday ?? 0).toStringAsFixed(2)),
          ]),
        ],
      ),
    );
  }

  Widget _kv(String k, String v) => Expanded(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(k, style: const TextStyle(color: AppColors.muted, fontSize: 11)),
            Text(v, style: const TextStyle(color: AppColors.onBackground,
                    fontWeight: FontWeight.w700, fontSize: 13)),
          ],
        ),
      );
}

class _SafetyPanel extends StatelessWidget {
  const _SafetyPanel({required this.account, required this.ref});
  final FuturesAccount account;
  final WidgetRef ref;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: AppColors.card, borderRadius: BorderRadius.circular(12)),
      child: Column(children: [
        _row(context,
            title: 'Live futures trading',
            subtitle: account.enabled
                ? 'Active — signals may open real leveraged positions'
                : 'Disabled',
            value: account.enabled,
            onChanged: (want) => want ? _activate(context) : _deactivate(context)),
        _row(context,
            title: 'Kill switch',
            subtitle: account.killSwitchActive
                ? 'ACTIVE — new entries blocked'
                : 'Ready',
            value: account.killSwitchActive,
            onChanged: (want) => want ? _trigger(context) : _release(context)),
      ]),
    );
  }

  Widget _row(BuildContext context,
      {required String title, required String subtitle,
       required bool value, required Future<void> Function(bool) onChanged}) {
    return Row(children: [
      Expanded(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(title, style: const TextStyle(
              color: AppColors.onCard, fontWeight: FontWeight.w700, fontSize: 14)),
          const SizedBox(height: 2),
          Text(subtitle,
              style: const TextStyle(color: AppColors.muted, fontSize: 12)),
        ]),
      ),
      Switch(value: value, onChanged: (v) => onChanged(v),
          activeTrackColor: AppColors.loss),
    ]);
  }

  Future<void> _activate(BuildContext context) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (dialogCtx) => AlertDialog(
        backgroundColor: AppColors.card,
        title: const Text('Enable live FUTURES trading?'),
        content: const Text(
          'This account will begin placing REAL LEVERAGED orders on Binance '
          'USDT-M FUTURES for matching signals. You are responsible for any '
          'losses, which may exceed the initial margin. Confirm you understand.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogCtx, false),
              child: const Text('Cancel')),
          TextButton(onPressed: () => Navigator.pop(dialogCtx, true),
              child: const Text('I UNDERSTAND — ENABLE',
                  style: TextStyle(color: AppColors.loss))),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ref.read(futuresTradingControllerProvider.notifier)
          .activate(acknowledged: true);
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Futures trading enabled')),
      );
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed: $e'), backgroundColor: AppColors.loss),
      );
    }
  }

  Future<void> _deactivate(BuildContext context) async {
    await ref.read(futuresTradingControllerProvider.notifier).deactivate();
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Futures trading disabled')),
    );
  }

  Future<void> _trigger(BuildContext context) async {
    await ref.read(futuresTradingControllerProvider.notifier).triggerKillSwitch();
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Kill switch active')),
    );
  }

  Future<void> _release(BuildContext context) async {
    await ref.read(futuresTradingControllerProvider.notifier).releaseKillSwitch();
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Kill switch released')),
    );
  }
}

class _PositionCard extends StatelessWidget {
  const _PositionCard({required this.position, required this.onClose});
  final FuturesPosition position;
  final VoidCallback? onClose;

  @override
  Widget build(BuildContext context) {
    final direction = position.positionSide == FuturesSide.long ? 'LONG' : 'SHORT';
    final dirColor = position.positionSide == FuturesSide.long
        ? AppColors.profit : AppColors.loss;
    final protLabel = _protectionLabel(position.protectionStatus);
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: AppColors.card, borderRadius: BorderRadius.circular(12)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(children: [
            Text(position.symbol,
                style: const TextStyle(color: AppColors.onBackground,
                    fontWeight: FontWeight.w800, fontSize: 15)),
            const SizedBox(width: 6),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
              decoration: BoxDecoration(
                color: dirColor.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(6),
              ),
              child: Text('$direction ${position.leverage}x',
                  style: TextStyle(color: dirColor, fontSize: 10,
                      fontWeight: FontWeight.w800)),
            ),
            const Spacer(),
            Text('U-PnL ${position.unrealizedPnl.toStringAsFixed(2)}',
                style: TextStyle(
                    color: position.unrealizedPnl >= 0 ? AppColors.profit : AppColors.loss,
                    fontWeight: FontWeight.w800)),
          ]),
          const SizedBox(height: 6),
          Row(children: [
            _kv('Qty', position.quantity.toStringAsFixed(4)),
            _kv('Entry', (position.entryPrice ?? 0).toStringAsFixed(2)),
            _kv('SL', (position.stopLoss ?? 0).toStringAsFixed(2)),
            _kv('Liq', (position.liquidationPrice ?? 0).toStringAsFixed(2)),
          ]),
          const SizedBox(height: 4),
          Row(children: [
            _kv('Margin', (position.initialMargin ?? 0).toStringAsFixed(2)),
            _kv('Fees', position.tradingFees.toStringAsFixed(6)),
            _kv('Funding', position.fundingFees.toStringAsFixed(6)),
            _kv('Realized', position.realizedPnl.toStringAsFixed(2)),
          ]),
          if (protLabel != null) Padding(
            padding: const EdgeInsets.only(top: 4),
            child: Row(children: [
              Icon(
                position.protectionStatus == FuturesProtectionStatus.protected_
                    ? Icons.verified_user_outlined : Icons.warning_amber_rounded,
                size: 14,
                color: position.protectionStatus == FuturesProtectionStatus.protectionFailed
                    ? AppColors.loss
                    : position.protectionStatus == FuturesProtectionStatus.protected_
                        ? AppColors.profit : AppColors.accent,
              ),
              const SizedBox(width: 4),
              Text(protLabel,
                  style: TextStyle(
                      color: position.protectionStatus == FuturesProtectionStatus.protectionFailed
                          ? AppColors.loss
                          : position.protectionStatus == FuturesProtectionStatus.protected_
                              ? AppColors.profit : AppColors.accent,
                      fontSize: 11, fontWeight: FontWeight.w700)),
            ]),
          ),
          if (onClose != null)
            Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Align(
                alignment: Alignment.centerRight,
                child: OutlinedButton(
                  onPressed: onClose,
                  style: OutlinedButton.styleFrom(
                    foregroundColor: AppColors.loss,
                    side: const BorderSide(color: AppColors.loss),
                    minimumSize: const Size(0, 32),
                  ),
                  child: const Text('CLOSE POSITION',
                      style: TextStyle(
                          fontWeight: FontWeight.w800, letterSpacing: 0.8)),
                ),
              ),
            ),
        ],
      ),
    );
  }

  static String? _protectionLabel(FuturesProtectionStatus s) => switch (s) {
        FuturesProtectionStatus.pending => 'SL pending…',
        FuturesProtectionStatus.protected_ => 'Protected by SL',
        FuturesProtectionStatus.protectionFailed =>
          'PROTECTION FAILED — position is UNPROTECTED',
        FuturesProtectionStatus.notApplicable => null,
      };

  Widget _kv(String k, String v) => Expanded(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(k, style: const TextStyle(color: AppColors.muted, fontSize: 10)),
          Text(v, style: const TextStyle(
              color: AppColors.onCard, fontWeight: FontWeight.w600, fontSize: 12)),
        ]),
      );
}

class _OrderCard extends StatelessWidget {
  const _OrderCard({required this.order, required this.onCancel});
  final FuturesOrder order;
  final VoidCallback? onCancel;

  @override
  Widget build(BuildContext context) {
    final statusColor = switch (order.status) {
      FuturesOrderStatus.filled => AppColors.profit,
      FuturesOrderStatus.rejected ||
      FuturesOrderStatus.failed ||
      FuturesOrderStatus.expired =>
        AppColors.loss,
      _ => AppColors.accent,
    };
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: AppColors.card, borderRadius: BorderRadius.circular(12)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(children: [
            Text(order.symbol,
                style: const TextStyle(color: AppColors.onBackground,
                    fontWeight: FontWeight.w800, fontSize: 15)),
            const SizedBox(width: 6),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
              decoration: BoxDecoration(
                color: AppColors.accent.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(6),
              ),
              child: Text(
                  '${order.purpose.name.toUpperCase()} • '
                  '${order.type.name.toUpperCase()}'
                  '${order.reduceOnly ? " • RO" : ""}',
                  style: const TextStyle(color: AppColors.accent, fontSize: 10,
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
                  style: TextStyle(color: statusColor, fontSize: 10,
                      fontWeight: FontWeight.w800)),
            ),
          ]),
          const SizedBox(height: 6),
          Row(children: [
            _kv('Qty', order.executedQuantity.toStringAsFixed(4)),
            _kv('Requested', order.requestedQuantity.toStringAsFixed(4)),
            _kv('Avg fill', (order.avgFillPrice ?? 0).toStringAsFixed(2)),
            _kv('Lev', '${order.leverage}x'),
          ]),
          if (order.rejectReason != null && order.rejectReason!.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Text(order.rejectReason!,
                  style: const TextStyle(color: AppColors.loss, fontSize: 11)),
            ),
          if (onCancel != null && !order.isTerminal)
            Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Align(
                alignment: Alignment.centerRight,
                child: OutlinedButton(
                  onPressed: onCancel,
                  style: OutlinedButton.styleFrom(
                    foregroundColor: AppColors.muted,
                    side: const BorderSide(color: AppColors.muted),
                    minimumSize: const Size(0, 32),
                  ),
                  child: const Text('CANCEL',
                      style: TextStyle(
                          fontWeight: FontWeight.w800, letterSpacing: 0.8)),
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _kv(String k, String v) => Expanded(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(k, style: const TextStyle(color: AppColors.muted, fontSize: 10)),
          Text(v, style: const TextStyle(
              color: AppColors.onCard, fontWeight: FontWeight.w600, fontSize: 12)),
        ]),
      );
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader(this.label);
  final String label;
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.fromLTRB(4, 8, 4, 6),
        child: Text(label, style: const TextStyle(
            color: AppColors.muted, fontSize: 12,
            fontWeight: FontWeight.w800, letterSpacing: 1.1)),
      );
}

class _Empty extends StatelessWidget {
  const _Empty(this.text);
  final String text;
  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(14),
        alignment: Alignment.center,
        decoration: BoxDecoration(
            color: AppColors.card, borderRadius: BorderRadius.circular(12)),
        child: Text(text, style: const TextStyle(color: AppColors.muted)),
      );
}

class _ErrorPanel extends StatelessWidget {
  const _ErrorPanel({required this.message, required this.onRetry});
  final String message;
  final VoidCallback onRetry;
  @override
  Widget build(BuildContext context) => Center(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Text(message, style: const TextStyle(color: AppColors.muted)),
          const SizedBox(height: 12),
          TextButton(onPressed: onRetry, child: const Text('Retry')),
        ]),
      );
}
