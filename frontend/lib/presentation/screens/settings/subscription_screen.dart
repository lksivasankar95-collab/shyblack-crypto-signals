import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../providers/settings_controller.dart';
import '../../widgets/settings_widgets.dart';

class SubscriptionScreen extends ConsumerWidget {
  const SubscriptionScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncSettings = ref.watch(settingsControllerProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Subscription')),
      body: asyncSettings.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => const Center(child: Text('Could not load', style: TextStyle(color: AppColors.muted))),
        data: (settings) => SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              SettingsCard(
                child: Column(
                  children: [
                    Container(
                      width: 64,
                      height: 64,
                      decoration: BoxDecoration(
                        color: AppColors.accent.withValues(alpha: 0.15),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(Icons.workspace_premium, color: AppColors.accent, size: 34),
                    ),
                    const SizedBox(height: 14),
                    Text(
                      settings.membershipTier,
                      style: const TextStyle(
                        color: AppColors.onBackground,
                        fontSize: 22,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Member since: ${settings.memberSince.isEmpty ? "—" : settings.memberSince}',
                      style: const TextStyle(color: AppColors.muted, fontSize: 13),
                    ),
                    const SizedBox(height: 4),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                      decoration: BoxDecoration(
                        color: settings.hasVerifiedExchange
                            ? AppColors.profit.withValues(alpha: 0.15)
                            : AppColors.muted.withValues(alpha: 0.15),
                        borderRadius: BorderRadius.circular(20),
                      ),
                      child: Text(
                        settings.hasVerifiedExchange ? 'Exchange Connected' : 'Paper Trading',
                        style: TextStyle(
                          color: settings.hasVerifiedExchange ? AppColors.profit : AppColors.muted,
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SettingsSectionTitle('CURRENT PLAN FEATURES'),
              SettingsCard(
                child: Column(
                  children: const [
                    _FeatureRow(label: 'Real-time Signals', included: true),
                    SizedBox(height: 10),
                    _FeatureRow(label: 'News Intelligence', included: true),
                    SizedBox(height: 10),
                    _FeatureRow(label: 'Market Data (Spot)', included: true),
                    SizedBox(height: 10),
                    _FeatureRow(label: 'Market Data (Futures)', included: true),
                    SizedBox(height: 10),
                    _FeatureRow(label: 'Paper Trading', included: true),
                    SizedBox(height: 10),
                    _FeatureRow(label: 'Exchange Connection', included: true),
                    SizedBox(height: 10),
                    _FeatureRow(label: 'Push Notifications', included: true),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: AppColors.card,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: AppColors.accent.withValues(alpha: 0.3)),
                ),
                child: const Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Premium Plans Coming Soon',
                      style: TextStyle(color: AppColors.accent, fontWeight: FontWeight.w700, fontSize: 14),
                    ),
                    SizedBox(height: 6),
                    Text(
                      'Advanced analytics, portfolio tracking, and additional signal strategies '
                      'will be available in upcoming premium tiers.',
                      style: TextStyle(color: AppColors.muted, fontSize: 13),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              OutlinedButton(
                onPressed: () {},
                style: OutlinedButton.styleFrom(
                  foregroundColor: AppColors.accent,
                  side: const BorderSide(color: AppColors.accent),
                  minimumSize: const Size.fromHeight(48),
                ),
                child: const Text('Contact Support'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _FeatureRow extends StatelessWidget {
  const _FeatureRow({required this.label, required this.included});
  final String label;
  final bool included;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(
          included ? Icons.check_circle : Icons.cancel_outlined,
          color: included ? AppColors.profit : AppColors.muted,
          size: 18,
        ),
        const SizedBox(width: 10),
        Text(label, style: const TextStyle(color: AppColors.onBackground, fontSize: 14)),
      ],
    );
  }
}
