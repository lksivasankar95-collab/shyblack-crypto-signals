import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../domain/entities/market_ticker.dart';
import '../../providers/markets_controller.dart';
import '../../widgets/coin_letter_avatar.dart';
import 'coin_detail_screen.dart';

class MarketsScreen extends ConsumerStatefulWidget {
  const MarketsScreen({super.key});

  @override
  ConsumerState<MarketsScreen> createState() => _MarketsScreenState();
}

class _MarketsScreenState extends ConsumerState<MarketsScreen> with SingleTickerProviderStateMixin {
  late final TabController _tabs;
  final _search = TextEditingController();
  String _query = '';

  static const _tabLabels = ['Watchlist', 'All Markets', 'Top Gainers', 'Top Losers', 'New Listings'];

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: _tabLabels.length, vsync: this, initialIndex: 1);
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
    final asyncMarkets = ref.watch(marketsControllerProvider);

    return ColoredBox(
      color: AppColors.background,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TabBar(
            controller: _tabs,
            isScrollable: true,
            tabAlignment: TabAlignment.start,
            labelColor: AppColors.accent,
            unselectedLabelColor: AppColors.muted,
            indicatorColor: AppColors.accent,
            dividerColor: Colors.transparent,
            labelStyle: const TextStyle(fontWeight: FontWeight.w800, fontSize: 13),
            tabs: [for (final label in _tabLabels) Tab(text: label)],
          ),
          if (_tabs.index == 1)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
              child: TextField(
                controller: _search,
                style: const TextStyle(color: AppColors.onBackground),
                decoration: const InputDecoration(
                  hintText: 'Search symbol or name',
                  prefixIcon: Icon(Icons.search, color: AppColors.muted),
                ),
              ),
            ),
          Expanded(
            child: asyncMarkets.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (error, _) => _ErrorState(
                message: error.toString(),
                onRetry: () => ref.read(marketsControllerProvider.notifier).refresh(),
              ),
              data: (data) => _MarketsBody(
                data: data,
                tabIndex: _tabs.index,
                query: _query,
                onRefresh: () => ref.read(marketsControllerProvider.notifier).refresh(silent: true),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _MarketsBody extends StatelessWidget {
  const _MarketsBody({
    required this.data,
    required this.tabIndex,
    required this.query,
    required this.onRefresh,
  });

  final MarketsViewData data;
  final int tabIndex;
  final String query;
  final Future<void> Function() onRefresh;

  @override
  Widget build(BuildContext context) {
    if (data.isOptionsUnavailable) {
      return RefreshIndicator(
        color: AppColors.accent,
        backgroundColor: AppColors.card,
        onRefresh: onRefresh,
        child: const CustomScrollView(
          physics: AlwaysScrollableScrollPhysics(),
          slivers: [
            SliverFillRemaining(
              hasScrollBody: false,
              child: _EmptyState(
                icon: Icons.tune,
                title: "Options trading data isn't available yet",
                subtitle: 'Switch Trading Mode in Settings to Spot or Futures to see live USDT markets.',
              ),
            ),
          ],
        ),
      );
    }

    final tickers = switch (tabIndex) {
      0 => const <MarketTicker>[],
      1 => _filter(data.all, query),
      2 => data.gainers,
      3 => data.losers,
      _ => const <MarketTicker>[],
    };

    final empty = switch (tabIndex) {
      0 => const _EmptyState(
          icon: Icons.star_border,
          title: 'No watchlist coins yet',
          subtitle: 'Watchlist is coming next — All Markets still lists every USDT pair.',
        ),
      1 => _EmptyState(
          icon: Icons.search_off,
          title: query.isEmpty ? 'No markets to show' : 'No matches for "$query"',
          subtitle: query.isEmpty ? 'Pull to refresh, or check the backend is running.' : 'Try another symbol or name.',
        ),
      2 => const _EmptyState(icon: Icons.trending_up, title: 'No gainers yet', subtitle: 'Pull to refresh.'),
      3 => const _EmptyState(icon: Icons.trending_down, title: 'No losers yet', subtitle: 'Pull to refresh.'),
      _ => const _EmptyState(
          icon: Icons.new_releases_outlined,
          title: 'No new listings yet',
          subtitle: 'This tab will light up when listing data is available.',
        ),
    };

    return RefreshIndicator(
      color: AppColors.accent,
      backgroundColor: AppColors.card,
      onRefresh: onRefresh,
      child: tickers.isEmpty
          ? CustomScrollView(
              physics: const AlwaysScrollableScrollPhysics(),
              slivers: [
                SliverFillRemaining(hasScrollBody: false, child: empty),
              ],
            )
          : ListView.builder(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
              itemCount: tickers.length,
              itemBuilder: (context, index) => _MarketTile(
                ticker: tickers[index],
                onTap: () => CoinDetailScreen.open(context, tickers[index]),
              ),
            ),
    );
  }

  static List<MarketTicker> _filter(List<MarketTicker> source, String query) {
    final q = query.trim().toLowerCase();
    if (q.isEmpty) {
      return source;
    }
    return source
        .where(
          (ticker) =>
              ticker.symbol.toLowerCase().contains(q) ||
              ticker.name.toLowerCase().contains(q) ||
              ticker.baseSymbol.toLowerCase().contains(q),
        )
        .toList();
  }
}

class _MarketTile extends StatelessWidget {
  const _MarketTile({required this.ticker, required this.onTap});

  final MarketTicker ticker;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final changeColor = ticker.isPositive ? AppColors.accent : AppColors.loss;

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
                CoinLetterAvatar(symbol: ticker.symbol, name: ticker.name),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        ticker.baseSymbol,
                        style: const TextStyle(
                          color: AppColors.onBackground,
                          fontWeight: FontWeight.w800,
                          fontSize: 15,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        ticker.name,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(color: AppColors.muted, fontSize: 12),
                      ),
                    ],
                  ),
                ),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text(
                      MarketFormat.price(ticker.price),
                      style: const TextStyle(
                        color: AppColors.onBackground,
                        fontWeight: FontWeight.w800,
                        fontSize: 15,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      MarketFormat.signedPercent(ticker.changePercent24h),
                      style: TextStyle(color: changeColor, fontWeight: FontWeight.w700, fontSize: 12),
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
}

class _EmptyState extends StatelessWidget {
  const _EmptyState({required this.icon, required this.title, required this.subtitle});

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
            style: const TextStyle(color: AppColors.onBackground, fontWeight: FontWeight.w800, fontSize: 18),
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
            Text(message, textAlign: TextAlign.center, style: const TextStyle(color: AppColors.muted)),
            const SizedBox(height: 16),
            SizedBox(
              width: 160,
              child: ElevatedButton(onPressed: onRetry, child: const Text('Retry')),
            ),
          ],
        ),
      ),
    );
  }
}
