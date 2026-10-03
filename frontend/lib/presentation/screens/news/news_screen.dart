import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../domain/entities/news.dart';
import '../../providers/news_providers.dart';
import '../../widgets/news_article_card.dart';
import 'news_detail_screen.dart';

class NewsScreen extends ConsumerStatefulWidget {
  const NewsScreen({super.key});

  @override
  ConsumerState<NewsScreen> createState() => _NewsScreenState();
}

class _NewsScreenState extends ConsumerState<NewsScreen> {
  final _search = TextEditingController();
  final _scroll = ScrollController();
  String _query = '';
  NewsSentiment? _sentiment;
  NewsImpact? _impact;
  NewsCategory? _category;

  @override
  void initState() {
    super.initState();
    _search.addListener(() {
      final next = _search.text.trim();
      if (next != _query) {
        setState(() => _query = next);
      }
    });
    _scroll.addListener(() {
      if (_scroll.position.pixels >= _scroll.position.maxScrollExtent - 400) {
        ref.read(newsFeedControllerProvider.notifier).loadMore();
      }
    });
  }

  @override
  void dispose() {
    _search.dispose();
    _scroll.dispose();
    super.dispose();
  }

  void _applyFilters() {
    ref.read(newsFeedControllerProvider.notifier).applyFilters(
      category: _category,
      sentiment: _sentiment,
      impact: _impact,
      query: _query.isEmpty ? null : _query,
    );
  }

  void _openArticle(NewsArticle article) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => NewsDetailScreen(
          articleId: article.id,
          initial: article,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final asyncFeed = ref.watch(newsFeedControllerProvider);

    return ColoredBox(
      color: AppColors.background,
      child: SafeArea(
        bottom: false,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _SearchField(
              controller: _search,
              onSubmitted: (_) => _applyFilters(),
              onClear: () {
                _search.clear();
                _applyFilters();
              },
            ),
            _FilterRow(
              sentiment: _sentiment,
              impact: _impact,
              onSentiment: (value) {
                setState(() => _sentiment = value);
                _applyFilters();
              },
              onImpact: (value) {
                setState(() => _impact = value);
                _applyFilters();
              },
              onCategory: (value) {
                setState(() => _category = value);
                _applyFilters();
              },
            ),
            Expanded(
              child: _NewsList(
                asyncFeed: asyncFeed,
                scrollController: _scroll,
                onArticleTap: _openArticle,
                onRefresh: () =>
                    ref.read(newsFeedControllerProvider.notifier).refresh(),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SearchField extends StatelessWidget {
  const _SearchField({
    required this.controller,
    required this.onSubmitted,
    required this.onClear,
  });

  final TextEditingController controller;
  final ValueChanged<String> onSubmitted;
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 6),
      child: TextField(
        controller: controller,
        onSubmitted: onSubmitted,
        style: const TextStyle(color: AppColors.onBackground, fontSize: 14),
        decoration: InputDecoration(
          hintText: 'Search news',
          hintStyle: const TextStyle(color: AppColors.muted),
          prefixIcon: const Icon(Icons.search, color: AppColors.muted, size: 20),
          suffixIcon: controller.text.isEmpty
              ? null
              : IconButton(
                  icon: const Icon(Icons.close, color: AppColors.muted, size: 18),
                  onPressed: onClear,
                ),
          filled: true,
          fillColor: AppColors.card,
          contentPadding: const EdgeInsets.symmetric(vertical: 10),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(10),
            borderSide: BorderSide.none,
          ),
        ),
      ),
    );
  }
}

class _FilterRow extends StatelessWidget {
  const _FilterRow({
    required this.sentiment,
    required this.impact,
    required this.onSentiment,
    required this.onImpact,
    required this.onCategory,
  });

  final NewsSentiment? sentiment;
  final NewsImpact? impact;
  final ValueChanged<NewsSentiment?> onSentiment;
  final ValueChanged<NewsImpact?> onImpact;
  final ValueChanged<NewsCategory?> onCategory;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
      child: Wrap(
        spacing: 8,
        runSpacing: 8,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          _chipAll(
            selected: sentiment == null,
            onTap: () => onSentiment(null),
          ),
          for (final value in NewsSentiment.values)
            ChoiceChip(
              label: Text(value.label),
              selected: sentiment == value,
              onSelected: (_) => onSentiment(value),
              labelStyle: TextStyle(
                color: sentiment == value ? AppColors.background : AppColors.muted,
                fontSize: 12,
                fontWeight: FontWeight.w700,
              ),
              backgroundColor: AppColors.card,
              selectedColor: AppColors.accent,
              side: BorderSide.none,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(8),
              ),
            ),
          for (final value in NewsImpact.values)
            ChoiceChip(
              label: Text(value.label),
              selected: impact == value,
              onSelected: (_) => onImpact(value),
              labelStyle: TextStyle(
                color: impact == value ? AppColors.background : AppColors.muted,
                fontSize: 12,
                fontWeight: FontWeight.w700,
              ),
              backgroundColor: AppColors.card,
              selectedColor: AppColors.accent,
              side: BorderSide.none,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(8),
              ),
            ),
          PopupMenuButton<NewsCategory>(
            tooltip: 'Filter by category',
            icon: const Icon(Icons.tune, color: AppColors.muted, size: 20),
            color: AppColors.card,
            onSelected: onCategory,
            itemBuilder: (_) => [
              const PopupMenuItem(
                value: null,
                child: Text('All categories'),
              ),
              for (final value in NewsCategory.values)
                PopupMenuItem(value: value, child: Text(value.label)),
            ],
          ),
        ],
      ),
    );
  }

  Widget _chipAll({required bool selected, required VoidCallback onTap}) {
    return ChoiceChip(
      label: const Text('All'),
      selected: selected,
      onSelected: (_) => onTap(),
      labelStyle: TextStyle(
        color: selected ? AppColors.background : AppColors.muted,
        fontSize: 12,
        fontWeight: FontWeight.w700,
      ),
      backgroundColor: AppColors.card,
      selectedColor: AppColors.accent,
      side: BorderSide.none,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
    );
  }
}

class _NewsList extends ConsumerStatefulWidget {
  const _NewsList({
    required this.asyncFeed,
    required this.onArticleTap,
    required this.onRefresh,
    this.scrollController,
  });

  final AsyncValue<NewsFeedData> asyncFeed;
  final ValueChanged<NewsArticle> onArticleTap;
  final Future<void> Function() onRefresh;
  final ScrollController? scrollController;

  @override
  ConsumerState<_NewsList> createState() => _NewsListState();
}

class _NewsListState extends ConsumerState<_NewsList> {
  @override
  Widget build(BuildContext context) {
    return widget.asyncFeed.when(
      loading: () => const Center(
        child: CircularProgressIndicator(color: AppColors.accent),
      ),
      error: (error, _) => _ErrorView(
        message: '$error',
        onRetry: () => ref.read(newsFeedControllerProvider.notifier).refresh(),
      ),
      data: (feed) {
        if (feed.articles.isEmpty) {
          return RefreshIndicator(
            onRefresh: widget.onRefresh,
            color: AppColors.accent,
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: const [
                SizedBox(height: 160),
                Center(
                  child: Text(
                    'No news matches your filters',
                    style: TextStyle(color: AppColors.muted, fontSize: 14),
                  ),
                ),
              ],
            ),
          );
        }
        return RefreshIndicator(
          onRefresh: widget.onRefresh,
          color: AppColors.accent,
          child: ListView.builder(
            controller: widget.scrollController,
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.only(bottom: 24, top: 4),
            itemCount: feed.articles.length + (feed.hasNext ? 1 : 0),
            itemBuilder: (context, index) {
              if (index >= feed.articles.length) {
                return const Padding(
                  padding: EdgeInsets.symmetric(vertical: 16),
                  child: Center(
                    child: SizedBox(
                      width: 22,
                      height: 22,
                      child: CircularProgressIndicator(
                        color: AppColors.accent,
                        strokeWidth: 2,
                      ),
                    ),
                  ),
                );
              }
              final article = feed.articles[index];
              return NewsArticleCard(
                article: article,
                onTap: () => widget.onArticleTap(article),
              );
            },
          ),
        );
      },
    );
  }
}

class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.message, required this.onRetry});

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
            const Icon(Icons.cloud_off, color: AppColors.muted, size: 40),
            const SizedBox(height: 12),
            const Text(
              'Could not load news',
              style: TextStyle(
                color: AppColors.onBackground,
                fontWeight: FontWeight.w800,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              message,
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
              textAlign: TextAlign.center,
              style: const TextStyle(color: AppColors.muted, fontSize: 12),
            ),
            const SizedBox(height: 12),
            FilledButton.icon(
              onPressed: onRetry,
              style: FilledButton.styleFrom(
                backgroundColor: AppColors.accent,
                foregroundColor: AppColors.background,
              ),
              icon: const Icon(Icons.refresh, size: 18),
              label: const Text('Retry'),
            ),
          ],
        ),
      ),
    );
  }
}