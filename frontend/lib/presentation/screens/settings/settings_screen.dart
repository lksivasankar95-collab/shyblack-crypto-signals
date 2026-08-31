import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../domain/entities/app_settings.dart';
import '../../providers/auth_session.dart';
import '../../providers/settings_controller.dart';
import '../../widgets/settings_widgets.dart';
import 'coming_soon_screen.dart';
import 'profile_screen.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncSettings = ref.watch(settingsControllerProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: asyncSettings.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (_, _) => const Center(child: Text('Could not load settings')),
          data: (settings) => SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Text(
                  'App settings',
                  style: TextStyle(color: AppColors.onBackground, fontSize: 24, fontWeight: FontWeight.w800),
                ),
                const SizedBox(height: 14),
                _ProfileSummaryCard(
                  settings: settings,
                  onTap: () => _open(context, const ProfileScreen()),
                ),
                const SettingsSectionTitle('TRADING MODE'),
                Row(
                  children: [
                    for (final mode in TradingMode.values) ...[
                      if (mode != TradingMode.values.first) const SizedBox(width: 8),
                      Expanded(
                        child: _ModeCard(
                          mode: mode,
                          selected: settings.tradingMode == mode,
                          onTap: () => ref.read(settingsControllerProvider.notifier).setTradingMode(mode),
                        ),
                      ),
                    ],
                  ],
                ),
                const SettingsSectionTitle('TRADING ACCOUNT'),
                SettingsCard(
                  padding: EdgeInsets.zero,
                  child: Column(
                    children: [
                      for (final account in TradingAccount.values)
                        _AccountTile(
                          account: account,
                          selected: settings.tradingAccount == account,
                          onTap: () =>
                              ref.read(settingsControllerProvider.notifier).setTradingAccount(account),
                        ),
                    ],
                  ),
                ),
                const SettingsSectionTitle('ACCOUNT'),
                SettingsCard(
                  padding: EdgeInsets.zero,
                  child: Column(
                    children: [
                      SettingsNavTile(
                        icon: Icons.person_outline,
                        title: 'Profile',
                        onTap: () => _open(context, const ProfileScreen()),
                      ),
                      SettingsNavTile(
                        icon: Icons.workspace_premium_outlined,
                        title: 'Subscription',
                        onTap: () => _soon(context, 'Subscription'),
                      ),
                      SettingsNavTile(
                        icon: Icons.shield_outlined,
                        title: 'Security',
                        onTap: () => _soon(context, 'Security'),
                      ),
                      SettingsNavTile(
                        icon: Icons.devices_outlined,
                        title: 'Devices',
                        onTap: () => _soon(context, 'Devices'),
                      ),
                      SettingsNavTile(
                        icon: Icons.account_balance_wallet_outlined,
                        title: 'Connect Exchange Accounts',
                        badge: _newBadge(),
                        onTap: () => _soon(context, 'Connect Exchange Accounts'),
                      ),
                    ],
                  ),
                ),
                const SettingsSectionTitle('PREFERENCES'),
                SettingsCard(
                  padding: EdgeInsets.zero,
                  child: Column(
                    children: [
                      SettingsNavTile(
                        icon: Icons.notifications_outlined,
                        title: 'Notifications',
                        onTap: () => _soon(context, 'Notifications'),
                      ),
                      SettingsNavTile(
                        icon: Icons.bolt_outlined,
                        title: 'Signal Preferences',
                        onTap: () => _soon(context, 'Signal Preferences'),
                      ),
                      SettingsNavTile(
                        icon: Icons.show_chart,
                        title: 'Market Preferences',
                        onTap: () => _soon(context, 'Market Preferences'),
                      ),
                      SettingsNavTile(
                        icon: Icons.palette_outlined,
                        title: 'Theme',
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(settings.themeName, style: const TextStyle(color: AppColors.muted, fontSize: 12)),
                            const SizedBox(width: 8),
                            Container(
                              width: 12,
                              height: 12,
                              decoration: BoxDecoration(
                                color: const Color(0xFF000000),
                                shape: BoxShape.circle,
                                border: Border.all(color: AppColors.accent, width: 1.5),
                              ),
                            ),
                          ],
                        ),
                        onTap: () => _pickTheme(context, ref, settings),
                      ),
                      SettingsNavTile(
                        icon: Icons.language,
                        title: 'Language',
                        trailing: Text(
                          settings.language,
                          style: const TextStyle(color: AppColors.muted, fontSize: 12),
                        ),
                        onTap: () => _pickLanguage(context, ref, settings),
                      ),
                    ],
                  ),
                ),
                const SettingsSectionTitle('DATA & SUPPORT'),
                SettingsCard(
                  padding: EdgeInsets.zero,
                  child: Column(
                    children: [
                      SettingsNavTile(
                        icon: Icons.storage_outlined,
                        title: 'Data Management',
                        onTap: () => _soon(context, 'Data Management'),
                      ),
                      SettingsNavTile(
                        icon: Icons.help_outline,
                        title: 'Help & Support',
                        onTap: () => _soon(context, 'Help & Support'),
                      ),
                      SettingsNavTile(
                        icon: Icons.info_outline,
                        title: 'About Us',
                        onTap: () => _soon(context, 'About Us'),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 20),
                OutlinedButton(
                  onPressed: () => _logout(context, ref),
                  style: OutlinedButton.styleFrom(
                    foregroundColor: AppColors.loss,
                    side: const BorderSide(color: AppColors.loss),
                    minimumSize: const Size.fromHeight(48),
                  ),
                  child: const Text('LOGOUT', style: TextStyle(fontWeight: FontWeight.w800, letterSpacing: 1)),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  static Widget _newBadge() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
      decoration: BoxDecoration(
        color: AppColors.accent,
        borderRadius: BorderRadius.circular(8),
      ),
      child: const Text(
        'New',
        style: TextStyle(color: Colors.black, fontSize: 10, fontWeight: FontWeight.w800),
      ),
    );
  }

  static void _open(BuildContext context, Widget screen) {
    Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => screen));
  }

  static void _soon(BuildContext context, String title) {
    _open(context, ComingSoonScreen(title: title));
  }

  static Future<void> _pickTheme(BuildContext context, WidgetRef ref, AppSettings settings) async {
    final next = await _choice(context, 'Theme', const ['Dark'], settings.themeName);
    if (next != null) {
      await ref.read(settingsControllerProvider.notifier).patch(settings.copyWith(themeName: next));
    }
  }

  static Future<void> _pickLanguage(BuildContext context, WidgetRef ref, AppSettings settings) async {
    final next = await _choice(context, 'Language', const ['English'], settings.language);
    if (next != null) {
      await ref.read(settingsControllerProvider.notifier).patch(settings.copyWith(language: next));
    }
  }

  static Future<String?> _choice(BuildContext context, String title, List<String> options, String current) {
    return showDialog<String>(
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
                onTap: () => Navigator.of(context).pop(option),
              ),
          ],
        ),
      ),
    );
  }

  static Future<void> _logout(BuildContext context, WidgetRef ref) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppColors.card,
        title: const Text('Log out?'),
        content: const Text('You will need to sign in again.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('LOGOUT', style: TextStyle(color: AppColors.loss)),
          ),
        ],
      ),
    );
    if (confirmed != true || !context.mounted) {
      return;
    }
    await ref.read(authSessionProvider.notifier).signOut();
  }
}

class _ProfileSummaryCard extends StatelessWidget {
  const _ProfileSummaryCard({required this.settings, required this.onTap});

  final AppSettings settings;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(14),
      child: SettingsCard(
        child: Row(
          children: [
            CircleAvatar(
              radius: 26,
              backgroundColor: AppColors.accent.withValues(alpha: 0.18),
              child: const Icon(Icons.person, color: AppColors.accent, size: 28),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Flexible(
                        child: Text(
                          settings.fullName,
                          style: const TextStyle(
                            color: AppColors.onBackground,
                            fontWeight: FontWeight.w800,
                            fontSize: 16,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      const PremiumBadge(compact: true),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Text(settings.email, style: const TextStyle(color: AppColors.muted, fontSize: 13)),
                ],
              ),
            ),
            const Icon(Icons.chevron_right, color: AppColors.muted),
          ],
        ),
      ),
    );
  }
}

class _ModeCard extends StatelessWidget {
  const _ModeCard({required this.mode, required this.selected, required this.onTap});

  final TradingMode mode;
  final bool selected;
  final VoidCallback onTap;

  IconData get _icon => switch (mode) {
        TradingMode.spot => Icons.currency_bitcoin,
        TradingMode.futures => Icons.trending_up,
        TradingMode.options => Icons.tune,
      };

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(14),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 8),
        decoration: BoxDecoration(
          color: AppColors.card,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: selected ? AppColors.accent : const Color(0xFF2A2A2A), width: selected ? 1.6 : 1),
        ),
        child: Column(
          children: [
            Icon(_icon, color: selected ? AppColors.accent : AppColors.muted, size: 22),
            const SizedBox(height: 8),
            Text(
              mode.label,
              style: TextStyle(
                color: selected ? AppColors.onBackground : AppColors.muted,
                fontWeight: FontWeight.w700,
                fontSize: 12,
              ),
            ),
            const SizedBox(height: 6),
            Icon(
              selected ? Icons.check_circle : Icons.radio_button_unchecked,
              size: 18,
              color: selected ? AppColors.accent : AppColors.muted,
            ),
          ],
        ),
      ),
    );
  }
}

class _AccountTile extends StatelessWidget {
  const _AccountTile({required this.account, required this.selected, required this.onTap});

  final TradingAccount account;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        child: Row(
          children: [
            Icon(
              account == TradingAccount.paper ? Icons.science_outlined : Icons.account_balance,
              color: selected ? AppColors.accent : AppColors.muted,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    account.label,
                    style: const TextStyle(color: AppColors.onCard, fontWeight: FontWeight.w700, fontSize: 14),
                  ),
                  const SizedBox(height: 2),
                  Text(account.subtitle, style: const TextStyle(color: AppColors.muted, fontSize: 12)),
                ],
              ),
            ),
            if (selected)
              Container(
                margin: const EdgeInsets.only(right: 8),
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: AppColors.accent.withValues(alpha: 0.16),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Text(
                  'Active',
                  style: TextStyle(color: AppColors.accent, fontSize: 11, fontWeight: FontWeight.w800),
                ),
              ),
            Icon(
              selected ? Icons.check_circle : Icons.radio_button_unchecked,
              color: selected ? AppColors.accent : AppColors.muted,
              size: 20,
            ),
          ],
        ),
      ),
    );
  }
}
