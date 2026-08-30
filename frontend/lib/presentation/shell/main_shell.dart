import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/constants/app_constants.dart';
import '../providers/navigation_provider.dart';
import '../screens/analysis/analysis_screen.dart';
import '../screens/markets/markets_screen.dart';
import '../screens/portfolio/portfolio_screen.dart';
import '../screens/settings/settings_screen.dart';
import '../screens/signals/signals_screen.dart';

class MainShell extends ConsumerWidget {
  const MainShell({super.key});

  static const _titles = [
    'Signals',
    'Markets',
    'Analysis',
    'Portfolio',
    'Settings',
  ];

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final index = ref.watch(selectedTabProvider);

    return Scaffold(
      appBar: AppBar(title: Text('${AppConstants.appName} · ${_titles[index]}')),
      body: IndexedStack(
        index: index,
        children: const [
          SignalsScreen(),
          MarketsScreen(),
          AnalysisScreen(),
          PortfolioScreen(),
          SettingsScreen(),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: index,
        onDestinationSelected: (value) =>
            ref.read(selectedTabProvider.notifier).select(value),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.bolt_outlined), selectedIcon: Icon(Icons.bolt), label: 'Signals'),
          NavigationDestination(icon: Icon(Icons.query_stats_outlined), selectedIcon: Icon(Icons.query_stats), label: 'Markets'),
          NavigationDestination(icon: Icon(Icons.analytics_outlined), selectedIcon: Icon(Icons.analytics), label: 'Analysis'),
          NavigationDestination(icon: Icon(Icons.account_balance_wallet_outlined), selectedIcon: Icon(Icons.account_balance_wallet), label: 'Portfolio'),
          NavigationDestination(icon: Icon(Icons.settings_outlined), selectedIcon: Icon(Icons.settings), label: 'Settings'),
        ],
      ),
    );
  }
}
