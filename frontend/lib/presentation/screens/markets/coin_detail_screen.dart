import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../domain/entities/coin_about.dart';
import '../../../domain/entities/market_ticker.dart';
import '../../providers/coin_detail_providers.dart';
import '../../providers/markets_controller.dart';
import '../../widgets/coin_kline_chart.dart';
import '../../widgets/coin_letter_avatar.dart';

class CoinDetailScreen extends ConsumerStatefulWidget {
  const CoinDetailScreen({super.key, required this.symbol, this.initialTicker});

  final String symbol;
  final MarketTicker? initialTicker;

  static void open(BuildContext context, MarketTicker ticker) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => CoinDetailScreen(symbol: ticker.symbol, initialTicker: ticker),
      ),
    );
  }

  @override
  ConsumerState<CoinDetailScreen> createState() => _CoinDetailScreenState();
}

class _CoinDetailScreenState extends ConsumerState<CoinDetailScreen> {
  ChartTimeframe _timeframe = ChartTimeframe.d1;
  bool _aboutExpanded = false;

  @override
  Widget build(BuildContext context) {
    final tickerAsync = ref.watch(coinTickerProvider(widget.symbol));
    final klinesAsync = ref.watch(
      coinKlinesProvider(KlineQuery(symbol: widget.symbol, interval: _timeframe.interval)),
    );
    final watched = ref.watch(localWatchlistProvider).contains(widget.symbol);
    final ticker = tickerAsync.value ?? widget.initialTicker;

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        titleSpacing: 0,
        title: ticker == null
            ? Text(widget.symbol)
            : Row(
                children: [
                  CoinLetterAvatar(symbol: ticker.symbol, name: ticker.name, radius: 16),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '${ticker.name} / ${MarketFormat.quoteName(ticker.symbol)}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w800),
                        ),
                        Text(
                          '${ticker.baseSymbol}/USDT',
                          style: const TextStyle(color: AppColors.muted, fontSize: 12),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
        actions: [
          IconButton(
            tooltip: 'Watchlist',
            onPressed: () => ref.read(localWatchlistProvider.notifier).toggle(widget.symbol),
            icon: Icon(watched ? Icons.star : Icons.star_border, color: watched ? AppColors.accent : AppColors.muted),
          ),
          IconButton(
            tooltip: 'Alerts',
            onPressed: () => _toast("Price alerts aren't wired yet"),
            icon: const Icon(Icons.notifications_none),
          ),
          IconButton(
            tooltip: 'Share',
            onPressed: () async {
              await Clipboard.setData(ClipboardData(text: widget.symbol));
              _toast('Copied ${widget.symbol}');
            },
            icon: const Icon(Icons.ios_share),
          ),
        ],
      ),
      body: ticker == null && tickerAsync.isLoading
          ? const Center(child: CircularProgressIndicator())
          : ticker == null
              ? _ErrorBody(
                  message: tickerAsync.error?.toString() ?? 'Could not load this market',
                  onRetry: () {
                    ref.invalidate(coinTickerRestProvider(widget.symbol));
                    ref.invalidate(coinTickerProvider(widget.symbol));
                  },
                )
              : Column(
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
                      child: _PriceAndTrade(ticker: ticker, convertingBtc: _btcPrice()),
                    ),
                    Expanded(
                      child: ListView(
                        padding: const EdgeInsets.fromLTRB(16, 0, 16, 28),
                        children: [
                          _RangeCard(ticker: ticker),
                          const SizedBox(height: 10),
                          _StatsRow(ticker: ticker),
                          const SizedBox(height: 16),
                          _TimeframeTabs(
                            selected: _timeframe,
                            onSelect: (next) => setState(() => _timeframe = next),
                          ),
                          const SizedBox(height: 12),
                          DecoratedBox(
                            decoration: BoxDecoration(
                              color: AppColors.card,
                              borderRadius: BorderRadius.circular(14),
                              border: Border.all(color: AppColors.accent.withValues(alpha: 0.35)),
                            ),
                            child: Padding(
                              padding: const EdgeInsets.fromLTRB(8, 12, 8, 10),
                              child: klinesAsync.when(
                                skipLoadingOnReload: true,
                                loading: () => const SizedBox(
                                  height: 280,
                                  child: Center(child: CircularProgressIndicator()),
                                ),
                                error: (error, _) => SizedBox(
                                  height: 200,
                                  child: _ErrorBody(
                                    message: error.toString(),
                                    onRetry: () => ref.invalidate(
                                      coinKlinesProvider(
                                        KlineQuery(symbol: widget.symbol, interval: _timeframe.interval),
                                      ),
                                    ),
                                  ),
                                ),
                                data: (candles) => CoinKlineChart(candles: candles),
                              ),
                            ),
                          ),
                          const SizedBox(height: 14),
                          _PerformanceRow(
                            data: performanceFromKlines(
                              klinesAsync.value ?? const [],
                              changePercent24h: ticker.changePercent24h,
                            ),
                          ),
                          const SizedBox(height: 16),
                          _AboutSection(
                            text: CoinAboutCopy.forSymbol(ticker.symbol, ticker.baseSymbol),
                            expanded: _aboutExpanded,
                            onToggle: () => setState(() => _aboutExpanded = !_aboutExpanded),
                          ),
                          const SizedBox(height: 16),
                          _SimilarCoins(
                            coins: similarCoins(
                              ref.watch(marketsControllerProvider).value?.all ?? const [],
                              ticker.symbol,
                            ),
                            onTap: (coin) => CoinDetailScreen.open(context, coin),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
    );
  }

  double? _btcPrice() {
    final all = ref.read(marketsControllerProvider).value?.all ?? const [];
    for (final item in all) {
      if (item.symbol == 'BTCUSDT') {
        return item.price;
      }
    }
    return null;
  }

  void _toast(String message) {
    if (!mounted) {
      return;
    }
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message), backgroundColor: AppColors.card),
    );
  }
}

class _PriceAndTrade extends StatelessWidget {
  const _PriceAndTrade({required this.ticker, this.convertingBtc});

  final MarketTicker ticker;
  final double? convertingBtc;

  @override
  Widget build(BuildContext context) {
    final up = ticker.isPositive;
    final btc = convertingBtc;
    final inBtc = btc != null && btc > 0 && ticker.symbol != 'BTCUSDT' ? ticker.price / btc : null;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          MarketFormat.price(ticker.price),
          style: const TextStyle(color: AppColors.onBackground, fontSize: 32, fontWeight: FontWeight.w800),
        ),
        const SizedBox(height: 4),
        Row(
          children: [
            Text(
              '${up ? '+' : ''}${MarketFormat.price(ticker.change24h)}  ${MarketFormat.signedPercent(ticker.changePercent24h)}',
              style: TextStyle(color: MarketFormat.changeColor(ticker.changePercent24h), fontWeight: FontWeight.w700),
            ),
            if (inBtc != null) ...[
              const SizedBox(width: 10),
              Text(
                '≈ ${inBtc.toStringAsFixed(8)} BTC',
                style: const TextStyle(color: AppColors.muted, fontSize: 12),
              ),
            ],
          ],
        ),
        const SizedBox(height: 12),
        ElevatedButton(
          onPressed: () {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text("Trading ${ticker.baseSymbol} isn't available yet"),
                backgroundColor: AppColors.card,
              ),
            );
          },
          child: Text('Trade ${ticker.baseSymbol}'),
        ),
      ],
    );
  }
}

class _RangeCard extends StatelessWidget {
  const _RangeCard({required this.ticker});

  final MarketTicker ticker;

  @override
  Widget build(BuildContext context) {
    final span = ticker.high24h - ticker.low24h;
    final t = span <= 0 ? 0.5 : ((ticker.price - ticker.low24h) / span).clamp(0.0, 1.0);
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.accent.withValues(alpha: 0.28)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Text('24H RANGE', style: TextStyle(color: AppColors.muted, fontSize: 11, fontWeight: FontWeight.w800, letterSpacing: 0.8)),
          const SizedBox(height: 10),
          Row(
            children: [
              Text(MarketFormat.price(ticker.low24h), style: const TextStyle(color: AppColors.muted, fontSize: 12)),
              const Spacer(),
              Text(MarketFormat.price(ticker.high24h), style: const TextStyle(color: AppColors.muted, fontSize: 12)),
            ],
          ),
          const SizedBox(height: 8),
          LayoutBuilder(
            builder: (context, constraints) {
              return Stack(
                clipBehavior: Clip.none,
                children: [
                  Container(
                    height: 6,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(8),
                      gradient: const LinearGradient(colors: [AppColors.loss, AppColors.accent]),
                    ),
                  ),
                  Positioned(
                    left: (constraints.maxWidth - 12) * t,
                    top: -4,
                    child: Container(
                      width: 12,
                      height: 14,
                      decoration: BoxDecoration(
                        color: AppColors.onBackground,
                        borderRadius: BorderRadius.circular(3),
                        border: Border.all(color: AppColors.accent, width: 2),
                      ),
                    ),
                  ),
                ],
              );
            },
          ),
        ],
      ),
    );
  }
}

class _StatsRow extends StatelessWidget {
  const _StatsRow({required this.ticker});

  final MarketTicker ticker;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(child: _StatCard(label: '24H VOLUME', value: MarketFormat.compact(ticker.volume24h))),
        const SizedBox(width: 10),
        Expanded(child: _StatCard(label: '24H HIGH', value: MarketFormat.price(ticker.high24h))),
        const SizedBox(width: 10),
        Expanded(child: _StatCard(label: '24H LOW', value: MarketFormat.price(ticker.low24h))),
      ],
    );
  }
}

class _StatCard extends StatelessWidget {
  const _StatCard({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 12),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.accent.withValues(alpha: 0.22)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: const TextStyle(color: AppColors.muted, fontSize: 10, fontWeight: FontWeight.w800)),
          const SizedBox(height: 6),
          Text(value, style: const TextStyle(color: AppColors.onBackground, fontWeight: FontWeight.w800)),
        ],
      ),
    );
  }
}

class _TimeframeTabs extends StatelessWidget {
  const _TimeframeTabs({required this.selected, required this.onSelect});

  final ChartTimeframe selected;
  final ValueChanged<ChartTimeframe> onSelect;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        for (final frame in ChartTimeframe.values) ...[
          if (frame != ChartTimeframe.values.first) const SizedBox(width: 8),
          Expanded(
            child: Material(
              color: selected == frame ? AppColors.accent.withValues(alpha: 0.16) : AppColors.card,
              borderRadius: BorderRadius.circular(10),
              child: InkWell(
                onTap: () => onSelect(frame),
                borderRadius: BorderRadius.circular(10),
                child: Container(
                  padding: const EdgeInsets.symmetric(vertical: 10),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: selected == frame ? AppColors.accent : const Color(0xFF2A2A2A)),
                  ),
                  child: Text(
                    frame.label,
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: selected == frame ? AppColors.accent : AppColors.muted,
                      fontWeight: FontWeight.w800,
                      fontSize: 13,
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ],
    );
  }
}

class _PerformanceRow extends StatelessWidget {
  const _PerformanceRow({required this.data});

  final PerformanceChange data;

  @override
  Widget build(BuildContext context) {
    final items = <(String, double?)>[
      ('1H', data.hour),
      ('24H', data.day),
      ('7D', data.week),
      ('30D', data.month),
    ].where((item) => item.$2 != null).toList();
    if (items.isEmpty) {
      return const SizedBox.shrink();
    }
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(14),
      ),
      child: Row(
        children: [
          for (final item in items)
            Expanded(
              child: Column(
                children: [
                  Text(item.$1, style: const TextStyle(color: AppColors.muted, fontSize: 11, fontWeight: FontWeight.w800)),
                  const SizedBox(height: 4),
                  Text(
                    MarketFormat.signedPercent(item.$2!),
                    style: TextStyle(color: MarketFormat.changeColor(item.$2!), fontWeight: FontWeight.w800),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

class _AboutSection extends StatelessWidget {
  const _AboutSection({required this.text, required this.expanded, required this.onToggle});

  final String text;
  final bool expanded;
  final VoidCallback onToggle;

  @override
  Widget build(BuildContext context) {
    final short = text.length > 160 && !expanded;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(14),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('ABOUT', style: TextStyle(color: AppColors.muted, fontSize: 11, fontWeight: FontWeight.w800, letterSpacing: 0.8)),
          const SizedBox(height: 8),
          Text(
            short ? '${text.substring(0, 160).trimRight()}…' : text,
            style: const TextStyle(color: AppColors.onCard, height: 1.45),
          ),
          if (text.length > 160)
            TextButton(
              onPressed: onToggle,
              child: Text(expanded ? 'Show less' : 'Read more'),
            ),
        ],
      ),
    );
  }
}

class _SimilarCoins extends StatelessWidget {
  const _SimilarCoins({required this.coins, required this.onTap});

  final List<MarketTicker> coins;
  final ValueChanged<MarketTicker> onTap;

  @override
  Widget build(BuildContext context) {
    if (coins.isEmpty) {
      return const SizedBox.shrink();
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('SIMILAR COINS', style: TextStyle(color: AppColors.muted, fontSize: 11, fontWeight: FontWeight.w800, letterSpacing: 0.8)),
        const SizedBox(height: 8),
        for (final coin in coins)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Material(
              color: AppColors.card,
              borderRadius: BorderRadius.circular(14),
              child: InkWell(
                onTap: () => onTap(coin),
                borderRadius: BorderRadius.circular(14),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                  child: Row(
                    children: [
                      CoinLetterAvatar(symbol: coin.symbol, name: coin.name, radius: 18),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          coin.baseSymbol,
                          style: const TextStyle(color: AppColors.onBackground, fontWeight: FontWeight.w800),
                        ),
                      ),
                      Text(
                        MarketFormat.price(coin.price),
                        style: const TextStyle(color: AppColors.onBackground, fontWeight: FontWeight.w700),
                      ),
                      const SizedBox(width: 8),
                      Text(
                        MarketFormat.signedPercent(coin.changePercent24h),
                        style: TextStyle(color: MarketFormat.changeColor(coin.changePercent24h), fontWeight: FontWeight.w700),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
      ],
    );
  }
}

class _ErrorBody extends StatelessWidget {
  const _ErrorBody({required this.message, required this.onRetry});

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
