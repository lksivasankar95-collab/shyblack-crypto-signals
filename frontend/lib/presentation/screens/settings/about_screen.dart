import 'package:flutter/material.dart';

import '../../../core/theme/app_colors.dart';
import '../../widgets/settings_widgets.dart';

class AboutScreen extends StatelessWidget {
  const AboutScreen({super.key});

  // App version from pubspec.yaml — 1.0.0+1
  static const String _appVersion = '1.0.0';
  static const String _buildNumber = '1';

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('About Us')),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SettingsCard(
              child: Column(
                children: [
                  Container(
                    width: 72,
                    height: 72,
                    decoration: BoxDecoration(
                      color: AppColors.accent.withValues(alpha: 0.12),
                      borderRadius: BorderRadius.circular(18),
                    ),
                    child: const Icon(Icons.currency_bitcoin, color: AppColors.accent, size: 40),
                  ),
                  const SizedBox(height: 14),
                  const Text(
                    'ShyBlack Crypto Signals',
                    style: TextStyle(
                      color: AppColors.onBackground,
                      fontSize: 20,
                      fontWeight: FontWeight.w800,
                    ),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'Version $_appVersion (Build $_buildNumber)',
                    style: const TextStyle(color: AppColors.muted, fontSize: 13),
                  ),
                ],
              ),
            ),
            const SettingsSectionTitle('APPLICATION'),
            SettingsCard(
              padding: EdgeInsets.zero,
              child: Column(
                children: [
                  _InfoTile(label: 'App Name', value: 'ShyBlack Crypto Signals'),
                  _InfoTile(label: 'Version', value: _appVersion),
                  _InfoTile(label: 'Build', value: _buildNumber),
                  _InfoTile(label: 'Platform', value: 'Flutter'),
                  _InfoTile(label: 'Backend', value: 'Spring Boot'),
                  _InfoTile(label: 'Database', value: 'PostgreSQL'),
                ],
              ),
            ),
            const SettingsSectionTitle('LEGAL'),
            SettingsCard(
              padding: EdgeInsets.zero,
              child: Column(
                children: [
                  SettingsNavTile(
                    icon: Icons.description_outlined,
                    title: 'Terms of Service',
                    onTap: () => _showLegal(context, 'Terms of Service', _terms),
                  ),
                  SettingsNavTile(
                    icon: Icons.privacy_tip_outlined,
                    title: 'Privacy Policy',
                    onTap: () => _showLegal(context, 'Privacy Policy', _privacy),
                  ),
                  SettingsNavTile(
                    icon: Icons.code,
                    title: 'Open Source Licenses',
                    onTap: () => showLicensePage(
                      context: context,
                      applicationName: 'ShyBlack Crypto Signals',
                      applicationVersion: _appVersion,
                    ),
                  ),
                ],
              ),
            ),
            const SettingsSectionTitle('DISCLAIMER'),
            SettingsCard(
              child: const Text(
                'ShyBlack Crypto Signals provides market analysis and trading signals for '
                'informational purposes only. This application does not constitute financial '
                'advice. Cryptocurrency trading involves significant risk of loss. '
                'Past performance does not guarantee future results.',
                style: TextStyle(color: AppColors.muted, fontSize: 13, height: 1.5),
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _showLegal(BuildContext context, String title, String content) {
    Navigator.of(context).push(MaterialPageRoute<void>(
      builder: (_) => Scaffold(
        backgroundColor: AppColors.background,
        appBar: AppBar(title: Text(title)),
        body: SingleChildScrollView(
          padding: const EdgeInsets.all(20),
          child: Text(content, style: const TextStyle(color: AppColors.muted, fontSize: 13, height: 1.6)),
        ),
      ),
    ));
  }

  static const _terms = '''
Terms of Service

Last updated: September 2026

By using ShyBlack Crypto Signals ("the App"), you agree to these Terms of Service.

1. USE OF THE APP
The App provides cryptocurrency market analysis and trading signals for informational purposes only. You must be 18 years or older to use the App.

2. NO FINANCIAL ADVICE
Nothing in this App constitutes financial, investment, legal, or tax advice. All signals and analysis are for informational purposes only.

3. RISK DISCLOSURE
Cryptocurrency trading involves substantial risk of loss. You may lose all of your invested capital. Never invest more than you can afford to lose.

4. EXCHANGE CREDENTIALS
API keys you provide are encrypted at rest. You are responsible for the security of your exchange credentials and for using appropriate permission levels (read-only recommended).

5. LIMITATION OF LIABILITY
ShyBlack shall not be liable for any trading losses, missed opportunities, or other damages resulting from use of the App.

6. MODIFICATIONS
We reserve the right to modify these Terms at any time. Continued use of the App constitutes acceptance of the modified Terms.

Contact: support@shyblack.com
''';

  static const _privacy = '''
Privacy Policy

Last updated: September 2026

1. DATA WE COLLECT
- Email address and name (from sign-up or Google Sign-In)
- Trading preferences and settings
- Device tokens for push notifications (encrypted)
- Exchange API credentials (AES-GCM encrypted at rest)

2. HOW WE USE YOUR DATA
- To provide trading signals and market data
- To send push notifications (if enabled)
- To persist your preferences across devices

3. DATA SECURITY
- All API credentials are encrypted at rest using AES-GCM
- JWT tokens are short-lived (15 minutes) with secure refresh
- We never log API secrets or authentication tokens

4. DATA SHARING
We do not sell, trade, or share your personal data with third parties, except as required by law.

5. DATA RETENTION
Your data is retained while your account is active. You may request deletion by contacting support.

6. CONTACT
For privacy questions: support@shyblack.com
''';
}

class _InfoTile extends StatelessWidget {
  const _InfoTile({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      child: Row(
        children: [
          Text(label, style: const TextStyle(color: AppColors.muted, fontSize: 13)),
          const Spacer(),
          Text(value, style: const TextStyle(color: AppColors.onBackground, fontWeight: FontWeight.w600, fontSize: 13)),
        ],
      ),
    );
  }
}
