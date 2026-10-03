import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../../core/theme/app_colors.dart';
import '../../providers/settings_controller.dart';
import '../../widgets/settings_widgets.dart';

class DataManagementScreen extends ConsumerWidget {
  const DataManagementScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Data Management')),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SettingsSectionTitle('CACHE'),
            SettingsCard(
              padding: EdgeInsets.zero,
              child: Column(
                children: [
                  SettingsNavTile(
                    icon: Icons.settings_backup_restore,
                    title: 'Clear Settings Cache',
                    subtitle: 'Reload settings from server',
                    onTap: () => _clearSettingsCache(context, ref),
                  ),
                  SettingsNavTile(
                    icon: Icons.cleaning_services_outlined,
                    title: 'Clear All App Cache',
                    subtitle: 'Clear locally cached data',
                    onTap: () => _confirmClearAll(context, ref),
                  ),
                ],
              ),
            ),
            const SettingsSectionTitle('ACCOUNT DATA'),
            SettingsCard(
              padding: EdgeInsets.zero,
              child: Column(
                children: [
                  SettingsNavTile(
                    icon: Icons.download_outlined,
                    title: 'Export Data',
                    subtitle: 'Contact support to request data export',
                    onTap: () => _showExportInfo(context),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 8),
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 4),
              child: Text(
                'Clearing the cache does not delete your account, signals, or trade history. '
                'Data will be re-synced from the server on next load.',
                style: TextStyle(color: AppColors.muted, fontSize: 12),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _clearSettingsCache(BuildContext context, WidgetRef ref) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('app_settings');
    ref.invalidate(settingsControllerProvider);
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Settings cache cleared. Reloading from server…')),
      );
    }
  }

  Future<void> _confirmClearAll(BuildContext context, WidgetRef ref) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppColors.card,
        title: const Text('Clear All Cache?'),
        content: const Text(
          'This will clear all locally cached app data. '
          'Your account, signals, and trade history are not affected.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('CLEAR', style: TextStyle(color: AppColors.loss)),
          ),
        ],
      ),
    );
    if (confirmed != true) return;

    final prefs = await SharedPreferences.getInstance();
    await prefs.clear();
    ref.invalidate(settingsControllerProvider);
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('App cache cleared')),
      );
      Navigator.of(context).pop();
    }
  }

  void _showExportInfo(BuildContext context) {
    showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppColors.card,
        title: const Text('Export Your Data'),
        content: const Text(
          'To request a full export of your account data, please contact '
          'support@shyblack.com from your registered email address. '
          'We will prepare and send your data within 7 business days.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('OK')),
        ],
      ),
    );
  }
}
