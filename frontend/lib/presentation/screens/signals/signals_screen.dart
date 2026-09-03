import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../domain/entities/app_settings.dart';
import '../../../domain/entities/signal.dart';
import '../../providers/settings_controller.dart';
import '../../providers/signals_controller.dart';
import '../../widgets/coin_letter_avatar.dart';
import '../notifications/notifications_screen.dart';
import '../settings/profile_screen.dart';
import '../settings/settings_screen.dart';
import 'signal_details_screen.dart';

enum _SignalFilter { none, highConfidence, draftExcluded }

class SignalsScreen extends ConsumerStatefulWidget {
  const SignalsScreen({super.key});

  @override
  ConsumerState<SignalsScreen> createState() => _SignalsScreenState();
}

class _SignalsScreenState extends ConsumerState<SignalsScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabs;
  final _search = TextEditingController();

  String _query = '';
  String? _market;
  PositionSide? _side;
  _SignalFilter _filter = _SignalFilter.none;

  static const _tabLabels = ['Active', 'Pending', 'Closed', 'Drafts'];

  @override
  void initState() {
    super.initState();
    _tabs = TabController(
      length: _tabLabels.length,
      vsync: this,
      initialIndex: 0,
    );
    _tabs.addListener(() {
      if (mounted) {
        setState(() {});
      }
    });
    _search.addListener(() {
      final next = _search.text.trim();
      if (next != _query) {
        setState(() => _query = next);
      }
    });
  }

  @override
  void dispose() {
    _tabs.dispose();
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final asyncSignals = ref.watch(signalsControllerProvider);
    final settings = ref.watch(settingsControllerProvider).value;

    return ColoredBox(
      color: AppColors.background,
      child: SafeArea(
        bottom: false,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _TopBar(
              onOpenSettings: () => _open(context, const SettingsScreen()),
              onOpenNotifications: () =>
                  _open(context, const NotificationsScreen()),
              onOpenProfile: () => _open(context, const ProfileScreen()),
            ),
            if (settings != null) _TradingModeCard(settings: settings),
            _PageHeader(onHistory: () => _tabs.animateTo(2)),
            TabBar(
              controller: _tabs,
              isScrollable: true,
              tabAlignment: TabAlignment.start,
              labelColor: AppColors.accent,
              unselectedLabelColor: AppColors.muted,
              indicatorColor: AppColors.accent,
              dividerColor: Colors.transparent,
              labelStyle: const TextStyle(
                fontWeight: FontWeight.w800,
                fontSize: 13,
              ),
              tabs: [for (final label in _tabLabels) Tab(text: label)],
            ),
            _SummaryCards(asyncSignals: asyncSignals),
            _SearchAndFilters(
              show: true,
              query: _query,
              onQueryChanged: (value) => _search.text = value,
              markets: _markets,
              market: _market,
              side: _side,
              filter: _filter,
              onMarketChanged: (value) => setState(() => _market = value),
              onSideChanged: (value) => setState(() => _side = value),
              onOpenFilters: _openFilters,
              onClear: _clearFilters,
            ),
            Expanded(
              child: asyncSignals.when(
                skipLoadingOnReload: true,
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (error, _) => _ErrorState(
                  message: error.toString(),
                  onRetry: () =>
                      ref.read(signalsControllerProvider.notifier).refresh(),
                ),
                data: (data) => _SignalsList(
                  data: data,
                  status: _tabStatus,
                  query: _query,
                  market: _market,
                  side: _side,
                  filter: _filter,
                  onRefresh: () => ref
                      .read(signalsControllerProvider.notifier)
                      .refresh(silent: true),
                ),
              ),
            ),
            _LiveFooter(lastUpdated: asyncSignals.value?.lastUpdated),
          ],
        ),
      ),
    );
  }

  SignalStatus get _tabStatus => switch (_tabs.index) {
    1 => SignalStatus.pending,
    2 => SignalStatus.closed,
    3 => SignalStatus.draft,
    _ => SignalStatus.active,
  };

  bool get _hasQueryOrFilter =>
      _query.isNotEmpty ||
      _market != null ||
      _side != null ||
      _filter != _SignalFilter.none;

  List<String> get _markets {
    final asyncSignals = ref.read(signalsControllerProvider);
    final all = asyncSignals.value?.all ?? const <Signal>[];
    final bases = <String>{for (final signal in all) signal.baseSymbol};
    return bases.toList()..sort();
  }

  void _clearFilters() {
    setState(() {
      _market = null;
      _side = null;
      _filter = _SignalFilter.none;
      _search.clear();
    });
  }

  Future<void> _openFilters() async {
    final next = await showModalBottomSheet<_SignalFilter>(
      context: context,
      backgroundColor: AppColors.card,
      builder: (context) => _FilterSheet(
        current: _filter,
        hasActiveFilters: _hasQueryOrFilter,
        onClear: () {
          Navigator.pop(context, _SignalFilter.none);
          _clearFilters();
        },
      ),
    );
    if (next != null && mounted) {
      setState(() => _filter = next);
    }
  }

  static void _open(BuildContext context, Widget screen) {
    Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => screen));
  }
}

extension on Signal {
  String get baseSymbol {
    final upper = symbol.toUpperCase();
    if (upper.endsWith('USDT') && upper.length > 4) {
      return upper.substring(0, upper.length - 4);
    }
    return upper;
  }
}

class _TopBar extends StatelessWidget {
  const _TopBar({
    required this.onOpenSettings,
    required this.onOpenNotifications,
    required this.onOpenProfile,
  });

  final VoidCallback onOpenSettings;
  final VoidCallback onOpenNotifications;
  final VoidCallback onOpenProfile;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(8, 4, 12, 4),
      child: Row(
        children: [
          IconButton(
            onPressed: onOpenSettings,
            icon: const Icon(Icons.menu, color: AppColors.onBackground),
          ),
          Row(
            children: [
              Icon(Icons.bolt, color: AppColors.accent, size: 22),
              const SizedBox(width: 6),
              const Text(
                'ShyBlack',
                style: TextStyle(
                  color: AppColors.onBackground,
                  fontWeight: FontWeight.w900,
                  fontSize: 17,
                  letterSpacing: 0.4,
                ),
              ),
            ],
          ),
          const Spacer(),
          IconButton(
            onPressed: onOpenNotifications,
            icon: const Stack(
              clipBehavior: Clip.none,
              children: [
                Icon(Icons.notifications_none, color: AppColors.muted),
                Positioned(right: -2, top: -2, child: _Dot()),
              ],
            ),
          ),
          const SizedBox(width: 4),
          IconButton(
            onPressed: onOpenProfile,
            icon: const CircleAvatar(
              radius: 16,
              backgroundColor: Color(0xFF00E676),
              child: Icon(Icons.person, size: 20, color: Colors.black),
            ),
          ),
        ],
      ),
    );
  }
}

class _Dot extends StatelessWidget {
  const _Dot();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 9,
      height: 9,
      decoration: const BoxDecoration(
        color: AppColors.accent,
        shape: BoxShape.circle,
        border: Border.fromBorderSide(
          BorderSide(color: AppColors.background, width: 1.5),
        ),
      ),
    );
  }
}

class _TradingModeCard extends StatelessWidget {
  const _TradingModeCard({required this.settings});

  final AppSettings settings;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 4, 16, 4),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.accent.withValues(alpha: 0.35)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Text(
            'TRADING MODE',
            style: TextStyle(
              color: AppColors.muted,
              fontSize: 11,
              fontWeight: FontWeight.w800,
              letterSpacing: 1.1,
            ),
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              for (final mode in TradingMode.values) ...[
                if (mode != TradingMode.values.first) const SizedBox(width: 8),
                _ModeChip(mode: mode, selected: settings.tradingMode == mode),
              ],
            ],
          ),
        ],
      ),
    );
  }
}

class _ModeChip extends ConsumerWidget {
  const _ModeChip({required this.mode, required this.selected});

  final TradingMode mode;
  final bool selected;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Expanded(
      child: InkWell(
        onTap: () =>
            ref.read(settingsControllerProvider.notifier).setTradingMode(mode),
        borderRadius: BorderRadius.circular(10),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 8),
          decoration: BoxDecoration(
            color: selected
                ? AppColors.accent.withValues(alpha: 0.16)
                : Colors.transparent,
            borderRadius: BorderRadius.circular(10),
            border: Border.all(
              color: selected ? AppColors.accent : const Color(0xFF2A2A2A),
              width: selected ? 1.4 : 1,
            ),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                mode == TradingMode.spot
                    ? Icons.currency_bitcoin
                    : mode == TradingMode.futures
                    ? Icons.trending_up
                    : Icons.tune,
                size: 16,
                color: selected ? AppColors.accent : AppColors.muted,
              ),
              const SizedBox(width: 6),
              Text(
                mode.label,
                style: TextStyle(
                  color: selected ? AppColors.onBackground : AppColors.muted,
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _PageHeader extends StatelessWidget {
  const _PageHeader({required this.onHistory});

  final VoidCallback onHistory;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
      child: Row(
        children: [
          const Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Signals',
                  style: TextStyle(
                    color: AppColors.onBackground,
                    fontWeight: FontWeight.w900,
                    fontSize: 24,
                  ),
                ),
                SizedBox(height: 2),
                Text(
                  'Signal History',
                  style: TextStyle(color: AppColors.muted, fontSize: 13),
                ),
              ],
            ),
          ),
          TextButton.icon(
            onPressed: onHistory,
            style: TextButton.styleFrom(
              foregroundColor: AppColors.accent,
              visualDensity: VisualDensity.compact,
            ),
            icon: const Icon(Icons.history, size: 16),
            label: const Text('History', style: TextStyle(fontSize: 12)),
          ),
        ],
      ),
    );
  }
}

class _SummaryCards extends StatelessWidget {
  const _SummaryCards({required this.asyncSignals});

  final AsyncValue<SignalsViewData> asyncSignals;

  @override
  Widget build(BuildContext context) {
    final stats = asyncSignals.value?.stats;
    final winRate30d = stats?.winRateLast30d;
    final totalPnl = stats?.totalPnlUsdt;
    final avgReturn = stats?.avgReturnPerSignal;

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 2),
      child: Row(
        children: [
          _SummaryCard(
            label: 'Active',
            value: '${stats?.activeCount ?? 0}',
            icon: Icons.bolt,
            color: AppColors.accent,
          ),
          const SizedBox(width: 8),
          _SummaryCard(
            label: 'Win Rate 30d',
            value: winRate30d == null
                ? '—'
                : '${(winRate30d * 100).toStringAsFixed(1)}%',
            icon: Icons.percent,
            color: winRate30d == null ? AppColors.muted : AppColors.accent,
          ),
          const SizedBox(width: 8),
          _SummaryCard(
            label: 'Total PnL',
            value: totalPnl == null
                ? '—'
                : MarketFormat.price(totalPnl) + (totalPnl >= 0 ? ' USDT' : ''),
            icon: Icons.account_balance_wallet_outlined,
            color: totalPnl == null
                ? AppColors.muted
                : totalPnl >= 0
                ? AppColors.accent
                : AppColors.loss,
            compact: totalPnl != null,
          ),
          const SizedBox(width: 8),
          _SummaryCard(
            label: 'Avg Return',
            value: avgReturn == null
                ? '—'
                : '${(avgReturn * 100).toStringAsFixed(1)}%',
            icon: Icons.show_chart,
            color: avgReturn == null ? AppColors.muted : AppColors.accent,
          ),
        ],
      ),
    );
  }
}

class _SummaryCard extends StatelessWidget {
  const _SummaryCard({
    required this.label,
    required this.value,
    required this.icon,
    required this.color,
    this.compact = false,
  });

  final String label;
  final String value;
  final IconData icon;
  final Color color;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 10),
        decoration: BoxDecoration(
          color: AppColors.card,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, size: 15, color: color),
            const SizedBox(height: 6),
            FittedBox(
              fit: BoxFit.scaleDown,
              child: Text(
                value,
                style: TextStyle(
                  color: color,
                  fontWeight: FontWeight.w900,
                  fontSize: compact ? 13 : 15,
                ),
              ),
            ),
            const SizedBox(height: 2),
            Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(color: AppColors.muted, fontSize: 10),
            ),
          ],
        ),
      ),
    );
  }
}

class _SearchAndFilters extends StatelessWidget {
  const _SearchAndFilters({
    required this.show,
    required this.query,
    required this.onQueryChanged,
    required this.markets,
    required this.market,
    required this.side,
    required this.filter,
    required this.onMarketChanged,
    required this.onSideChanged,
    required this.onOpenFilters,
    required this.onClear,
  });

  final bool show;
  final String query;
  final ValueChanged<String> onQueryChanged;
  final List<String> markets;
  final String? market;
  final PositionSide? side;
  final _SignalFilter filter;
  final ValueChanged<String?> onMarketChanged;
  final ValueChanged<PositionSide?> onSideChanged;
  final VoidCallback onOpenFilters;
  final VoidCallback onClear;

  bool get _filtersActive =>
      market != null || side != null || filter != _SignalFilter.none;

  @override
  Widget build(BuildContext context) {
    if (!show) {
      return const SizedBox.shrink();
    }
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 6, 16, 2),
      child: Column(
        children: [
          TextField(
            onChanged: onQueryChanged,
            style: const TextStyle(color: AppColors.onBackground),
            decoration: const InputDecoration(
              hintText: 'Search symbol or type',
              prefixIcon: Icon(Icons.search, color: AppColors.muted),
              isDense: true,
            ),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(child: _marketDropdown(context)),
              const SizedBox(width: 8),
              Expanded(child: _typeDropdown(context)),
              const SizedBox(width: 8),
              SizedBox(
                width: 96,
                child: _FilterButton(
                  active: _filtersActive,
                  onPressed: onOpenFilters,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _marketDropdown(BuildContext context) {
    return DropdownButtonFormField<String?>(
      initialValue: market,
      isExpanded: true,
      dropdownColor: AppColors.card,
      decoration: const InputDecoration(
        isDense: true,
        contentPadding: EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      ),
      style: const TextStyle(color: AppColors.onBackground, fontSize: 12),
      items: [
        const DropdownMenuItem<String?>(
          value: null,
          child: Text('All Markets', style: TextStyle(color: AppColors.muted)),
        ),
        for (final base in markets)
          DropdownMenuItem<String?>(
            value: base,
            child: Text(
              base,
              style: const TextStyle(color: AppColors.onBackground),
            ),
          ),
      ],
      onChanged: onMarketChanged,
    );
  }

  Widget _typeDropdown(BuildContext context) {
    return DropdownButtonFormField<PositionSide?>(
      initialValue: side,
      isExpanded: true,
      dropdownColor: AppColors.card,
      decoration: const InputDecoration(
        isDense: true,
        contentPadding: EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      ),
      style: const TextStyle(color: AppColors.onBackground, fontSize: 12),
      items: [
        const DropdownMenuItem<PositionSide?>(
          value: null,
          child: Text('All Types', style: TextStyle(color: AppColors.muted)),
        ),
        for (final side in PositionSide.values)
          DropdownMenuItem<PositionSide?>(value: side, child: Text(side.label)),
      ],
      onChanged: onSideChanged,
    );
  }
}

class _FilterButton extends StatelessWidget {
  const _FilterButton({required this.active, required this.onPressed});

  final bool active;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      onPressed: onPressed,
      style: OutlinedButton.styleFrom(
        foregroundColor: active ? AppColors.accent : AppColors.muted,
        side: BorderSide(
          color: active ? AppColors.accent : const Color(0xFF2A2A2A),
        ),
        visualDensity: VisualDensity.compact,
      ),
      icon: Icon(Icons.tune, size: 15),
      label: const Text('Filters', style: TextStyle(fontSize: 12)),
    );
  }
}

class _FilterSheet extends StatelessWidget {
  const _FilterSheet({
    required this.current,
    required this.hasActiveFilters,
    required this.onClear,
  });

  final _SignalFilter current;
  final bool hasActiveFilters;
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                const Text(
                  'Filters',
                  style: TextStyle(
                    color: AppColors.onBackground,
                    fontWeight: FontWeight.w800,
                    fontSize: 16,
                  ),
                ),
                const Spacer(),
                TextButton(onPressed: onClear, child: const Text('Clear all')),
              ],
            ),
            const SizedBox(height: 8),
            _FilterOption(
              label: 'All signals',
              subtitle: 'No additional filtering',
              selected: current == _SignalFilter.none,
              value: _SignalFilter.none,
            ),
            _FilterOption(
              label: 'High confidence only',
              subtitle: 'Confidence matches a strong setup',
              selected: current == _SignalFilter.highConfidence,
              value: _SignalFilter.highConfidence,
            ),
            _FilterOption(
              label: 'Exclude drafts',
              subtitle: 'Hide draft signals everywhere',
              selected: current == _SignalFilter.draftExcluded,
              value: _SignalFilter.draftExcluded,
            ),
            const SizedBox(height: 4),
          ],
        ),
      ),
    );
  }
}

class _FilterOption extends StatelessWidget {
  const _FilterOption({
    required this.label,
    required this.subtitle,
    required this.selected,
    required this.value,
  });

  final String label;
  final String subtitle;
  final bool selected;
  final _SignalFilter value;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: () => Navigator.of(context).pop(value),
      borderRadius: BorderRadius.circular(10),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 10),
        child: Row(
          children: [
            Icon(
              selected ? Icons.radio_button_checked : Icons.radio_button_off,
              color: selected ? AppColors.accent : AppColors.muted,
              size: 20,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    label,
                    style: TextStyle(
                      color: selected
                          ? AppColors.onBackground
                          : AppColors.onCard,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    subtitle,
                    style: const TextStyle(
                      color: AppColors.muted,
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SignalsList extends StatelessWidget {
  const _SignalsList({
    required this.data,
    required this.status,
    required this.query,
    required this.market,
    required this.side,
    required this.filter,
    required this.onRefresh,
  });

  final SignalsViewData data;
  final SignalStatus status;
  final String query;
  final String? market;
  final PositionSide? side;
  final _SignalFilter filter;
  final Future<void> Function() onRefresh;

  @override
  Widget build(BuildContext context) {
    final signals = _filtered();
    final empty = _EmptyState(
      icon: switch (status) {
        SignalStatus.active => Icons.bolt_outlined,
        SignalStatus.pending => Icons.schedule,
        SignalStatus.closed => Icons.task_alt,
        SignalStatus.draft => Icons.edit_note,
      },
      title: switch (status) {
        SignalStatus.active => 'No active signals',
        SignalStatus.pending => 'No pending signals',
        SignalStatus.closed => 'No closed signals yet',
        SignalStatus.draft => 'No drafts yet',
      },
      subtitle: status == SignalStatus.closed
          ? 'Closed signals will appear here once trades are recorded.'
          : 'Pull to refresh, or check the backend is running.',
    );

    return RefreshIndicator(
      color: AppColors.accent,
      backgroundColor: AppColors.card,
      onRefresh: onRefresh,
      child: signals.isEmpty
          ? ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: [
                SizedBox(
                  height: MediaQuery.sizeOf(context).height * 0.32,
                  child: empty,
                ),
              ],
            )
          : ListView.builder(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
              itemCount: signals.length,
              itemBuilder: (context, index) {
                final signal = signals[index];
                return _SignalTile(
                  key: ValueKey(signal.id),
                  signal: signal,
                  onTap: () => SignalDetailsScreen.open(context, signal),
                );
              },
            ),
    );
  }

  List<Signal> _filtered() {
    final q = query.trim().toLowerCase();
    final candidates =
        status == SignalStatus.draft && filter == _SignalFilter.draftExcluded
        ? const <Signal>[]
        : data.byStatus(status);

    return [
      for (final signal in candidates)
        if (filter != _SignalFilter.highConfidence ||
            (signal.confidence ?? 0) >= 80)
          if (market == null || signal.baseSymbol == market)
            if (side == null || signal.side == side)
              if (q.isEmpty ||
                  signal.symbol.toLowerCase().contains(q) ||
                  signal.side.label.toLowerCase().contains(q))
                signal,
    ];
  }
}

class _SignalTile extends StatelessWidget {
  const _SignalTile({super.key, required this.signal, required this.onTap});

  final Signal signal;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final sideColor = signal.side == PositionSide.long
        ? AppColors.accent
        : AppColors.loss;
    final market = signal.baseSymbol;

    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Material(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(14),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(14),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
            child: Row(
              children: [
                CoinLetterAvatar(symbol: market, name: market),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Text(
                            signal.symbol,
                            style: const TextStyle(
                              color: AppColors.onBackground,
                              fontWeight: FontWeight.w800,
                              fontSize: 15,
                            ),
                          ),
                          const SizedBox(width: 8),
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 6,
                              vertical: 2,
                            ),
                            decoration: BoxDecoration(
                              color: sideColor.withValues(alpha: 0.14),
                              borderRadius: BorderRadius.circular(8),
                            ),
                            child: Text(
                              signal.side.label,
                              style: TextStyle(
                                color: sideColor,
                                fontSize: 10,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 4),
                      Text(
                        signal.strategy ?? signal.status.label,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: AppColors.muted,
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ),
                ),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text(
                      _price(signal.entryPrice),
                      style: const TextStyle(
                        color: AppColors.onBackground,
                        fontWeight: FontWeight.w800,
                        fontSize: 15,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      signal.confidence == null
                          ? signal.status.label
                          : '${signal.confidence}%',
                      style: TextStyle(
                        color: signal.confidence == null
                            ? AppColors.muted
                            : AppColors.accent,
                        fontWeight: FontWeight.w700,
                        fontSize: 12,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  static String _price(double? value) =>
      value == null ? '—' : MarketFormat.price(value);
}

class _LiveFooter extends StatelessWidget {
  const _LiveFooter({required this.lastUpdated});

  final DateTime? lastUpdated;

  @override
  Widget build(BuildContext context) {
    final freshness = lastUpdated == null
        ? 'Live updates via WebSocket'
        : 'Live updates via WebSocket · ${_time(lastUpdated!)}';
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 8),
      decoration: const BoxDecoration(
        color: AppColors.card,
        border: Border(top: BorderSide(color: Color(0xFF2A2A2A))),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            width: 8,
            height: 8,
            decoration: const BoxDecoration(
              color: AppColors.accent,
              shape: BoxShape.circle,
            ),
          ),
          const SizedBox(width: 8),
          Text(
            freshness,
            style: const TextStyle(color: AppColors.muted, fontSize: 11),
          ),
        ],
      ),
    );
  }

  static String _time(DateTime value) {
    final local = value.toLocal();
    final h = local.hour.toString().padLeft(2, '0');
    final m = local.minute.toString().padLeft(2, '0');
    return '$h:$m';
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState({
    required this.icon,
    required this.title,
    required this.subtitle,
  });

  final IconData icon;
  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(32),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, color: AppColors.accent, size: 48),
          const SizedBox(height: 16),
          Text(
            title,
            textAlign: TextAlign.center,
            style: const TextStyle(
              color: AppColors.onBackground,
              fontWeight: FontWeight.w800,
              fontSize: 18,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            subtitle,
            textAlign: TextAlign.center,
            style: const TextStyle(color: AppColors.muted, height: 1.4),
          ),
        ],
      ),
    );
  }
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.wifi_off, color: AppColors.muted, size: 40),
            const SizedBox(height: 12),
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(color: AppColors.muted),
            ),
            const SizedBox(height: 16),
            SizedBox(
              width: 160,
              child: ElevatedButton(
                onPressed: onRetry,
                child: const Text('Retry'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
