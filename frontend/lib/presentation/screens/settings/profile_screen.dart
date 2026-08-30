import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../domain/entities/app_settings.dart';
import '../../providers/settings_controller.dart';
import '../../widgets/settings_widgets.dart';
import 'coming_soon_screen.dart';

class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncSettings = ref.watch(settingsControllerProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Profile')),
      body: asyncSettings.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => const Center(child: Text('Could not load profile')),
        data: (settings) => SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _Header(settings: settings),
              const SizedBox(height: 16),
              SettingsCard(
                child: Row(
                  children: [
                    _StatCell(label: 'Member Since', value: settings.memberSince),
                    _StatCell(label: 'Membership', value: settings.membershipTier),
                    _StatCell(label: 'Valid Till', value: settings.subscriptionValidTill),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              SettingsCard(
                child: Text(
                  '"${settings.tagline}"',
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    color: AppColors.accent,
                    fontStyle: FontStyle.italic,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              const SettingsSectionTitle('PROFILE INFORMATION'),
              SettingsCard(
                padding: EdgeInsets.zero,
                child: Column(
                  children: [
                    SettingsNavTile(
                      icon: Icons.badge_outlined,
                      title: 'Full Name',
                      subtitle: settings.fullName,
                      onTap: () => _editText(
                        context,
                        ref,
                        settings,
                        title: 'Full Name',
                        value: settings.fullName,
                        onSave: (value) => settings.copyWith(fullName: value),
                      ),
                    ),
                    SettingsNavTile(
                      icon: Icons.email_outlined,
                      title: 'Email',
                      subtitle: settings.email,
                      onTap: () => _editText(
                        context,
                        ref,
                        settings,
                        title: 'Email',
                        value: settings.email,
                        onSave: (value) => settings.copyWith(email: value),
                      ),
                    ),
                    SettingsNavTile(
                      icon: Icons.phone_outlined,
                      title: 'Phone Number',
                      subtitle: settings.phone,
                      onTap: () => _editText(
                        context,
                        ref,
                        settings,
                        title: 'Phone Number',
                        value: settings.phone,
                        onSave: (value) => settings.copyWith(phone: value),
                      ),
                    ),
                    SettingsNavTile(
                      icon: Icons.public,
                      title: 'Country',
                      subtitle: settings.country,
                      onTap: () => _editText(
                        context,
                        ref,
                        settings,
                        title: 'Country',
                        value: settings.country,
                        onSave: (value) => settings.copyWith(country: value),
                      ),
                    ),
                    SettingsNavTile(
                      icon: Icons.schedule,
                      title: 'Timezone',
                      subtitle: settings.timezone,
                      onTap: () => _editText(
                        context,
                        ref,
                        settings,
                        title: 'Timezone',
                        value: settings.timezone,
                        onSave: (value) => settings.copyWith(timezone: value),
                      ),
                    ),
                  ],
                ),
              ),
              const SettingsSectionTitle('TRADING PREFERENCES'),
              SettingsCard(
                padding: EdgeInsets.zero,
                child: Column(
                  children: [
                    SettingsNavTile(
                      icon: Icons.swap_horiz,
                      title: 'Trading Mode',
                      subtitle: settings.tradingMode.label,
                      onTap: () => _pickMode(context, ref, settings),
                    ),
                    SettingsNavTile(
                      icon: Icons.attach_money,
                      title: 'Default Quote Currency',
                      subtitle: settings.quoteCurrency,
                      onTap: () => _pickOption(
                        context,
                        ref,
                        title: 'Quote Currency',
                        options: const ['USDT', 'USDC', 'BTC'],
                        current: settings.quoteCurrency,
                        apply: (value) => settings.copyWith(quoteCurrency: value),
                      ),
                    ),
                    SettingsNavTile(
                      icon: Icons.speed,
                      title: 'Risk Profile',
                      subtitle: settings.riskProfile.label,
                      onTap: () => _pickRisk(context, ref, settings),
                    ),
                    SettingsNavTile(
                      icon: Icons.layers_outlined,
                      title: 'Default Leverage View',
                      subtitle: settings.defaultLeverageView,
                      onTap: () => _pickOption(
                        context,
                        ref,
                        title: 'Leverage View',
                        options: const ['Isolated', 'Cross'],
                        current: settings.defaultLeverageView,
                        apply: (value) => settings.copyWith(defaultLeverageView: value),
                      ),
                    ),
                  ],
                ),
              ),
              const SettingsSectionTitle('QUICK ACTIONS'),
              GridView.count(
                crossAxisCount: 2,
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                mainAxisSpacing: 8,
                crossAxisSpacing: 8,
                childAspectRatio: 1.7,
                children: [
                  _ActionButton(
                    icon: Icons.lock_reset,
                    label: 'Change Password',
                    onTap: () => _soon(context, 'Change Password'),
                  ),
                  _ActionButton(
                    icon: Icons.security,
                    label: 'Account Security',
                    onTap: () => _soon(context, 'Account Security'),
                  ),
                  _ActionButton(
                    icon: Icons.download_outlined,
                    label: 'Download My Data',
                    onTap: () => _soon(context, 'Download My Data'),
                  ),
                  _ActionButton(
                    icon: Icons.delete_outline,
                    label: 'Delete Account',
                    danger: true,
                    onTap: () => _soon(context, 'Delete Account'),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  static void _soon(BuildContext context, String title) {
    Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => ComingSoonScreen(title: title)));
  }

  static Future<void> _editText(
    BuildContext context,
    WidgetRef ref,
    AppSettings settings, {
    required String title,
    required String value,
    required AppSettings Function(String value) onSave,
  }) async {
    final controller = TextEditingController(text: value);
    final next = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppColors.card,
        title: Text(title),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: const InputDecoration(hintText: 'Enter value'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
          TextButton(
            onPressed: () => Navigator.pop(context, controller.text.trim()),
            child: const Text('Save'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (next == null || next.isEmpty) {
      return;
    }
    await ref.read(settingsControllerProvider.notifier).patch(onSave(next));
  }

  static Future<void> _pickOption(
    BuildContext context,
    WidgetRef ref, {
    required String title,
    required List<String> options,
    required String current,
    required AppSettings Function(String value) apply,
  }) async {
    final next = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppColors.card,
        title: Text(title),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (final option in options)
              ListTile(
                title: Text(option),
                trailing: option == current ? const Icon(Icons.check, color: AppColors.accent) : null,
                onTap: () => Navigator.pop(context, option),
              ),
          ],
        ),
      ),
    );
    if (next == null) {
      return;
    }
    await ref.read(settingsControllerProvider.notifier).patch(apply(next));
  }

  static Future<void> _pickMode(BuildContext context, WidgetRef ref, AppSettings settings) async {
    final next = await showDialog<TradingMode>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppColors.card,
        title: const Text('Trading Mode'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (final mode in TradingMode.values)
              ListTile(
                title: Text(mode.label),
                trailing: mode == settings.tradingMode ? const Icon(Icons.check, color: AppColors.accent) : null,
                onTap: () => Navigator.pop(context, mode),
              ),
          ],
        ),
      ),
    );
    if (next == null) {
      return;
    }
    await ref.read(settingsControllerProvider.notifier).setTradingMode(next);
  }

  static Future<void> _pickRisk(BuildContext context, WidgetRef ref, AppSettings settings) async {
    final next = await showDialog<RiskProfile>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppColors.card,
        title: const Text('Risk Profile'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (final risk in RiskProfile.values)
              ListTile(
                title: Text(risk.label),
                trailing: risk == settings.riskProfile ? const Icon(Icons.check, color: AppColors.accent) : null,
                onTap: () => Navigator.pop(context, risk),
              ),
          ],
        ),
      ),
    );
    if (next == null) {
      return;
    }
    await ref.read(settingsControllerProvider.notifier).patch(settings.copyWith(riskProfile: next));
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.settings});

  final AppSettings settings;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Stack(
          children: [
            CircleAvatar(
              radius: 40,
              backgroundColor: AppColors.accent.withValues(alpha: 0.18),
              child: const Icon(Icons.person, color: AppColors.accent, size: 42),
            ),
            Positioned(
              right: 0,
              bottom: 0,
              child: Container(
                padding: const EdgeInsets.all(4),
                decoration: const BoxDecoration(color: AppColors.accent, shape: BoxShape.circle),
                child: const Icon(Icons.edit, size: 14, color: Colors.black),
              ),
            ),
          ],
        ),
        const SizedBox(height: 10),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(
              settings.fullName,
              style: const TextStyle(color: AppColors.onBackground, fontSize: 20, fontWeight: FontWeight.w800),
            ),
            const SizedBox(width: 8),
            const PremiumBadge(),
          ],
        ),
        const SizedBox(height: 8),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text('ID ${settings.memberId}', style: const TextStyle(color: AppColors.muted, fontSize: 13)),
            IconButton(
              visualDensity: VisualDensity.compact,
              onPressed: () async {
                await Clipboard.setData(ClipboardData(text: settings.memberId));
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Member ID copied')),
                  );
                }
              },
              icon: const Icon(Icons.copy, size: 16, color: AppColors.accent),
            ),
          ],
        ),
      ],
    );
  }
}

class _StatCell extends StatelessWidget {
  const _StatCell({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Column(
        children: [
          Text(label, style: const TextStyle(color: AppColors.muted, fontSize: 11)),
          const SizedBox(height: 6),
          Text(
            value,
            textAlign: TextAlign.center,
            style: const TextStyle(color: AppColors.onBackground, fontWeight: FontWeight.w700, fontSize: 13),
          ),
        ],
      ),
    );
  }
}

class _ActionButton extends StatelessWidget {
  const _ActionButton({
    required this.icon,
    required this.label,
    required this.onTap,
    this.danger = false,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool danger;

  @override
  Widget build(BuildContext context) {
    final color = danger ? AppColors.loss : AppColors.accent;
    return Material(
      color: AppColors.card,
      borderRadius: BorderRadius.circular(14),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(14),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, color: color, size: 22),
              const SizedBox(height: 8),
              Text(
                label,
                textAlign: TextAlign.center,
                style: TextStyle(color: danger ? AppColors.loss : AppColors.onCard, fontWeight: FontWeight.w700, fontSize: 12),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
