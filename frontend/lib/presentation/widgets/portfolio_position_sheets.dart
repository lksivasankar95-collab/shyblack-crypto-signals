import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../domain/entities/portfolio_account.dart';
import '../providers/portfolio_controller.dart';
import 'portfolio_widgets.dart' show PortfolioStatePanel, PortfolioValue;

/// Mobile detail, stop/target editing and close confirmation for one position.
///
/// Three rules this file exists to enforce:
///
///  * **Actions address a real record.** Everything is keyed on
///    [PortfolioPosition.positionId], never a list index, so an action can never
///    land on whichever position happens to occupy that slot.
///  * **No action without a confirmation.** Close and reset are destructive and
///    always confirm first; stop/target edits confirm too, because a mistyped
///    stop leaves a position apparently protected when it is not.
///  * **No control that cannot work.** A row with no addressable id, or whose
///    mode is not PAPER, shows no action at all rather than one that would fail.
///
/// The backend remains the final authority on validity: the local checks here are
/// input hygiene only and never a second, divergent set of business rules.
class PortfolioPositionSheets {
  const PortfolioPositionSheets._();

  // ── Detail ────────────────────────────────────────────────────

  /// Opens the full detail view for [position].
  static Future<void> showDetail(
    BuildContext context,
    PortfolioPosition position,
  ) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.card,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(18)),
      ),
      builder: (_) => _PositionDetail(position: position),
    );
  }

  // ── Close ─────────────────────────────────────────────────────

  /// Confirms, then closes. Returns true only when the backend accepted it.
  static Future<bool> confirmClose(
    BuildContext context,
    WidgetRef ref,
    PortfolioPosition position,
  ) async {
    // Captured before any await: the messenger outlives the dialog this opens,
    // whereas the BuildContext may not.
    final messenger = ScaffoldMessenger.of(context);

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => _CloseConfirmDialog(position: position),
    );
    if (confirmed != true) return false;

    final id = position.positionId;
    if (id == null) return false;

    await ref
        .read(portfolioPositionActionControllerProvider.notifier)
        .close(id);

    final action = ref.read(portfolioPositionActionControllerProvider);
    if (action.hasError) {
      // The position is untouched on the server; say so rather than implying a
      // close that did not happen.
      messenger.showSnackBar(
        SnackBar(content: Text('Close failed: ${_reason(action.error)}')),
      );
      return false;
    }
    messenger.showSnackBar(
      SnackBar(content: Text('${position.symbol} closed')),
    );
    return true;
  }

  // ── Stop / target ─────────────────────────────────────────────

  /// Opens the stop/target sheet. Returns true when the backend accepted it.
  static Future<bool> editRisk(
    BuildContext context,
    WidgetRef ref,
    PortfolioPosition position,
  ) async {
    final id = position.positionId;
    if (id == null) return false;

    // Captured before the sheet opens, for the same reason as confirmClose.
    final messenger = ScaffoldMessenger.of(context);

    final saved = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.card,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(18)),
      ),
      builder: (_) => _RiskSheet(position: position),
    );
    if (saved != true &&
        !ref.read(portfolioPositionActionControllerProvider).hasError) {
      return false;
    }

    // A failed update also closes the sheet, so the error state must be read
    // before the early return above: otherwise a server rejection would look
    // exactly like a cancel and the user would believe the edit had applied.
    final action = ref.read(portfolioPositionActionControllerProvider);
    if (action.hasError) {
      messenger.showSnackBar(
        SnackBar(content: Text('Could not update: ${_reason(action.error)}')),
      );
      return false;
    }
    return true;
  }

  static String _reason(Object? error) {
    final text = error.toString();
    // The server's validation message is the useful one; fall back to something
    // honest rather than leaking a stack trace or implying success.
    if (text.contains('Exception: ')) return text.split('Exception: ').last;
    if (text.isEmpty) return 'the request failed';
    return text;
  }
}

// ── Detail sheet ─────────────────────────────────────────────────

class _PositionDetail extends StatelessWidget {
  const _PositionDetail({required this.position});

  final PortfolioPosition position;

  @override
  Widget build(BuildContext context) {
    final futures = position.accountCategory == PortfolioCategory.futures;
    final entry = position.entryPrice;
    final current = position.currentPrice;
    final pnl = position.unrealizedPnl;
    final pct = (entry != null && entry != 0 && current != null && pnl != null)
        ? (current - entry) / entry * 100
        : null;

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
                    position.symbol,
                    style: const TextStyle(
                      color: AppColors.onCard,
                      fontSize: 16,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
                IconButton(
                  key: const Key('detail-close-icon'),
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
                _Pill(
                  label: futures
                      ? 'FUTURES'
                      : position.accountCategory == PortfolioCategory.options
                      ? 'OPTIONS'
                      : 'SPOT',
                ),
                _Pill(label: position.side ?? 'SIDE UNKNOWN'),
                _Pill(label: position.status ?? 'STATUS UNKNOWN'),
                _Pill(
                  label: position.accountMode == PortfolioMode.paper
                      ? 'PAPER'
                      : 'LIVE',
                ),
              ],
            ),
            const SizedBox(height: 14),
            _Group(
              title: 'OVERVIEW',
              rows: {
                'Quantity': PortfolioValue.format(position.quantity),
                futures ? 'Position Size' : 'Avg Buy': PortfolioValue.format(
                  entry,
                ),
                futures ? 'Mark Price' : 'Current Price': PortfolioValue.format(
                  current,
                ),
                'Value': PortfolioValue.format(position.notional),
                'Unrealized P&L': PortfolioValue.format(pnl),
                'P&L %': PortfolioValue.format(pct),
              },
            ),
            if (futures) ...[
              const SizedBox(height: 12),
              _Group(
                title: 'RISK',
                rows: {
                  'Leverage': position.leverage == null
                      ? '—'
                      : '${position.leverage}x',
                  'Liquidation': PortfolioValue.format(
                    position.liquidationPrice,
                  ),
                },
              ),
            ],
            const SizedBox(height: 12),
            _Group(
              title: 'RISK',
              rows: {
                'Stop Loss': PortfolioValue.format(position.stopLoss),
                'Take Profit': PortfolioValue.format(position.takeProfit1),
              },
            ),
            const SizedBox(height: 16),
            _Actions(position: position),
          ],
        ),
      ),
    );
  }
}

/// Close and stop/target controls, or nothing at all when neither can apply.
class _Actions extends ConsumerWidget {
  const _Actions({required this.position});

  final PortfolioPosition position;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final actionable = position.positionId != null;
    final open = position.status == null || position.status == 'OPEN';

    if (!actionable || !open) {
      return const PortfolioStatePanel(
        title: 'No actions available',
        message:
            'This row has no addressable position record, so it cannot be closed or '
            're-protected from here.',
        icon: Icons.info_outline,
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        OutlinedButton(
          key: const Key('detail-update-sltp'),
          onPressed: () async {
            await PortfolioPositionSheets.editRisk(context, ref, position);
            if (context.mounted) Navigator.of(context).maybePop();
          },
          style: _outline(AppColors.accent),
          child: const Text('UPDATE SL/TP'),
        ),
        const SizedBox(height: 8),
        OutlinedButton(
          key: const Key('detail-close-position'),
          onPressed: () async {
            final closed = await PortfolioPositionSheets.confirmClose(
              context,
              ref,
              position,
            );
            if (closed && context.mounted) Navigator.of(context).maybePop();
          },
          style: _outline(AppColors.loss),
          child: const Text('CLOSE'),
        ),
      ],
    );
  }

  static ButtonStyle _outline(Color color) => OutlinedButton.styleFrom(
    foregroundColor: color,
    side: BorderSide(color: color),
    minimumSize: const Size.fromHeight(44),
  );
}

/// Close confirmation. Shows the figures a trader needs to not mis-tap.
class _CloseConfirmDialog extends StatelessWidget {
  const _CloseConfirmDialog({required this.position});

  final PortfolioPosition position;

  @override
  Widget build(BuildContext context) {
    final entry = position.entryPrice;
    final current = position.currentPrice;
    final futures = position.accountCategory == PortfolioCategory.futures;
    final estimated = (position.quantity != null && current != null)
        ? position.quantity! * current
        : null;
    final pnl = position.unrealizedPnl;

    return AlertDialog(
      backgroundColor: AppColors.card,
      title: Text(
        'Close ${position.symbol}?',
        style: const TextStyle(color: AppColors.onCard),
      ),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _row('Market', futures ? 'FUTURES' : 'SPOT'),
            _row(
              'Account',
              position.accountMode == PortfolioMode.paper ? 'PAPER' : 'LIVE',
            ),
            _row('Side', position.side ?? '—'),
            _row('Quantity', PortfolioValue.format(position.quantity)),
            _row(
              futures ? 'Mark Price' : 'Current Price',
              PortfolioValue.format(current),
            ),
            _row('Estimated Value', PortfolioValue.format(estimated)),
            _row('Estimated P&L', PortfolioValue.format(pnl)),
            if (entry != null) _row('Entry', PortfolioValue.format(entry)),
            const SizedBox(height: 10),
            const Text(
              'A closed position cannot be reopened from this screen.',
              style: TextStyle(color: AppColors.muted, fontSize: 11),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          key: const Key('close-cancel'),
          onPressed: () => Navigator.of(context).pop(false),
          child: const Text('CANCEL'),
        ),
        TextButton(
          key: const Key('close-confirm'),
          onPressed: () => Navigator.of(context).pop(true),
          child: const Text('CONFIRM CLOSE'),
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

/// Stop / target editor. Local checks are input hygiene only; the server decides.
class _RiskSheet extends ConsumerStatefulWidget {
  const _RiskSheet({required this.position});

  final PortfolioPosition position;

  @override
  ConsumerState<_RiskSheet> createState() => _RiskSheetState();
}

class _RiskSheetState extends ConsumerState<_RiskSheet> {
  late final TextEditingController _stop = TextEditingController(
    text: _fmt(widget.position.stopLoss),
  );
  late final TextEditingController _target = TextEditingController(
    text: _fmt(widget.position.takeProfit1),
  );
  final _formKey = GlobalKey<FormState>();
  bool _busy = false;

  static String _fmt(double? v) =>
      v == null ? '' : v.toStringAsFixed(v.truncateToDouble() == v ? 0 : 2);

  @override
  void dispose() {
    _stop.dispose();
    _target.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final position = widget.position;
    final long = (position.side ?? 'LONG').toUpperCase() == 'LONG';
    final entry = position.entryPrice;

    return Padding(
      padding: EdgeInsets.only(
        left: 16,
        right: 16,
        top: 14,
        // Keeps the focused field above the keyboard on a small phone.
        bottom: MediaQuery.of(context).viewInsets.bottom + 16,
      ),
      child: SingleChildScrollView(
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                'Stop Loss / Take Profit',
                style: TextStyle(
                  color: AppColors.onCard,
                  fontSize: 15,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                '${position.symbol} · ${position.side ?? '—'}'
                '${entry == null ? '' : ' · entry ${_fmt(entry)}'}',
                style: const TextStyle(color: AppColors.muted, fontSize: 11),
              ),
              const SizedBox(height: 14),
              TextFormField(
                key: const Key('risk-stop'),
                controller: _stop,
                keyboardType: const TextInputType.numberWithOptions(
                  decimal: true,
                ),
                style: const TextStyle(color: AppColors.onCard),
                decoration: const InputDecoration(
                  labelText: 'Stop Loss',
                  labelStyle: TextStyle(color: AppColors.muted),
                  enabledBorder: OutlineInputBorder(),
                ),
                validator: (raw) => _validate(raw, long, entry, isStop: true),
              ),
              const SizedBox(height: 10),
              TextFormField(
                key: const Key('risk-target'),
                controller: _target,
                keyboardType: const TextInputType.numberWithOptions(
                  decimal: true,
                ),
                style: const TextStyle(color: AppColors.onCard),
                decoration: const InputDecoration(
                  labelText: 'Take Profit',
                  labelStyle: TextStyle(color: AppColors.muted),
                  enabledBorder: OutlineInputBorder(),
                ),
                validator: (raw) => _validate(raw, long, entry, isStop: false),
              ),
              const SizedBox(height: 8),
              const Text(
                'A level on the wrong side of the entry can never trigger, so the '
                'position would run unprotected. The server enforces the same rule.',
                style: TextStyle(color: AppColors.muted, fontSize: 10),
              ),
              const SizedBox(height: 14),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      key: const Key('risk-cancel'),
                      onPressed: _busy
                          ? null
                          : () => Navigator.of(context).pop(false),
                      style: OutlinedButton.styleFrom(
                        foregroundColor: AppColors.muted,
                        side: const BorderSide(color: AppColors.muted),
                        minimumSize: const Size.fromHeight(44),
                      ),
                      child: const Text('CANCEL'),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: OutlinedButton(
                      key: const Key('risk-save'),
                      onPressed: _busy ? null : _submit,
                      style: OutlinedButton.styleFrom(
                        foregroundColor: AppColors.accent,
                        side: const BorderSide(color: AppColors.accent),
                        minimumSize: const Size.fromHeight(44),
                      ),
                      child: Text(_busy ? 'SAVING…' : 'UPDATE'),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// Input hygiene mirroring the server rule, so the obvious mistake is caught
  /// before a round trip. The server remains authoritative.
  String? _validate(
    String? raw,
    bool long,
    double? entry, {
    required bool isStop,
  }) {
    final text = (raw ?? '').trim();
    if (text.isEmpty) return null; // leave unchanged
    final value = double.tryParse(text);
    if (value == null) return 'Enter a valid number';
    if (value <= 0) return 'Must be greater than zero';
    if (entry == null) return null; // cannot judge; the server will
    if (isStop) {
      if (long && value >= entry) return 'A LONG stop must be below the entry';
      if (!long && value <= entry)
        return 'A SHORT stop must be above the entry';
    } else {
      if (long && value <= entry)
        return 'A LONG target must be above the entry';
      if (!long && value >= entry)
        return 'A SHORT target must be below the entry';
    }
    return null;
  }

  Future<void> _submit() async {
    if (_formKey.currentState?.validate() != true) return;
    setState(() => _busy = true);

    final stop = double.tryParse(_stop.text.trim());
    final target = double.tryParse(_target.text.trim());
    final id = widget.position.positionId;
    if (id == null) {
      if (mounted) Navigator.of(context).pop(false);
      return;
    }

    final notifier = ref.read(
      portfolioPositionActionControllerProvider.notifier,
    );
    await notifier.updateRisk(
      id,
      stopLoss: _stop.text.trim().isEmpty ? null : stop,
      takeProfit: _target.text.trim().isEmpty ? null : target,
    );

    if (!mounted) return;
    final failed = ref.read(portfolioPositionActionControllerProvider).hasError;
    Navigator.of(context).pop(!failed);
  }
}

class _Group extends StatelessWidget {
  const _Group({required this.title, required this.rows});

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
        for (final entry in rows.entries)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 3),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  entry.key,
                  style: const TextStyle(color: AppColors.muted, fontSize: 12),
                ),
                Text(
                  entry.value,
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
