import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/theme/app_colors.dart';
import '../../widgets/settings_widgets.dart';

final _exchangesProvider = FutureProvider.autoDispose<List<Map<String, dynamic>>>(
  (ref) => ref.read(listExchangesProvider)(),
);

class ExchangeAccountsScreen extends ConsumerWidget {
  const ExchangeAccountsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncExchanges = ref.watch(_exchangesProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Connect Exchange Accounts'),
        actions: [
          IconButton(
            icon: const Icon(Icons.add, color: AppColors.accent),
            onPressed: () => _showConnectDialog(context, ref),
          ),
        ],
      ),
      body: asyncExchanges.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => _ErrorState(onRetry: () => ref.invalidate(_exchangesProvider)),
        data: (exchanges) => exchanges.isEmpty
            ? _EmptyState(onConnect: () => _showConnectDialog(context, ref))
            : ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  const SettingsSectionTitle('CONNECTED EXCHANGES'),
                  SettingsCard(
                    padding: EdgeInsets.zero,
                    child: Column(
                      children: exchanges
                          .map((e) => _ExchangeTile(
                                data: e,
                                onTest: () => _testConnection(context, ref, e['id'] as String),
                                onDelete: () => _confirmDelete(context, ref, e['id'] as String,
                                    e['exchange'] as String? ?? 'Exchange'),
                              ))
                          .toList(),
                    ),
                  ),
                  const SizedBox(height: 16),
                  OutlinedButton.icon(
                    icon: const Icon(Icons.add),
                    label: const Text('Connect Another Exchange'),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: AppColors.accent,
                      side: const BorderSide(color: AppColors.accent),
                      minimumSize: const Size.fromHeight(48),
                    ),
                    onPressed: () => _showConnectDialog(context, ref),
                  ),
                ],
              ),
      ),
    );
  }

  Future<void> _showConnectDialog(BuildContext context, WidgetRef ref) async {
    final result = await showDialog<Map<String, String>>(
      context: context,
      builder: (context) => const _ConnectExchangeDialog(),
    );
    if (result == null || !context.mounted) return;
    try {
      await ref.read(settingsRepositoryProvider).connectExchange(result);
      ref.invalidate(_exchangesProvider);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Exchange connected. Tap Test to verify.')),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to connect: $e'), backgroundColor: AppColors.loss),
        );
      }
    }
  }

  Future<void> _testConnection(BuildContext context, WidgetRef ref, String id) async {
    try {
      final result = await ref.read(settingsRepositoryProvider).testExchangeConnection(id);
      ref.invalidate(_exchangesProvider);
      final success = result['success'] as bool? ?? false;
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(success ? 'Connection verified' : (result['message'] as String? ?? 'Test failed')),
            backgroundColor: success ? AppColors.profit : AppColors.loss,
          ),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Test failed: $e'), backgroundColor: AppColors.loss),
        );
      }
    }
  }

  Future<void> _confirmDelete(
      BuildContext context, WidgetRef ref, String id, String name) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppColors.card,
        title: const Text('Disconnect Exchange?'),
        content: Text('Remove $name credentials? This cannot be undone.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('DISCONNECT', style: TextStyle(color: AppColors.loss)),
          ),
        ],
      ),
    );
    if (confirmed != true || !context.mounted) return;
    try {
      await ref.read(settingsRepositoryProvider).deleteExchange(id);
      ref.invalidate(_exchangesProvider);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Exchange disconnected')),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed: $e'), backgroundColor: AppColors.loss),
        );
      }
    }
  }
}

class _ExchangeTile extends StatelessWidget {
  const _ExchangeTile({required this.data, required this.onTest, required this.onDelete});

  final Map<String, dynamic> data;
  final VoidCallback onTest;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    final exchange = data['exchange'] as String? ?? '—';
    final status = data['status'] as String? ?? 'NOT_CONNECTED';
    final maskedKey = data['maskedApiKey'] as String? ?? '****';
    final label = data['label'] as String?;
    final isConnected = status == 'CONNECTED';

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      child: Row(
        children: [
          Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              color: AppColors.accent.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(10),
            ),
            child: const Icon(Icons.account_balance_wallet, color: AppColors.accent, size: 22),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label ?? exchange,
                  style: const TextStyle(color: AppColors.onCard, fontWeight: FontWeight.w700, fontSize: 14),
                ),
                const SizedBox(height: 2),
                Text(
                  maskedKey,
                  style: const TextStyle(color: AppColors.muted, fontSize: 12, fontFamily: 'monospace'),
                ),
              ],
            ),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              _StatusChip(connected: isConnected),
              const SizedBox(height: 6),
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  _SmallButton(label: 'Test', onTap: onTest),
                  const SizedBox(width: 8),
                  _SmallButton(label: 'Remove', danger: true, onTap: onDelete),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.connected});
  final bool connected;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: (connected ? AppColors.profit : AppColors.muted).withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        connected ? 'Connected' : 'Not Connected',
        style: TextStyle(
          color: connected ? AppColors.profit : AppColors.muted,
          fontSize: 11,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}

class _SmallButton extends StatelessWidget {
  const _SmallButton({required this.label, required this.onTap, this.danger = false});
  final String label;
  final VoidCallback onTap;
  final bool danger;

  @override
  Widget build(BuildContext context) {
    final color = danger ? AppColors.loss : AppColors.accent;
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        decoration: BoxDecoration(
          border: Border.all(color: color.withValues(alpha: 0.5)),
          borderRadius: BorderRadius.circular(6),
        ),
        child: Text(label, style: TextStyle(color: color, fontSize: 11, fontWeight: FontWeight.w700)),
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState({required this.onConnect});
  final VoidCallback onConnect;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.account_balance_wallet_outlined, color: AppColors.accent, size: 48),
            const SizedBox(height: 16),
            const Text(
              'No Exchanges Connected',
              style: TextStyle(color: AppColors.onBackground, fontSize: 18, fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 8),
            const Text(
              'Connect your exchange API keys to enable live trading signals.',
              textAlign: TextAlign.center,
              style: TextStyle(color: AppColors.muted),
            ),
            const SizedBox(height: 24),
            FilledButton.icon(
              icon: const Icon(Icons.add),
              label: const Text('Connect Exchange'),
              style: FilledButton.styleFrom(backgroundColor: AppColors.accent, foregroundColor: Colors.black),
              onPressed: onConnect,
            ),
          ],
        ),
      ),
    );
  }
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.onRetry});
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.cloud_off, color: AppColors.muted, size: 40),
          const SizedBox(height: 12),
          const Text('Could not load exchanges', style: TextStyle(color: AppColors.muted)),
          const SizedBox(height: 12),
          TextButton(onPressed: onRetry, child: const Text('Retry')),
        ],
      ),
    );
  }
}

class _ConnectExchangeDialog extends StatefulWidget {
  const _ConnectExchangeDialog();

  @override
  State<_ConnectExchangeDialog> createState() => _ConnectExchangeDialogState();
}

class _ConnectExchangeDialogState extends State<_ConnectExchangeDialog> {
  final _apiKeyCtrl = TextEditingController();
  final _apiSecretCtrl = TextEditingController();
  final _labelCtrl = TextEditingController();
  String _exchange = 'BINANCE';
  bool _showSecret = false;

  @override
  void dispose() {
    _apiKeyCtrl.dispose();
    _apiSecretCtrl.dispose();
    _labelCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: AppColors.card,
      title: const Text('Connect Exchange'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            DropdownButtonFormField<String>(
              value: _exchange,
              dropdownColor: AppColors.card,
              decoration: const InputDecoration(labelText: 'Exchange'),
              items: ['BINANCE', 'BINANCE_FUTURES']
                  .map((e) => DropdownMenuItem(value: e, child: Text(e)))
                  .toList(),
              onChanged: (v) => setState(() => _exchange = v ?? 'BINANCE'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _labelCtrl,
              decoration: const InputDecoration(labelText: 'Label (optional)'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _apiKeyCtrl,
              decoration: const InputDecoration(labelText: 'API Key'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _apiSecretCtrl,
              obscureText: !_showSecret,
              decoration: InputDecoration(
                labelText: 'API Secret',
                suffixIcon: IconButton(
                  icon: Icon(_showSecret ? Icons.visibility_off : Icons.visibility),
                  onPressed: () => setState(() => _showSecret = !_showSecret),
                ),
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              'API keys are encrypted at rest. Read-only permissions recommended.',
              style: TextStyle(color: AppColors.muted, fontSize: 11),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
        FilledButton(
          style: FilledButton.styleFrom(backgroundColor: AppColors.accent, foregroundColor: Colors.black),
          onPressed: () {
            if (_apiKeyCtrl.text.trim().isEmpty || _apiSecretCtrl.text.trim().isEmpty) return;
            Navigator.pop(context, {
              'exchange': _exchange,
              'apiKey': _apiKeyCtrl.text.trim(),
              'apiSecret': _apiSecretCtrl.text.trim(),
              'label': _labelCtrl.text.trim().isEmpty ? null : _labelCtrl.text.trim(),
              'displayName': _exchange,
            });
          },
          child: const Text('Connect'),
        ),
      ],
    );
  }
}
