import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/theme/app_colors.dart';
import '../../providers/auth_session.dart';
import '../../widgets/settings_widgets.dart';

final _profileProvider = FutureProvider.autoDispose<Map<String, dynamic>>(
  (ref) => ref.read(settingsRemoteDataSourceProvider).fetchProfile(),
);

class SecurityScreen extends ConsumerWidget {
  const SecurityScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncProfile = ref.watch(_profileProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Security')),
      body: asyncProfile.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => const _SecurityBody(authProvider: null, email: null),
        data: (profile) => _SecurityBody(
          authProvider: profile['authProvider'] as String?,
          email: profile['email'] as String?,
        ),
      ),
    );
  }
}

class _SecurityBody extends ConsumerWidget {
  const _SecurityBody({required this.authProvider, required this.email});

  final String? authProvider;
  final String? email;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isGoogle = authProvider?.toUpperCase() == 'GOOGLE';

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const SettingsSectionTitle('AUTHENTICATION'),
          SettingsCard(
            child: Column(
              children: [
                Row(
                  children: [
                    Icon(
                      isGoogle ? Icons.g_mobiledata : Icons.email_outlined,
                      color: AppColors.accent,
                      size: 28,
                    ),
                    const SizedBox(width: 12),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          isGoogle ? 'Google Account' : 'Email & Password',
                          style: const TextStyle(
                            color: AppColors.onBackground,
                            fontWeight: FontWeight.w700,
                            fontSize: 15,
                          ),
                        ),
                        if (email != null)
                          Text(email!, style: const TextStyle(color: AppColors.muted, fontSize: 13)),
                      ],
                    ),
                  ],
                ),
                if (isGoogle) ...[
                  const SizedBox(height: 12),
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: AppColors.accent.withValues(alpha: 0.08),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Row(
                      children: [
                        Icon(Icons.shield_outlined, color: AppColors.accent, size: 16),
                        SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            'Password and 2FA are managed through your Google Account settings.',
                            style: TextStyle(color: AppColors.accent, fontSize: 12),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ],
            ),
          ),
          const SettingsSectionTitle('SESSION'),
          SettingsCard(
            padding: EdgeInsets.zero,
            child: Column(
              children: [
                SettingsNavTile(
                  icon: Icons.devices_outlined,
                  title: 'Registered Devices',
                  subtitle: 'View devices with push access',
                  onTap: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => const _DevicesPreviewScreen(),
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SettingsSectionTitle('ACCOUNT ACTIONS'),
          SettingsCard(
            padding: EdgeInsets.zero,
            child: Column(
              children: [
                SettingsNavTile(
                  icon: Icons.logout,
                  title: 'Sign Out',
                  subtitle: 'End your current session',
                  onTap: () => _confirmLogout(context, ref),
                ),
                SettingsNavTile(
                  icon: Icons.delete_forever_outlined,
                  title: 'Delete Account',
                  subtitle: 'Permanently remove your account',
                  onTap: () => _showDeleteInfo(context),
                ),
              ],
            ),
          ),
          const SizedBox(height: 8),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 4),
            child: Text(
              'All API keys and credentials are encrypted at rest using AES-GCM encryption.',
              style: TextStyle(color: AppColors.muted, fontSize: 12),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _confirmLogout(BuildContext context, WidgetRef ref) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppColors.card,
        title: const Text('Sign Out?'),
        content: const Text('You will need to sign in again.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('SIGN OUT', style: TextStyle(color: AppColors.loss)),
          ),
        ],
      ),
    );
    if (confirmed != true || !context.mounted) return;
    await ref.read(authSessionProvider.notifier).signOut();
  }

  void _showDeleteInfo(BuildContext context) {
    showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppColors.card,
        title: const Text('Delete Account'),
        content: const Text(
          'To permanently delete your account and all associated data, '
          'please contact support at support@shyblack.com with your registered email.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('OK')),
        ],
      ),
    );
  }
}

class _DevicesPreviewScreen extends ConsumerWidget {
  const _DevicesPreviewScreen();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncTokens = ref.watch(
      FutureProvider.autoDispose<List<Map<String, dynamic>>>(
        (ref) => ref.read(listDeviceTokensProvider)(),
      ),
    );

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Registered Devices')),
      body: asyncTokens.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => const Center(child: Text('Could not load devices', style: TextStyle(color: AppColors.muted))),
        data: (tokens) => tokens.isEmpty
            ? const Center(child: Text('No registered devices', style: TextStyle(color: AppColors.muted)))
            : ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  const SettingsSectionTitle('ACTIVE DEVICES'),
                  SettingsCard(
                    padding: EdgeInsets.zero,
                    child: Column(
                      children: tokens.map((t) => ListTile(
                        dense: true,
                        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
                        leading: const Icon(Icons.phone_android, color: AppColors.accent),
                        title: Text(
                          t['maskedToken'] as String? ?? '****',
                          style: const TextStyle(
                            color: AppColors.onCard,
                            fontFamily: 'monospace',
                            fontSize: 13,
                          ),
                        ),
                        subtitle: Text(
                          t['createdAt'] as String? ?? '',
                          style: const TextStyle(color: AppColors.muted, fontSize: 11),
                        ),
                        trailing: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
                          decoration: BoxDecoration(
                            color: AppColors.profit.withValues(alpha: 0.15),
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: const Text('Active', style: TextStyle(color: AppColors.profit, fontSize: 11, fontWeight: FontWeight.w700)),
                        ),
                      )).toList(),
                    ),
                  ),
                ],
              ),
      ),
    );
  }
}
