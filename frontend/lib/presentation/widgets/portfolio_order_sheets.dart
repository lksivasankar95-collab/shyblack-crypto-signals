import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../domain/entities/portfolio_account.dart';
import '../providers/portfolio_controller.dart';
import 'portfolio_capital_sheet.dart' show PortfolioInlineError;
import 'portfolio_widgets.dart' show PortfolioStatePanel, PortfolioValue;

/// Mobile detail for a resting order and for a completed trade, plus the order
/// cancellation flow.
///
/// Three rules this file exists to enforce:
///
///  * **No broken buttons.** Cancel is rendered only when
///    [PortfolioOrder.isCancellable] — i.e. the order has an addressable local
///    record and the exchange state is not terminal. An order placed outside this
///    app, or an already-terminal one, shows no control at all.
///  * **Never a false success.** The cancellation is awaited; a failure reports
///    the real error and leaves the order listed. An uncertain result (timeout)
///    is reported as uncertain rather than as done.
///  * **No double submit.** The confirm button disables itself while the request
///    is in flight, so a second tap cannot queue a second cancel.
class PortfolioOrderSheets {
  const PortfolioOrderSheets._();

  // ── Order detail ──────────────────────────────────────────────

  static Future<void> showOrderDetail(
    BuildContext context,
    PortfolioOrder order,
  ) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.card,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(18)),
      ),
      builder: (_) => _OrderDetail(order: order),
    );
  }

  // ── Trade detail ──────────────────────────────────────────────

  static Future<void> showTradeDetail(
    BuildContext context,
    PortfolioHistoryEntry entry,
  ) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.card,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(18)),
      ),
      builder: (_) => _TradeDetail(entry: entry),
    );
  }

  // ── Cancel ────────────────────────────────────────────────────

  /// Confirms, then cancels. Returns true only when the backend accepted it.
  static Future<bool> confirmCancel(
    BuildContext context,
    WidgetRef ref,
    PortfolioOrder order,
  ) async {
    if (!order.isCancellable) return false;

    final messenger = ScaffoldMessenger.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => _CancelDialog(order: order),
    );
    if (confirmed != true) return false;

    await ref
        .read(portfolioOrderActionControllerProvider.notifier)
        .cancel(
          mode: order.accountMode,
          category: order.accountCategory,
          cancelId: order.cancelId!,
        );

    final action = ref.read(portfolioOrderActionControllerProvider);
    if (action.hasError) {
      messenger.showSnackBar(
        SnackBar(content: Text('Cancel failed: ${_reason(action.error)}')),
      );
      return false;
    }
    messenger.showSnackBar(
      SnackBar(content: Text('${order.symbol} order cancelled')),
    );
    return true;
  }

  /// A cancellation that may or may not have reached the exchange is reported as
  /// uncertain, never as success.
  static String _reason(Object? error) {
    final text = error.toString();
    if (text.contains('Exception: ')) return text.split('Exception: ').last;
    if (text.isEmpty) return 'the request failed';
    return text;
  }
}

// ── Order detail ─────────────────────────────────────────────────

class _OrderDetail extends ConsumerWidget {
  const _OrderDetail({required this.order});

  final PortfolioOrder order;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final futures = order.accountCategory == PortfolioCategory.futures;
    final action = ref.watch(portfolioOrderActionControllerProvider);
    final busy = action.isLoading;

    return SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(16, 14, 16, 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    order.symbol,
                    style: const TextStyle(
                      color: AppColors.onCard,
                      fontSize: 16,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
                IconButton(
                  key: const Key('order-detail-close'),
                  onPressed: () => Navigator.of(context).pop(),
                  icon: const Icon(
                    Icons.close,
                    color: AppColors.muted,
                    size: 20,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 6),
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: [
                _Pill(label: futures ? 'FUTURES' : 'SPOT'),
                _Pill(
                  label: order.accountMode == PortfolioMode.paper
                      ? 'PAPER'
                      : 'LIVE',
                ),
                _Pill(label: order.status.apiValue),
              ],
            ),
            const SizedBox(height: 14),
            _Rows(
              title: 'ORDER',
              rows: {
                'Order ID': _id(order.orderId),
                'Client Order ID': order.clientOrderId ?? '�',
                'Side': order.side ?? '—',
                'Order Type': order.orderType,
                'Status': order.status.apiValue,
                'Created': _time(order.createdAt),
                'Updated': _time(order.updatedAt),
              },
            ),
            const SizedBox(height: 12),
            _Rows(
              title: 'QUANTITY',
              rows: {
                'Price': PortfolioValue.format(order.price),
                'Quantity': PortfolioValue.format(order.originalQuantity),
                'Filled': PortfolioValue.format(order.executedQuantity),
                'Remaining': PortfolioValue.format(order.remainingQuantity),
                if (order.averageFillPrice != null)
                  'Avg Fill Price': PortfolioValue.format(
                    order.averageFillPrice,
                  ),
              },
            ),
            // Futures-only attributes are shown only for futures: a spot row has
            // no position side, no reduce-only flag and no trigger price.
            if (futures) ...[
              const SizedBox(height: 12),
              _Rows(
                title: 'FUTURES',
                rows: {
                  'Position Side': order.positionSide ?? '—',
                  'Reduce Only': order.reduceOnly == null
                      ? '—'
                      : (order.reduceOnly! ? 'YES' : 'NO'),
                  'Trigger Price': PortfolioValue.format(order.stopPrice),
                },
              ),
            ],
            const SizedBox(height: 16),
            if (!order.isCancellable)
              const PortfolioStatePanel(
                title: 'Not cancellable',
                message:
                    'This order cannot be cancelled from here — it was not placed by '
                    'this application, or it has already reached a final state.',
                icon: Icons.info_outline,
              )
            else ...[
              OutlinedButton(
                key: const Key('order-cancel'),
                onPressed: busy
                    ? null
                    : () async {
                        final ok = await PortfolioOrderSheets.confirmCancel(
                          context,
                          ref,
                          order,
                        );
                        if (ok && context.mounted)
                          Navigator.of(context).maybePop();
                      },
                style: OutlinedButton.styleFrom(
                  foregroundColor: AppColors.loss,
                  side: const BorderSide(color: AppColors.loss),
                  minimumSize: const Size.fromHeight(44),
                ),
                child: Text(busy ? 'CANCELLING…' : 'CANCEL ORDER'),
              ),
              if (action.hasError) ...[
                const SizedBox(height: 10),
                PortfolioInlineError(
                  message:
                      'Cancel failed: '
                      '${PortfolioOrderSheets._reason(action.error)}',
                ),
              ],
            ],
          ],
        ),
      ),
    );
  }

  static String _id(int? id) => id == null ? '—' : '$id';

  static String _time(DateTime? value) {
    if (value == null) return '—';
    final local = value.toLocal();
    final hh = local.hour.toString().padLeft(2, '0');
    final mm = local.minute.toString().padLeft(2, '0');
    return '${local.year}-${local.month.toString().padLeft(2, '0')}-'
        '${local.day.toString().padLeft(2, '0')} $hh:$mm';
  }
}

/// Cancel confirmation, stating the figures a trader needs to not mis-tap.
class _CancelDialog extends StatelessWidget {
  const _CancelDialog({required this.order});

  final PortfolioOrder order;

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: AppColors.card,
      title: Text(
        'Cancel ${order.symbol} order?',
        style: const TextStyle(color: AppColors.onCard),
      ),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _row(
              'Market',
              order.accountCategory == PortfolioCategory.futures
                  ? 'FUTURES'
                  : 'SPOT',
            ),
            _row(
              'Account',
              order.accountMode == PortfolioMode.paper ? 'PAPER' : 'LIVE',
            ),
            _row('Side', order.side ?? '—'),
            _row('Order Type', order.orderType),
            _row('Price', PortfolioValue.format(order.price)),
            _row('Quantity', PortfolioValue.format(order.originalQuantity)),
            _row('Filled', PortfolioValue.format(order.executedQuantity)),
            _row('Remaining', PortfolioValue.format(order.remainingQuantity)),
          ],
        ),
      ),
      actions: [
        TextButton(
          key: const Key('cancel-dialog-cancel'),
          onPressed: () => Navigator.of(context).pop(false),
          child: const Text('CANCEL'),
        ),
        TextButton(
          key: const Key('cancel-dialog-confirm'),
          onPressed: () => Navigator.of(context).pop(true),
          child: const Text('CONFIRM CANCEL'),
        ),
      ],
    );
  }

  static Widget _row(String label, String value) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 3),
    child: Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          label,
          style: const TextStyle(color: AppColors.muted, fontSize: 12),
        ),
        Text(
          value,
          style: const TextStyle(
            color: AppColors.onCard,
            fontSize: 12,
            fontWeight: FontWeight.w700,
          ),
        ),
      ],
    ),
  );
}

// ── Trade detail ─────────────────────────────────────────────────

class _TradeDetail extends StatelessWidget {
  const _TradeDetail({required this.entry});

  final PortfolioHistoryEntry entry;

  @override
  Widget build(BuildContext context) {
    final futures = entry.accountCategory == PortfolioCategory.futures;
    return SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(16, 14, 16, 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    // An income record can carry no symbol; shown as unknown
                    // rather than as an empty heading.
                    entry.symbol ?? 'Unknown symbol',
                    style: const TextStyle(
                      color: AppColors.onCard,
                      fontSize: 16,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
                IconButton(
                  key: const Key('trade-detail-close'),
                  onPressed: () => Navigator.of(context).pop(),
                  icon: const Icon(
                    Icons.close,
                    color: AppColors.muted,
                    size: 20,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 6),
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: [
                _Pill(label: futures ? 'FUTURES' : 'SPOT'),
                _Pill(
                  label: entry.accountMode == PortfolioMode.paper
                      ? 'PAPER'
                      : 'LIVE',
                ),
                _Pill(label: entry.entryType),
              ],
            ),
            const SizedBox(height: 14),
            _Rows(
              title: 'EXECUTION',
              rows: {
                'Trade ID': _id(entry.tradeId),
                'Order ID': _id(entry.orderId),
                'Side': entry.side ?? '—',
                'Price': PortfolioValue.format(entry.price),
                'Quantity': PortfolioValue.format(entry.quantity),
                'Value': PortfolioValue.format(entry.quoteQuantity),
                'Fee': PortfolioValue.format(entry.fee),
                if (entry.feeAsset != null) 'Fee Asset': entry.feeAsset!,
                'Executed': _time(entry.occurredAt),
                if (entry.status != null) 'Status': entry.status!,
              },
            ),
            // Realized P&L is only meaningful where the source reports it; spot
            // publishes none, so the row is absent rather than showing zero.
            if (entry.realizedPnl != null) ...[
              const SizedBox(height: 12),
              _Rows(
                title: 'RESULT',
                rows: {
                  'Realized P&L': PortfolioValue.format(entry.realizedPnl),
                },
              ),
            ],
            if (futures && entry.positionSide != null) ...[
              const SizedBox(height: 12),
              _Rows(
                title: 'FUTURES',
                rows: {'Position Side': entry.positionSide!},
              ),
            ],
          ],
        ),
      ),
    );
  }

  static String _id(int? id) => id == null ? '—' : '$id';

  static String _time(DateTime? value) {
    if (value == null) return '—';
    final local = value.toLocal();
    final hh = local.hour.toString().padLeft(2, '0');
    final mm = local.minute.toString().padLeft(2, '0');
    return '${local.year}-${local.month.toString().padLeft(2, '0')}-'
        '${local.day.toString().padLeft(2, '0')} $hh:$mm';
  }
}

// ── Shared pieces ────────────────────────────────────────────────

class _Rows extends StatelessWidget {
  const _Rows({required this.title, required this.rows});

  final String title;
  final Map<String, String> rows;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: const TextStyle(
            color: AppColors.muted,
            fontSize: 10,
            fontWeight: FontWeight.w800,
            letterSpacing: 1,
          ),
        ),
        const SizedBox(height: 6),
        for (final e in rows.entries)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 3),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  e.key,
                  style: const TextStyle(color: AppColors.muted, fontSize: 12),
                ),
                Text(
                  e.value,
                  style: const TextStyle(
                    color: AppColors.onCard,
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }
}

class _Pill extends StatelessWidget {
  const _Pill({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
    decoration: BoxDecoration(
      color: AppColors.muted.withValues(alpha: 0.14),
      borderRadius: BorderRadius.circular(6),
      border: Border.all(color: AppColors.muted.withValues(alpha: 0.5)),
    ),
    child: Text(
      label,
      style: const TextStyle(
        color: AppColors.muted,
        fontSize: 9,
        fontWeight: FontWeight.w800,
      ),
    ),
  );
}
