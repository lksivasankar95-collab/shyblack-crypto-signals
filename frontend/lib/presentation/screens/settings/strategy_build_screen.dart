import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../domain/entities/trading_strategy.dart';
import '../../providers/strategy_providers.dart';
import 'create_strategy_screen.dart';

class StrategyBuildScreen extends ConsumerStatefulWidget {
  const StrategyBuildScreen({super.key});

  @override
  ConsumerState<StrategyBuildScreen> createState() =>
      _StrategyBuildScreenState();
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
        title: const Text(
          'Strategy Build',
          style: TextStyle(fontWeight: FontWeight.w800, fontSize: 18),
        ),
        bottom: TabBar(
          controller: _tabs,
          labelColor: AppColors.accent,
          unselectedLabelColor: AppColors.muted,
          indicatorColor: AppColors.accent,
          tabs: const [
            Tab(text: 'SPOT'),
            Tab(text: 'FUTURES'),
          ],
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
    final async = ref.watch(strategyTabProvider(mode));

    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text(
              'Could not load strategies',
              style: TextStyle(color: AppColors.muted),
            ),
            const SizedBox(height: 12),
            TextButton(
              onPressed: () =>
                  ref.read(strategyTabProvider(mode).notifier).refresh(),
              child: const Text('Retry'),
            ),
          ],
        ),
      ),
      data: (state) => RefreshIndicator(
        color: AppColors.accent,
        onRefresh: () => ref.read(strategyTabProvider(mode).notifier).refresh(),
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
          children: [
            _ActiveStrategyCard(state: state, mode: mode),
            const SizedBox(height: 16),
            Row(
              children: [
                const Text(
                  'All Strategies',
                  style: TextStyle(
                    color: AppColors.onBackground,
                    fontWeight: FontWeight.w800,
                    fontSize: 15,
                  ),
                ),
                const Spacer(),
                TextButton.icon(
                  onPressed: () => _addStrategy(context, ref),
                  icon: const Icon(Icons.add, size: 18),
                  label: const Text('Add New'),
                  style: TextButton.styleFrom(
                    foregroundColor: AppColors.accent,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            ...state.strategies.map(
              (s) => _StrategyCard(
                strategy: s,
                isActive: state.activeInfo?.strategyId == s.id,
                onSetActive: () => ref
                    .read(strategyTabProvider(mode).notifier)
                    .setActive(s.id),
                onDelete: s.deletable
                    ? () => _confirmDelete(context, ref, s)
                    : null,
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _addStrategy(BuildContext context, WidgetRef ref) {
    Navigator.of(context)
        .push(
          MaterialPageRoute<void>(
            builder: (_) => CreateStrategyScreen(mode: mode),
          ),
        )
        .then((_) => ref.read(strategyTabProvider(mode).notifier).refresh());
  }

  void _confirmDelete(
    BuildContext context,
    WidgetRef ref,
    TradingStrategy strategy,
  ) {
    showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.card,
        title: const Text('Delete Strategy'),
        content: Text('Delete "${strategy.name}"? This cannot be undone.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text(
              'DELETE',
              style: TextStyle(color: AppColors.loss),
            ),
          ),
        ],
      ),
    ).then((confirmed) {
      if (confirmed == true) {
        ref
            .read(strategyTabProvider(mode).notifier)
            .deleteStrategy(strategy.id);
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
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: AppColors.accent.withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(
                  '$modeLabel ACTIVE',
                  style: const TextStyle(
                    color: AppColors.accent,
                    fontSize: 10,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          if (info == null || !info.hasActiveStrategy)
            const Text(
              'No active strategy selected',
              style: TextStyle(color: AppColors.muted, fontSize: 14),
            )
          else ...[
            Text(
              info.strategyName ?? 'Unknown',
              style: const TextStyle(
                color: AppColors.onBackground,
                fontWeight: FontWeight.w700,
                fontSize: 16,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              'v${info.strategyVersion}',
              style: const TextStyle(color: AppColors.muted, fontSize: 12),
            ),
          ],
        ],
      ),
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
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    strategy.name,
                    style: const TextStyle(
                      color: AppColors.onBackground,
                      fontWeight: FontWeight.w700,
                      fontSize: 14,
                    ),
                  ),
                ),
                if (strategy.strategyType == StrategyType.system)
                  _chip('SYSTEM', AppColors.muted),
                if (isActive) ...[
                  const SizedBox(width: 6),
                  _chip('ACTIVE', AppColors.accent),
                ],
              ],
            ),
            if (strategy.description != null &&
                strategy.description!.isNotEmpty) ...[
              const SizedBox(height: 6),
              Text(
                strategy.description!,
                style: const TextStyle(color: AppColors.muted, fontSize: 12),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
            ],
            const SizedBox(height: 4),
            Text(
              'v${strategy.version} · ${strategy.tradingMode.name.toUpperCase()}',
              style: const TextStyle(color: AppColors.muted, fontSize: 11),
            ),
            if (strategy.config?.pullback != null) ...[
              const SizedBox(height: 10),
              _PullbackSummary(config: strategy.config!.pullback!),
            ],
            if (strategy.config?.emaTrendFollowing != null) ...[
              const SizedBox(height: 10),
              _EmaTrendSummary(config: strategy.config!.emaTrendFollowing!),
            ],
            const SizedBox(height: 12),
            Row(
              children: [
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
                      child: const Text(
                        'Set Active',
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  )
                else
                  const Expanded(
                    child: Row(
                      children: [
                        Icon(
                          Icons.check_circle,
                          color: AppColors.accent,
                          size: 16,
                        ),
                        SizedBox(width: 4),
                        Text(
                          'Currently Active',
                          style: TextStyle(
                            color: AppColors.accent,
                            fontSize: 12,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ],
                    ),
                  ),
                if (onDelete != null) ...[
                  const SizedBox(width: 8),
                  IconButton(
                    onPressed: onDelete,
                    icon: const Icon(
                      Icons.delete_outline,
                      color: AppColors.loss,
                      size: 20,
                    ),
                    tooltip: 'Delete',
                    constraints: const BoxConstraints(
                      minWidth: 32,
                      minHeight: 32,
                    ),
                    padding: EdgeInsets.zero,
                  ),
                ],
              ],
            ),
          ],
        ),
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
      child: Text(
        label,
        style: TextStyle(
          color: color,
          fontSize: 10,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}

/// Read-only summary of an EMA_TREND_FOLLOWING strategy's parameters.
class _EmaTrendSummary extends StatelessWidget {
  final EmaTrendFollowingConfig config;
  const _EmaTrendSummary({required this.config});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: const Color(0xFF2A2A2A)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'EMA TREND FOLLOWING · SPOT · LONG ONLY',
            style: TextStyle(
              color: AppColors.accent,
              fontSize: 10,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 6),
          _row(
            context,
            'Timeframes',
            '${config.htfTimeframe} → ${config.entryTimeframe}',
          ),
          _row(
            context,
            'Trend',
            'EMA${config.htfFastEma}/${config.htfSlowEma} · slope ${config.trendSlopeLookback}',
          ),
          _row(
            context,
            'Entry',
            'EMA${config.entryFastEma}/${config.entrySlowEma} · sep ≥ ${config.minimumEmaSeparationPct}%',
          ),
          _row(
            context,
            'RSI',
            config.rsiFilterEnabled
                ? '${config.minimumRsiForLong}-${config.maximumRsiForLong}'
                : 'off',
          ),
          _row(
            context,
            'Risk',
            'R:R ≥ ${config.minRR} · TP ${config.tp1R}R/${config.tp2R}R/${config.tp3R}R',
          ),
          _row(context, 'Min score', '${config.minimumScore}/100'),
        ],
      ),
    );
  }

  static Widget _row(BuildContext context, String label, String value) =>
      Padding(
        padding: const EdgeInsets.symmetric(vertical: 1.5),
        child: Row(
          children: [
            SizedBox(
              width: 78,
              child: Text(
                label,
                style: const TextStyle(color: AppColors.muted, fontSize: 11),
              ),
            ),
            Expanded(
              child: Text(
                value,
                style: const TextStyle(
                  color: AppColors.onBackground,
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ],
        ),
      );
}

/// Read-only summary of a TREND_PULLBACK strategy's parameters, rendered from
/// the strategy config returned by the backend — no hardcoded values.
class _PullbackSummary extends StatelessWidget {
  final PullbackConfig config;
  const _PullbackSummary({required this.config});

  @override
  Widget build(BuildContext context) {
    final zone = config.zoneMode == 'EMA20'
        ? 'EMA${config.pullbackEma}'
        : 'EMA${config.pullbackEma}-${config.entryEma}';
    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: const Color(0xFF2A2A2A)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'TREND PULLBACK · SPOT · LONG ONLY',
            style: TextStyle(
              color: AppColors.accent,
              fontSize: 10,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 6),
          _row('Timeframes', '${config.htf} → ${config.entryTimeframe}'),
          _row(
            'Trend',
            'EMA${config.emaFastHtf}/${config.emaSlowHtf} · ADX ≥ ${_n(config.minAdx)}',
          ),
          _row(
            'Pullback',
            '$zone · RSI ${_n(config.rsiMin)}-${_n(config.rsiMax)}',
          ),
          _row(
            'Risk',
            'SL ${_n(config.slAtrBuffer)} ATR · max ${_n(config.maxSlAtr)} · R:R ≥ ${_n(config.minRR)}',
          ),
          _row(
            'Targets',
            '${_n(config.tp1R)}R / ${_n(config.tp2R)}R / ${_n(config.tp3R)}R',
          ),
          _row(
            'Volume',
            config.volumeFilterEnabled
                ? '≥ ${_n(config.minVolumeMultiplier)}×'
                : 'off',
          ),
          _row('Min score', '${config.minimumScore}/100'),
        ],
      ),
    );
  }

  static Widget _row(String label, String value) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 1.5),
    child: Row(
      children: [
        SizedBox(
          width: 78,
          child: Text(
            label,
            style: const TextStyle(color: AppColors.muted, fontSize: 11),
          ),
        ),
        Expanded(
          child: Text(
            value,
            style: const TextStyle(
              color: AppColors.onBackground,
              fontSize: 11,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
      ],
    ),
  );

  static String _n(double v) =>
      v == v.roundToDouble() ? v.toInt().toString() : v.toString();
}
