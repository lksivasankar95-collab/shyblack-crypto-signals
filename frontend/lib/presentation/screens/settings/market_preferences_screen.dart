import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../domain/entities/app_settings.dart';
import '../../providers/settings_controller.dart';
import '../../widgets/settings_widgets.dart';

class MarketPreferencesScreen extends ConsumerWidget {
  const MarketPreferencesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncSettings = ref.watch(settingsControllerProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Market Preferences')),
      body: asyncSettings.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => const Center(child: Text('Could not load settings', style: TextStyle(color: AppColors.muted))),
        data: (settings) => SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SettingsSectionTitle('TRADING PREFERENCES'),
              SettingsCard(
                padding: EdgeInsets.zero,
                child: Column(
                  children: [
                    SettingsNavTile(
                      icon: Icons.attach_money,
                      title: 'Quote Currency',
                      subtitle: 'Default quote asset for market data',
                      trailing: Text(
                        settings.quoteCurrency,
                        style: const TextStyle(color: AppColors.muted, fontSize: 12),
                      ),
                      onTap: () => _pickQuote(context, ref, settings),
                    ),
                    SettingsNavTile(
                      icon: Icons.layers_outlined,
                      title: 'Default Leverage View',
                      subtitle: 'Futures position leverage display',
                      trailing: Text(
                        settings.defaultLeverageView,
                        style: const TextStyle(color: AppColors.muted, fontSize: 12),
                      ),
                      onTap: () => _pickLeverage(context, ref, settings),
                    ),
                  ],
                ),
              ),
              const SettingsSectionTitle('DATA SOURCE'),
              SettingsCard(
                child: Column(
                  children: const [
                    _InfoRow(label: 'Provider', value: 'Binance'),
                    SizedBox(height: 10),
                    _InfoRow(label: 'Spot Stream', value: 'WebSocket (live)'),
                    SizedBox(height: 10),
                    _InfoRow(label: 'Futures Stream', value: 'WebSocket (live)'),
                    SizedBox(height: 10),
                    _InfoRow(label: 'REST Fallback', value: 'Enabled'),
                  ],
                ),
              ),
              const SizedBox(height: 8),
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 4),
                child: Text(
                  'Market data is provided in real time via Binance WebSocket streams. '
                  'REST API is used as fallback when WebSocket is unavailable.',
                  style: TextStyle(color: AppColors.muted, fontSize: 12),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _pickQuote(BuildContext context, WidgetRef ref, AppSettings settings) async {
    final next = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppColors.card,
        title: const Text('Quote Currency'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: ['USDT', 'USDC', 'BTC', 'ETH'].map((q) => ListTile(
            title: Text(q),
            trailing: q == settings.quoteCurrency ? const Icon(Icons.check, color: AppColors.accent) : null,
            onTap: () => Navigator.pop(context, q),
          )).toList(),
        ),
      ),
    );
    if (next == null) return;
    await ref.read(settingsControllerProvider.notifier).patch(settings.copyWith(quoteCurrency: next));
  }

  Future<void> _pickLeverage(BuildContext context, WidgetRef ref, AppSettings settings) async {
    final next = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppColors.card,
        title: const Text('Default Leverage View'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: ['1x', '2x', '3x', '5x', '10x', '20x', 'Isolated', 'Cross'].map((v) => ListTile(
            title: Text(v),
            trailing: v == settings.defaultLeverageView ? const Icon(Icons.check, color: AppColors.accent) : null,
            onTap: () => Navigator.pop(context, v),
          )).toList(),
        ),
      ),
    );
    if (next == null) return;
    await ref.read(settingsControllerProvider.notifier).patch(settings.copyWith(defaultLeverageView: next));
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Text(label, style: const TextStyle(color: AppColors.muted, fontSize: 13)),
        const Spacer(),
        Text(value, style: const TextStyle(color: AppColors.onBackground, fontWeight: FontWeight.w600, fontSize: 13)),
      ],
    );
  }
}
