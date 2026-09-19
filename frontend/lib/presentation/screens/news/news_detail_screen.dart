import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../domain/entities/news.dart';
import '../../providers/news_providers.dart';
import '../../widgets/news_article_card.dart';
import 'asset_news_screen.dart';

class NewsDetailScreen extends ConsumerWidget {
  const NewsDetailScreen({
    super.key,
    required this.articleId,
    this.initial,
  });

  final String articleId;
  final NewsArticle? initial;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncDetail = ref.watch(newsDetailProvider(articleId));
    final article = asyncDetail.value?.article ?? initial;

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        title: Text(
          article?.sourceName.toUpperCase() ?? 'Article',
          style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w800),
        ),
      ),
      body: asyncDetail.when(
        loading: () => article == null
            ? const Center(
                child: CircularProgressIndicator(color: AppColors.accent),
              )
            : _DetailBody(article: article),
        error: (error, _) => article == null
            ? _DetailError(
                message: '$error',
                onRetry: () => ref.invalidate(newsDetailProvider(articleId)),
              )
            : _DetailBody(article: article),
        data: (detail) => _DetailBody(
          article: detail.article,
          content: detail.content,
          processingStatus: detail.processingStatus,
        ),
      ),
    );
  }
}

class _DetailBody extends StatelessWidget {
  const _DetailBody({
    required this.article,
    this.content,
    this.processingStatus,
  });

  final NewsArticle article;
  final String? content;
  final NewsProcessingStatus? processingStatus;

  @override
  Widget build(BuildContext context) {
    final bodyText = content ?? article.summary;

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
      children: [
        _MetaRow(article: article),
        const SizedBox(height: 12),
        Text(
          article.title,
          style: const TextStyle(
            color: AppColors.onBackground,
            fontSize: 20,
            fontWeight: FontWeight.w900,
            height: 1.25,
          ),
        ),
        const SizedBox(height: 8),
        Text(
          timeAgoLabel(article.publishedAt),
          style: const TextStyle(color: AppColors.muted, fontSize: 13),
        ),
        if (article.author != null) ...[
          const SizedBox(height: 4),
          Text(
            'By ${article.author}',
            style: const TextStyle(color: AppColors.muted, fontSize: 13),
          ),
        ],
        const Divider(height: 28, color: AppColors.card),
        if (bodyText != null && bodyText.isNotEmpty) ...[
          Text(
            bodyText,
            style: const TextStyle(
              color: AppColors.onCard,
              fontSize: 15,
              height: 1.5,
            ),
          ),
          const SizedBox(height: 24),
        ],
        if (article.assets.isNotEmpty) ...[
          Text(
            'Affected assets',
            style: _sectionStyle(),
          ),
          const SizedBox(height: 8),
          for (final asset in article.assets)
            _AssetRow(
              asset: asset,
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) => AssetNewsScreen(symbol: asset.symbol),
                ),
              ),
            ),
          const SizedBox(height: 24),
        ],
        if (processingStatus != null) ...[
          Text('Processing status', style: _sectionStyle()),
          const SizedBox(height: 6),
          Text(
            processingStatus!.label,
            style: const TextStyle(color: AppColors.muted, fontSize: 13),
          ),
        ],
        if (article.sourceUrl != null) ...[
          const SizedBox(height: 24),
          OutlinedButton.icon(
            onPressed: () => _openSource(context, article.sourceUrl!),
            style: OutlinedButton.styleFrom(
              foregroundColor: AppColors.accent,
              side: const BorderSide(color: AppColors.accent),
            ),
            icon: const Icon(Icons.open_in_new, size: 18),
            label: const Text('Open original article'),
          ),
        ],
      ],
    );
  }

  TextStyle _sectionStyle() {
    return const TextStyle(
      color: AppColors.onBackground,
      fontSize: 15,
      fontWeight: FontWeight.w800,
    );
  }

  void _openSource(BuildContext context, String url) {
    final message = SnackBar(
      content: Text('Opening $url in your browser...'),
      behavior: SnackBarBehavior.floating,
    );
    ScaffoldMessenger.of(context).showSnackBar(message);
  }
}

class _MetaRow extends StatelessWidget {
  const _MetaRow({required this.article});

  final NewsArticle article;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        NewsSentimentBadge(sentiment: article.sentiment),
        const SizedBox(width: 6),
        NewsImpactBadge(impact: article.impactLevel),
        const SizedBox(width: 6),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
          decoration: BoxDecoration(
            color: AppColors.onCard.withValues(alpha: 0.12),
            borderRadius: BorderRadius.circular(6),
          ),
          child: Text(
            article.category.label,
            style: const TextStyle(color: AppColors.muted, fontSize: 11),
          ),
        ),
        const Spacer(),
        if (article.newsScore != null)
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                'News score',
                style: const TextStyle(color: AppColors.muted, fontSize: 10),
              ),
              Text(
                article.newsScore!.toStringAsFixed(1),
                style: const TextStyle(
                  color: AppColors.accent,
                  fontSize: 16,
                  fontWeight: FontWeight.w900,
                ),
              ),
            ],
          ),
      ],
    );
  }
}

class _AssetRow extends StatelessWidget {
  const _AssetRow({required this.asset, required this.onTap});

  final NewsAsset asset;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Row(
          children: [
            Container(
              width: 36,
              height: 36,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: AppColors.accent.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                asset.symbol,
                style: const TextStyle(
                  color: AppColors.accent,
                  fontSize: 11,
                  fontWeight: FontWeight.w900,
                ),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    asset.name,
                    style: const TextStyle(
                      color: AppColors.onBackground,
                      fontSize: 14,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  Text(
                    asset.relationshipType == NewsAssetRelationshipType.primary
                        ? 'Primary subject'
                        : 'Mentioned',
                    style: const TextStyle(color: AppColors.muted, fontSize: 12),
                  ),
                ],
              ),
            ),
            if (asset.relevanceScore != null)
              Text(
                asset.relevanceScore!.toStringAsFixed(2),
                style: const TextStyle(
                  color: AppColors.muted,
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                ),
              ),
            const SizedBox(width: 4),
            const Icon(
              Icons.chevron_right,
              color: AppColors.muted,
              size: 20,
            ),
          ],
        ),
      ),
    );
  }
}

class _DetailError extends StatelessWidget {
  const _DetailError({required this.message, required this.onRetry});

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