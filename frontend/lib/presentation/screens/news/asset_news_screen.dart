import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../domain/entities/news.dart';
import '../../providers/news_providers.dart';
import '../../widgets/news_article_card.dart';
import 'news_detail_screen.dart';

class AssetNewsScreen extends ConsumerWidget {
  const AssetNewsScreen({super.key, required this.symbol});

  final String symbol;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncContext = ref.watch(newsContextProvider(symbol));
    final asyncNews = ref.watch(assetNewsProvider(symbol));

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        title: Text(
          '${symbol.toUpperCase()} News',
          style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w800),
        ),
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(newsContextProvider(symbol));
          ref.invalidate(assetNewsProvider(symbol));
          await Future<void>.delayed(const Duration(milliseconds: 300));
        },
        color: AppColors.accent,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.only(bottom: 24),
          children: [
            _ContextCard(asyncContext: asyncContext),
            const SizedBox(height: 8),
            ...asyncNews.when(
              loading: () => const [
                Padding(
                  padding: EdgeInsets.all(32),
                  child: Center(
                    child: CircularProgressIndicator(color: AppColors.accent),
                  ),
                ),
              ],
              error: (error, _) => [
                Padding(
                  padding: const EdgeInsets.all(20),
                  child: Center(
                    child: Text(
                      'Could not load news: $error',
                      textAlign: TextAlign.center,
                      style: const TextStyle(color: AppColors.muted),
                    ),
                  ),
                ),
              ],
              data: (page) => page.items.isEmpty
                  ? const [
                      Padding(
                        padding: EdgeInsets.all(32),
                        child: Center(
                          child: Text(
                            'No news for this asset yet',
                            style: TextStyle(color: AppColors.muted),
                          ),
                        ),
                      ),
                    ]
                  : [
                      for (final item in page.items)
                        NewsArticleCard(
                          article: item,
                          onTap: () => Navigator.of(context).push(
                            MaterialPageRoute<void>(
                              builder: (_) => NewsDetailScreen(
                                articleId: item.id,
                                initial: item,
                              ),
                            ),
                          ),
                        ),
                    ],
            ),
          ],
        ),
      ),
    );
  }
}

class _ContextCard extends ConsumerWidget {
  const _ContextCard({required this.asyncContext});

  final AsyncValue<NewsContext> asyncContext;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final context = asyncContext.value;
    if (context == null) {
      return Container(
        margin: const EdgeInsets.fromLTRB(16, 8, 16, 8),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: AppColors.card,
          borderRadius: BorderRadius.circular(12),
        ),
        child: const Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            SizedBox(
              width: 16,
              height: 16,
              child: CircularProgressIndicator(
                color: AppColors.accent,
                strokeWidth: 2,
              ),
            ),
            SizedBox(width: 10),
            Text(
              'Computing news context…',
              style: TextStyle(color: AppColors.muted, fontSize: 12),
            ),
          ],
        ),
      );
    }

    return Container(
      margin: const EdgeInsets.fromLTRB(16, 8, 16, 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: AppColors.accent.withValues(alpha: 0.25),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'NEWS CONTEXT',
            style: TextStyle(
              color: AppColors.muted,
              fontSize: 11,
              fontWeight: FontWeight.w800,
              letterSpacing: 0.8,
            ),
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              _metric(
                label: 'News score',
                value: context.newsScore?.toStringAsFixed(1) ?? '—',
              ),
              _metric(
                label: 'Sentiment',
                value: context.sentiment.label,
              ),
              _metric(
                label: 'Impact',
                value: context.impactLevel.label,
              ),
              _metric(
                label: 'Articles',
                value: '${context.articleCount}',
              ),
              _metric(
                label: 'Confidence',
                value: (context.confidenceScore == null
                    ? '—'
                    : '${(context.confidenceScore! * 100).round()}%'),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _metric({required String label, required String value}) {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label.toUpperCase(),
            style: const TextStyle(color: AppColors.muted, fontSize: 9),
          ),
          const SizedBox(height: 2),
          Text(
            value,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              color: AppColors.onBackground,
              fontSize: 13,
              fontWeight: FontWeight.w800,
            ),
          ),
        ],
      ),
    );
  }
}