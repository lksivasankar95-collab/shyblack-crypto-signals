import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../domain/entities/trading_strategy.dart';
import '../../providers/strategy_providers.dart';
import 'create_strategy_screen.dart';

class StrategyBuildScreen extends ConsumerStatefulWidget {
  const StrategyBuildScreen({super.key});

  @override
  ConsumerState<StrategyBuildScreen> createState() => _StrategyBuildScreenState();
}

class _StrategyBuildScreenState extends ConsumerState<StrategyBuildScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabs;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        foregroundColor: AppColors.onBackground,
        title: const Text('Strategy Build',
            style: TextStyle(fontWeight: FontWeight.w800, fontSize: 18)),
        bottom: TabBar(
          controller: _tabs,
          labelColor: AppColors.accent,
          unselectedLabelColor: AppColors.muted,
          indicatorColor: AppColors.accent,
          tabs: const [Tab(text: 'SPOT'), Tab(text: 'FUTURES')],
        ),
      ),
      body: TabBarView(
        controller: _tabs,
        children: const [
          _StrategyModeTab(mode: StrategyTradingMode.spot),
          _StrategyModeTab(mode: StrategyTradingMode.futures),
        ],
      ),
    );
  }
}

class _StrategyModeTab extends ConsumerWidget {
  final StrategyTradingMode mode;
  const _StrategyModeTab({required this.mode});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(StrategyTabController.provider(mode));

    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          const Text('Could not load strategies', style: TextStyle(color: AppColors.muted)),
          const SizedBox(height: 12),
          TextButton(
            onPressed: () => ref.read(StrategyTabController.provider(mode).notifier).refresh(),
            child: const Text('Retry'),
          ),
        ]),
      ),
      data: (state) => RefreshIndicator(
        color: AppColors.accent,
        onRefresh: () => ref.read(StrategyTabController.provider(mode).notifier).refresh(),
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
          children: [
            _ActiveStrategyCard(state: state, mode: mode),
            const SizedBox(height: 16),
            Row(children: [
              const Text('All Strategies',
                  style: TextStyle(color: AppColors.onBackground, fontWeight: FontWeight.w800, fontSize: 15)),
              const Spacer(),
              TextButton.icon(
                onPressed: () => _addStrategy(context, ref),
                icon: const Icon(Icons.add, size: 18),
                label: const Text('Add New'),
                style: TextButton.styleFrom(foregroundColor: AppColors.accent),
              ),
            ]),
            const SizedBox(height: 8),
            ...state.strategies.map((s) => _StrategyCard(
              strategy: s,
              isActive: state.activeInfo?.strategyId == s.id,
              onSetActive: () => ref.read(StrategyTabController.provider(mode).notifier).setActive(s.id),
              onDelete: s.deletable
                  ? () => _confirmDelete(context, ref, s)
                  : null,
            )),
          ],
        ),
      ),
    );
  }

  void _addStrategy(BuildContext context, WidgetRef ref) {
    Navigator.of(context).push(MaterialPageRoute<void>(
      builder: (_) => CreateStrategyScreen(mode: mode),
    )).then((_) => ref.read(StrategyTabController.provider(mode).notifier).refresh());
  }

  void _confirmDelete(BuildContext context, WidgetRef ref, TradingStrategy strategy) {
    showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.card,
        title: const Text('Delete Strategy'),
        content: Text('Delete "${strategy.name}"? This cannot be undone.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('DELETE', style: TextStyle(color: AppColors.loss)),
          ),
        ],
      ),
    ).then((confirmed) {
      if (confirmed == true) {
        ref.read(StrategyTabController.provider(mode).notifier).deleteStrategy(strategy.id);
      }
    });
  }
}

class _ActiveStrategyCard extends StatelessWidget {
  final StrategyTabState state;
  final StrategyTradingMode mode;
  const _ActiveStrategyCard({required this.state, required this.mode});

  @override
  Widget build(BuildContext context) {
    final info = state.activeInfo;
    final modeLabel = mode == StrategyTradingMode.spot ? 'SPOT' : 'FUTURES';
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.accent.withValues(alpha: 0.3)),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
            decoration: BoxDecoration(
              color: AppColors.accent.withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Text('$modeLabel ACTIVE',
                style: const TextStyle(color: AppColors.accent, fontSize: 10, fontWeight: FontWeight.w800)),
          ),
        ]),
        const SizedBox(height: 10),
        if (info == null || !info.hasActiveStrategy)
          const Text('No active strategy selected',
              style: TextStyle(color: AppColors.muted, fontSize: 14))
        else ...[
          Text(info.strategyName ?? 'Unknown',
              style: const TextStyle(color: AppColors.onBackground, fontWeight: FontWeight.w700, fontSize: 16)),
          const SizedBox(height: 4),
          Text('v${info.strategyVersion}',
              style: const TextStyle(color: AppColors.muted, fontSize: 12)),
        ],
      ]),
    );
  }
}

class _StrategyCard extends StatelessWidget {
  final TradingStrategy strategy;
  final bool isActive;
  final VoidCallback onSetActive;
  final VoidCallback? onDelete;

  const _StrategyCard({
    required this.strategy,
    required this.isActive,
    required this.onSetActive,
    this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: isActive ? AppColors.accent : const Color(0xFF2A2A2A),
          width: isActive ? 1.5 : 1,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Expanded(
              child: Text(strategy.name,
                  style: const TextStyle(color: AppColors.onBackground, fontWeight: FontWeight.w700, fontSize: 14)),
            ),
            if (strategy.strategyType == StrategyType.system)
              _chip('SYSTEM', AppColors.muted),
            if (isActive) ...[
              const SizedBox(width: 6),
              _chip('ACTIVE', AppColors.accent),
            ],
          ]),
          if (strategy.description != null && strategy.description!.isNotEmpty) ...[
            const SizedBox(height: 6),
            Text(strategy.description!,
                style: const TextStyle(color: AppColors.muted, fontSize: 12), maxLines: 2,
                overflow: TextOverflow.ellipsis),
          ],
          const SizedBox(height: 4),
          Text('v${strategy.version} · ${strategy.tradingMode.name.toUpperCase()}',
              style: const TextStyle(color: AppColors.muted, fontSize: 11)),
          const SizedBox(height: 12),
          Row(children: [
            if (!isActive)
              Expanded(
                child: OutlinedButton(
                  onPressed: onSetActive,
                  style: OutlinedButton.styleFrom(
                    foregroundColor: AppColors.accent,
                    side: const BorderSide(color: AppColors.accent),
                    minimumSize: const Size(0, 36),
                    padding: const EdgeInsets.symmetric(horizontal: 12),
                  ),
                  child: const Text('Set Active', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700)),
                ),
              )
            else
              const Expanded(
                child: Row(children: [
                  Icon(Icons.check_circle, color: AppColors.accent, size: 16),
                  SizedBox(width: 4),
                  Text('Currently Active', style: TextStyle(color: AppColors.accent, fontSize: 12, fontWeight: FontWeight.w700)),
                ]),
              ),
            if (onDelete != null) ...[
              const SizedBox(width: 8),
              IconButton(
                onPressed: onDelete,
                icon: const Icon(Icons.delete_outline, color: AppColors.loss, size: 20),
                tooltip: 'Delete',
                constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
                padding: EdgeInsets.zero,
              ),
            ],
          ]),
        ]),
      ),
    );
  }

  static Widget _chip(String label, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Text(label, style: TextStyle(color: color, fontSize: 10, fontWeight: FontWeight.w700)),
    );
  }
}
