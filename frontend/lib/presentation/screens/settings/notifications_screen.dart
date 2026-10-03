import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/theme/app_colors.dart';
import '../../widgets/settings_widgets.dart';

final _notifPrefsProvider = FutureProvider.autoDispose<Map<String, dynamic>>(
  (ref) => ref.read(getNotificationPrefsProvider)(),
);

class NotificationsScreen extends ConsumerStatefulWidget {
  const NotificationsScreen({super.key});

  @override
  ConsumerState<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends ConsumerState<NotificationsScreen> {
  Map<String, bool> _prefs = {};
  bool _saving = false;
  bool _loaded = false;

  void _initPrefs(Map<String, dynamic> data) {
    if (_loaded) return;
    _loaded = true;
    _prefs = {
      'signalsEnabled': data['signalsEnabled'] as bool? ?? true,
      'buyAlertsEnabled': data['buyAlertsEnabled'] as bool? ?? true,
      'sellAlertsEnabled': data['sellAlertsEnabled'] as bool? ?? true,
      'newsAlertsEnabled': data['newsAlertsEnabled'] as bool? ?? true,
      'systemAlertsEnabled': data['systemAlertsEnabled'] as bool? ?? true,
    };
  }

  Future<void> _save() async {
    if (_saving) return;
    setState(() => _saving = true);
    try {
      await ref.read(updateNotificationPrefsProvider)(_prefs);
      ref.invalidate(_notifPrefsProvider);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Notification preferences saved')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to save: $e'), backgroundColor: AppColors.loss),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final asyncPrefs = ref.watch(_notifPrefsProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Notifications'),
        actions: [
          if (_saving)
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 16),
              child: SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2)),
            )
          else
            TextButton(
              onPressed: _save,
              child: const Text('Save', style: TextStyle(color: AppColors.accent)),
            ),
        ],
      ),
      body: asyncPrefs.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text('Could not load preferences', style: TextStyle(color: AppColors.muted)),
              const SizedBox(height: 12),
              TextButton(onPressed: () => ref.invalidate(_notifPrefsProvider), child: const Text('Retry')),
            ],
          ),
        ),
        data: (data) {
          _initPrefs(data);
          return SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const SettingsSectionTitle('SIGNAL ALERTS'),
                SettingsCard(
                  padding: EdgeInsets.zero,
                  child: Column(
                    children: [
                      _PrefTile(
                        icon: Icons.bolt,
                        title: 'Signal Alerts',
                        subtitle: 'Receive all trading signal notifications',
                        value: _prefs['signalsEnabled'] ?? true,
                        onChanged: (v) => setState(() => _prefs['signalsEnabled'] = v),
                      ),
                      _PrefTile(
                        icon: Icons.trending_up,
                        title: 'BUY Alerts',
                        subtitle: 'Notify on BUY signals',
                        value: _prefs['buyAlertsEnabled'] ?? true,
                        onChanged: (v) => setState(() => _prefs['buyAlertsEnabled'] = v),
                      ),
                      _PrefTile(
                        icon: Icons.trending_down,
                        title: 'SELL Alerts',
                        subtitle: 'Notify on SELL signals',
                        value: _prefs['sellAlertsEnabled'] ?? true,
                        onChanged: (v) => setState(() => _prefs['sellAlertsEnabled'] = v),
                      ),
                    ],
                  ),
                ),
                const SettingsSectionTitle('OTHER ALERTS'),
                SettingsCard(
                  padding: EdgeInsets.zero,
                  child: Column(
                    children: [
                      _PrefTile(
                        icon: Icons.newspaper,
                        title: 'News Alerts',
                        subtitle: 'High-impact news notifications',
                        value: _prefs['newsAlertsEnabled'] ?? true,
                        onChanged: (v) => setState(() => _prefs['newsAlertsEnabled'] = v),
                      ),
                      _PrefTile(
                        icon: Icons.info_outline,
                        title: 'System Alerts',
                        subtitle: 'App updates and system messages',
                        value: _prefs['systemAlertsEnabled'] ?? true,
                        onChanged: (v) => setState(() => _prefs['systemAlertsEnabled'] = v),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 8),
                const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 4),
                  child: Text(
                    'Push notifications require FCM to be configured on your device.',
                    style: TextStyle(color: AppColors.muted, fontSize: 12),
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _PrefTile extends StatelessWidget {
  const _PrefTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.value,
    required this.onChanged,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final bool value;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      dense: true,
      contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      leading: Icon(icon, color: AppColors.accent, size: 22),
      title: Text(title, style: const TextStyle(color: AppColors.onCard, fontWeight: FontWeight.w600, fontSize: 14)),
      subtitle: Text(subtitle, style: const TextStyle(color: AppColors.muted, fontSize: 12)),
      trailing: Switch(
        value: value,
        onChanged: onChanged,
        activeTrackColor: AppColors.accent,
      ),
    );
  }
}
