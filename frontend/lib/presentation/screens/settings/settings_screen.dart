import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../domain/entities/app_settings.dart';
import '../../providers/auth_session.dart';
import '../../providers/settings_controller.dart';
import '../../shell/main_shell.dart';
import '../../widgets/settings_widgets.dart';
import '../futures_trading/futures_trading_screen.dart';
import '../live_trading/live_trading_screen.dart';
import 'about_screen.dart';
import 'data_management_screen.dart';
import 'exchange_accounts_screen.dart';
import 'help_support_screen.dart';
import 'market_preferences_screen.dart';
import 'notifications_screen.dart';
import 'profile_screen.dart';
import 'security_screen.dart';
import 'signal_preferences_screen.dart';
import 'strategy_build_screen.dart';
import 'subscription_screen.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  /// Stable key so the back affordance is discoverable by tests/semantics.
  static const backButtonKey = Key('settings_back_button');

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncSettings = ref.watch(settingsControllerProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        leading: IconButton(
          key: backButtonKey,
          icon: const Icon(Icons.arrow_back),
          tooltip: MaterialLocalizations.of(context).backButtonTooltip,
          onPressed: () => _goBack(context),
        ),
        title: const Text('App settings'),
      ),
      body: SafeArea(
        child: asyncSettings.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, stackTrace) => Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Text('Could not load settings', style: TextStyle(color: AppColors.muted)),
                const SizedBox(height: 12),
                TextButton(
                  onPressed: () => ref.invalidate(settingsControllerProvider),
                  child: const Text('Retry'),
                ),
              ],
            ),
          ),
          data: (settings) => SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _ProfileSummaryCard(
                  settings: settings,
                  onTap: () => _open(context, const ProfileScreen()),
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
                        onTap: () => _open(context, const SubscriptionScreen()),
                      ),
                      SettingsNavTile(
                        icon: Icons.shield_outlined,
                        title: 'Security',
                        onTap: () => _open(context, const SecurityScreen()),
                      ),
                      SettingsNavTile(
                        icon: Icons.account_balance_wallet_outlined,
                        title: 'Connect Exchange Accounts',
                        badge: _badge(
                          settings.hasVerifiedExchange ? 'Connected' : 'New',
                          settings.hasVerifiedExchange ? AppColors.profit : AppColors.accent,
                        ),
                        onTap: () => _open(context, const ExchangeAccountsScreen()),
                      ),
                      SettingsNavTile(
                        icon: Icons.bolt,
                        title: 'Live Trading',
                        subtitle: settings.liveTradingAllowed
                            ? 'Real orders enabled'
                            : 'Real orders disabled',
                        badge: _badge(
                          settings.liveTradingAllowed ? 'LIVE' : 'OFF',
                          settings.liveTradingAllowed ? AppColors.loss : AppColors.muted,
                        ),
                        onTap: () => _open(context, const LiveTradingScreen()),
                      ),
                      SettingsNavTile(
                        icon: Icons.stacked_line_chart,
                        title: 'Futures Trading',
                        subtitle: 'USDT-M leveraged — off by default',
                        badge: _badge('FUTURES', AppColors.loss),
                        onTap: () => _open(context, const FuturesTradingScreen()),
                      ),
                      SettingsNavTile(
                        icon: Icons.architecture,
                        title: 'Strategy Build',
                        subtitle: 'Manage Spot & Futures strategies',
                        onTap: () => _open(context, const StrategyBuildScreen()),
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
                        icon: Icons.attach_money,
                        title: 'Default Quote Currency',
                        subtitle: settings.quoteCurrency,
                        onTap: () => _pickQuoteCurrency(context, ref, settings),
                      ),
                      SettingsNavTile(
                        icon: Icons.speed,
                        title: 'Risk Profile',
                        subtitle: settings.riskProfile.label,
                        onTap: () => _pickRiskProfile(context, ref, settings),
                      ),
                      SettingsNavTile(
                        icon: Icons.layers_outlined,
                        title: 'Default Leverage View',
                        subtitle: settings.defaultLeverageView,
                        onTap: () => _pickLeverageView(context, ref, settings),
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
                        onTap: () => _open(context, const NotificationsScreen()),
                      ),
                      SettingsNavTile(
                        icon: Icons.bolt_outlined,
                        title: 'Signal Preferences',
                        onTap: () => _open(context, const SignalPreferencesScreen()),
                      ),
                      SettingsNavTile(
                        icon: Icons.show_chart,
                        title: 'Market Preferences',
                        onTap: () => _open(context, const MarketPreferencesScreen()),
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
                        onTap: () => _open(context, const DataManagementScreen()),
                      ),
                      SettingsNavTile(
                        icon: Icons.help_outline,
                        title: 'Help & Support',
                        onTap: () => _open(context, const HelpSupportScreen()),
                      ),
                      SettingsNavTile(
                        icon: Icons.info_outline,
                        title: 'About Us',
                        onTap: () => _open(context, const AboutScreen()),
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

  static Widget _badge(String label, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Text(
        label,
        style: TextStyle(color: color, fontSize: 10, fontWeight: FontWeight.w800),
      ),
    );
  }

  /// Pop back to the previous route, or fall back to the application's main
  /// Signals shell when Settings was opened as a root route (no route to pop).
  static void _goBack(BuildContext context) {
    final navigator = Navigator.of(context);
    if (navigator.canPop()) {
      navigator.pop();
    } else {
      navigator.pushReplacement(
        MaterialPageRoute<void>(builder: (_) => const MainShell()),
      );
    }
  }

  static void _open(BuildContext context, Widget screen) {
    Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => screen));
  }

  static Future<void> _pickTheme(BuildContext context, WidgetRef ref, AppSettings settings) async {
    final next = await _choice(context, 'Theme', const ['dark', 'light', 'system'], settings.themeName);
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

  static Future<void> _pickQuoteCurrency(BuildContext context, WidgetRef ref, AppSettings settings) async {
    final next = await _choice(context, 'Quote Currency', const ['USDT', 'USDC', 'BTC'], settings.quoteCurrency);
    if (next != null) {
      await ref.read(settingsControllerProvider.notifier).patch(settings.copyWith(quoteCurrency: next));
    }
  }

  static Future<void> _pickLeverageView(BuildContext context, WidgetRef ref, AppSettings settings) async {
    final next = await _choice(context, 'Leverage View', const ['Isolated', 'Cross'], settings.defaultLeverageView);
    if (next != null) {
      await ref.read(settingsControllerProvider.notifier).patch(settings.copyWith(defaultLeverageView: next));
    }
  }

  static Future<void> _pickRiskProfile(BuildContext context, WidgetRef ref, AppSettings settings) async {
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
                          settings.fullName.isEmpty ? 'My Account' : settings.fullName,
                          style: const TextStyle(
                            color: AppColors.onBackground,
                            fontWeight: FontWeight.w800,
                            fontSize: 16,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      PremiumBadge(compact: true, label: settings.membershipTier),
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
