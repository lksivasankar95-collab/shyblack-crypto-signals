import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../domain/repositories/portfolio_repository.dart';
import '../providers/portfolio_controller.dart';

/// Paper-account capital management.
///
/// Opened from the single management icon beside the PAPER ACCOUNT badge. That
/// icon is rendered **only** while the account mode resolved from Settings is
/// PAPER, so simulated-funds controls are unreachable on a live screen.
///
/// Nothing here writes the account mode, and nothing here can reach a live
/// balance: [requirePaperMode] rejects the action server-side too.
class PortfolioCapitalSheet extends ConsumerWidget {
  const PortfolioCapitalSheet({super.key});

  static Future<void> show(BuildContext context) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.card,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(18)),
      ),
      builder: (_) => const PortfolioCapitalSheet(),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final history = ref.watch(portfolioCapitalHistoryProvider);
    final action = ref.watch(portfolioCapitalControllerProvider);

    return SafeArea(
      child: Padding(
        padding: EdgeInsets.only(
          left: 16,
          right: 16,
          top: 14,
          bottom: MediaQuery.of(context).viewInsets.bottom + 16,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                const Expanded(
                  child: Text(
                    'Paper Account',
                    style: TextStyle(
                      color: AppColors.onCard,
                      fontSize: 15,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
                IconButton(
                  key: const Key('capital-sheet-close'),
                  onPressed: () => Navigator.of(context).pop(),
                  icon: const Icon(
                    Icons.close,
                    color: AppColors.muted,
                    size: 20,
                  ),
                  tooltip: 'Cancel',
                ),
              ],
            ),
            const SizedBox(height: 4),
            const Text(
              'Simulated funds only. These movements cannot affect a live account.',
              style: TextStyle(color: AppColors.muted, fontSize: 11),
            ),
            const SizedBox(height: 14),

            // A failed mutation is shown as a failure, never silently swallowed
            // and never rendered as a zero balance.
            if (action.hasError)
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: PortfolioInlineError(
                  key: const Key('capital-action-error'),
                  message: _describe(action.error),
                ),
              ),

            _ActionButton(
              buttonKey: const Key('capital-add'),
              label: '+ Add Capital',
              icon: Icons.add,
              enabled: !action.isLoading,
              onTap: () => _prompt(
                context,
                ref,
                title: 'Add Capital',
                confirmLabel: 'ADD',
                add: true,
              ),
            ),
            const SizedBox(height: 8),
            _ActionButton(
              buttonKey: const Key('capital-reduce'),
              label: '- Reduce Capital',
              icon: Icons.remove,
              enabled: !action.isLoading,
              onTap: () => _prompt(
                context,
                ref,
                title: 'Reduce Capital',
                confirmLabel: 'REDUCE',
                add: false,
              ),
            ),
            const SizedBox(height: 8),
            _ActionButton(
              buttonKey: const Key('capital-reset'),
              label: 'Reset Paper Account',
              icon: Icons.restart_alt,
              enabled: !action.isLoading,
              destructive: true,
              onTap: () => _confirmReset(context, ref),
            ),

            const SizedBox(height: 16),
            const _SheetLabel('CAPITAL HISTORY'),
            const SizedBox(height: 8),
            Flexible(
              child: history.when(
                loading: () => const Padding(
                  padding: EdgeInsets.symmetric(vertical: 18),
                  child: Center(
                    child: SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    ),
                  ),
                ),
                error: (error, _) => PortfolioInlineError(
                  message:
                      'Capital history could not be loaded. ${_describe(error)}',
                ),
                data: (events) => events.isEmpty
                    ? const _EmptyNote('No capital movements yet.')
                    : ListView.separated(
                        shrinkWrap: true,
                        itemCount: events.length,
                        separatorBuilder: (_, _) =>
                            const Divider(color: Color(0xFF2A2A2A), height: 1),
                        itemBuilder: (_, i) => _CapitalRow(event: events[i]),
                      ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _prompt(
    BuildContext context,
    WidgetRef ref, {
    required String title,
    required String confirmLabel,
    required bool add,
  }) async {
    final amountCtrl = TextEditingController();
    final reasonCtrl = TextEditingController();
    final formKey = GlobalKey<FormState>();

    final submitted = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: AppColors.card,
        title: Text(title, style: const TextStyle(color: AppColors.onCard)),
        content: Form(
          key: formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextFormField(
                key: const Key('capital-amount'),
                controller: amountCtrl,
                autofocus: true,
                keyboardType: const TextInputType.numberWithOptions(
                  decimal: true,
                ),
                style: const TextStyle(color: AppColors.onCard),
                decoration: const InputDecoration(
                  labelText: 'Amount',
                  labelStyle: TextStyle(color: AppColors.muted),
                  enabledBorder: OutlineInputBorder(),
                ),
                validator: (raw) {
                  final value = double.tryParse((raw ?? '').trim());
                  if (value == null) return 'Enter a valid number';
                  if (value <= 0) return 'Amount must be greater than zero';
                  return null;
                },
              ),
              if (add) ...[
                const SizedBox(height: 10),
                Wrap(
                  spacing: 8,
                  children: [
                    for (final quick in const [100.0, 500.0, 1000.0, 5000.0])
                      ActionChip(
                        label: Text('+${quick.toStringAsFixed(0)}'),
                        labelStyle: const TextStyle(
                          color: AppColors.muted,
                          fontSize: 11,
                        ),
                        onPressed: () =>
                            amountCtrl.text = quick.toStringAsFixed(0),
                      ),
                  ],
                ),
              ],
              const SizedBox(height: 10),
              TextFormField(
                key: const Key('capital-reason'),
                controller: reasonCtrl,
                style: const TextStyle(color: AppColors.onCard),
                decoration: const InputDecoration(
                  labelText: 'Reason (optional)',
                  labelStyle: TextStyle(color: AppColors.muted),
                  enabledBorder: OutlineInputBorder(),
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('CANCEL'),
          ),
          TextButton(
            key: Key('capital-confirm-$confirmLabel'),
            onPressed: () {
              if (formKey.currentState?.validate() != true) return;
              Navigator.of(dialogContext).pop(true);
            },
            child: Text(confirmLabel),
          ),
        ],
      ),
    );

    if (submitted != true) return;
    final amount = double.parse(amountCtrl.text.trim());
    final reason = reasonCtrl.text.trim();

    final controller = ref.read(portfolioCapitalControllerProvider.notifier);
    if (add) {
      await controller.add(amount, reason: reason.isEmpty ? null : reason);
    } else {
      await controller.reduce(amount, reason: reason.isEmpty ? null : reason);
    }
  }

  Future<void> _confirmReset(BuildContext context, WidgetRef ref) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: AppColors.card,
        title: const Text(
          'Reset paper account?',
          style: TextStyle(color: AppColors.onCard),
        ),
        content: const Text(
          'This will reset your paper account. Open positions will be closed and '
          'capital returned to the starting balance. This action cannot be undone. '
          'It cannot affect a live account.',
          style: TextStyle(color: AppColors.muted, fontSize: 12),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('CANCEL'),
          ),
          TextButton(
            key: const Key('capital-reset-confirm'),
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('RESET'),
          ),
        ],
      ),
    );
    // Cancel is the default: an unconfirmed destructive action must do nothing.
    if (confirmed != true) return;
    await ref.read(portfolioCapitalControllerProvider.notifier).reset();
  }

  /// Surfaces a validation / transport failure without inventing a figure.
  static String _describe(Object? error) {
    final text = error?.toString() ?? '';
    if (text.contains('only ')) return text;
    return 'The request could not be completed. Please try again.';
  }
}

class _CapitalRow extends StatelessWidget {
  const _CapitalRow({required this.event});

  final PaperCapitalEvent event;

  @override
  Widget build(BuildContext context) {
    final positive = event.amount >= 0;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 58,
            child: Text(
              event.type.label,
              style: TextStyle(
                color: positive ? AppColors.profit : AppColors.loss,
                fontSize: 10,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '${positive ? '+' : ''}${event.amount.toStringAsFixed(2)}',
                  style: TextStyle(
                    color: positive ? AppColors.profit : AppColors.loss,
                    fontSize: 13,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                Text(
                  '${event.previousBalance.toStringAsFixed(2)}  ->  '
                  '${event.newBalance.toStringAsFixed(2)}',
                  style: const TextStyle(color: AppColors.muted, fontSize: 10),
                ),
                if (event.reason != null && event.reason!.isNotEmpty)
                  Text(
                    event.reason!,
                    style: const TextStyle(
                      color: AppColors.muted,
                      fontSize: 10,
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _ActionButton extends StatelessWidget {
  const _ActionButton({
    required this.buttonKey,
    required this.label,
    required this.icon,
    required this.enabled,
    required this.onTap,
    this.destructive = false,
  });

  final Key buttonKey;
  final String label;
  final IconData icon;
  final bool enabled;
  final VoidCallback onTap;
  final bool destructive;

  @override
  Widget build(BuildContext context) {
    final color = destructive ? AppColors.loss : AppColors.accent;
    return OutlinedButton.icon(
      key: buttonKey,
      onPressed: enabled ? onTap : null,
      icon: Icon(icon, size: 16, color: enabled ? color : AppColors.muted),
      label: Text(
        label,
        style: TextStyle(
          color: enabled ? color : AppColors.muted,
          fontSize: 12,
          fontWeight: FontWeight.w700,
        ),
      ),
      style: OutlinedButton.styleFrom(
        foregroundColor: color,
        side: BorderSide(color: enabled ? color : AppColors.muted),
        minimumSize: const Size.fromHeight(44),
      ),
    );
  }
}

class _SheetLabel extends StatelessWidget {
  const _SheetLabel(this.text);

  final String text;

  @override
  Widget build(BuildContext context) => Text(
    text,
    style: const TextStyle(
      color: AppColors.muted,
      fontSize: 10,
      fontWeight: FontWeight.w800,
      letterSpacing: 1,
    ),
  );
}

class _EmptyNote extends StatelessWidget {
  const _EmptyNote(this.text);

  final String text;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 14),
    child: Text(
      text,
      style: const TextStyle(color: AppColors.muted, fontSize: 12),
    ),
  );
}

/// Compact inline error, shared with the position sheets.
class PortfolioInlineError extends StatelessWidget {
  const PortfolioInlineError({super.key, required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        border: Border.all(color: AppColors.loss),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.error_outline, color: AppColors.loss, size: 16),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              message,
              style: const TextStyle(color: AppColors.loss, fontSize: 11),
            ),
          ),
        ],
      ),
    );
  }
}
