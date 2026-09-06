import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/constants/app_constants.dart';
import '../../core/di/providers.dart';
import '../providers/auth_session.dart';
import '../providers/navigation_provider.dart';
import '../providers/markets_controller.dart';
import '../providers/signals_controller.dart';
import '../screens/backtesting/backtesting_screen.dart';
import '../screens/markets/markets_screen.dart';
import '../screens/news/news_screen.dart';
import '../screens/portfolio/portfolio_screen.dart';
import '../screens/signals/signal_details_screen.dart';
import '../screens/signals/signals_screen.dart';

class MainShell extends ConsumerStatefulWidget {
  const MainShell({super.key});

  static const _titles = [
    'Signals',
    'Markets',
    'News',
    'Portfolio',
    'Backtesting',
  ];

  @override
  ConsumerState<MainShell> createState() => _MainShellState();
}

class _MainShellState extends ConsumerState<MainShell> {
  String? _processingPending;

  void _handlePendingSignal(String? signalId) {
    if (signalId != null && signalId != _processingPending) {
      _processingPending = signalId;
      _processPending(signalId);
    }
  }

  Future<void> _processPending(String signalId) async {
    try {
      // Wait for auth to be authenticated
      const timeout = Duration(seconds: 15);
      final start = DateTime.now();
      while (mounted) {
        final auth = ref.read(authSessionProvider).value;
        if (auth == AuthStatus.authenticated) break;
        if (DateTime.now().difference(start) > timeout) return _clearPendingIfMatching(signalId);
        await Future.delayed(const Duration(milliseconds: 200));
      }

      // Ensure signals data available; trigger refresh if needed
      final start2 = DateTime.now();
      while (mounted) {
        final asyncSignals = ref.read(signalsControllerProvider);
        if (asyncSignals is AsyncData<SignalsViewData>) {
          final all = asyncSignals.value.all;
          final match = all.where((s) => s.id == signalId).toList();
          if (mounted && match.isNotEmpty) {
            SignalDetailsScreen.open(context, match.first);
            break;
          }
        }
        // trigger a refresh and wait
        unawaited(ref.read(signalsControllerProvider.notifier).refresh());
        if (DateTime.now().difference(start2) > Duration(seconds: 12)) break;
        await Future.delayed(const Duration(milliseconds: 300));
      }
    } finally {
      _clearPendingIfMatching(signalId);
    }
  }

  void _clearPendingIfMatching(String signalId) {
    final current = ref.read(pendingSignalProvider);
    if (current == signalId) ref.read(pendingSignalProvider.notifier).state = null;
    _processingPending = null;
  }

  @override
  Widget build(BuildContext context) {
    ref.listen<String?>(
      pendingSignalProvider,
      (previous, next) {
        _handlePendingSignal(next);
      },
    );
    _handlePendingSignal(ref.read(pendingSignalProvider));

    final index = ref.watch(selectedTabProvider);
    ref.watch(marketsControllerProvider);
    ref.watch(signalsControllerProvider);

    return Scaffold(
      appBar: index == 0
          ? null
          : AppBar(title: Text('${AppConstants.appName} · ${MainShell._titles[index]}')),
      body: IndexedStack(
        index: index,
        children: const [
          SignalsScreen(),
          MarketsScreen(),
          NewsScreen(),
          PortfolioScreen(),
          BacktestingScreen(),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: index,
        onDestinationSelected: (value) =>
            ref.read(selectedTabProvider.notifier).select(value),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.bolt_outlined),
            selectedIcon: Icon(Icons.bolt),
            label: 'Signals',
          ),
          NavigationDestination(
            icon: Icon(Icons.query_stats_outlined),
            selectedIcon: Icon(Icons.query_stats),
            label: 'Markets',
          ),
          NavigationDestination(
            icon: Icon(Icons.newspaper_outlined),
            selectedIcon: Icon(Icons.newspaper),
            label: 'News',
          ),
          NavigationDestination(
            icon: Icon(Icons.account_balance_wallet_outlined),
            selectedIcon: Icon(Icons.account_balance_wallet),
            label: 'Portfolio',
          ),
          NavigationDestination(
            icon: Icon(Icons.analytics_outlined),
            selectedIcon: Icon(Icons.analytics),
            label: 'Backtesting',
          ),
        ],
      ),
    );
  }
}
