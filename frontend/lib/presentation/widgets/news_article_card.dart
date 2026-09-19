import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../domain/entities/news.dart';

String timeAgoLabel(DateTime time, {DateTime? now}) {
  final reference = now ?? DateTime.now();
  final difference = reference.difference(time.toLocal());
  if (difference.inMinutes < 1) return 'just now';
  if (difference.inMinutes < 60) return '${difference.inMinutes}m ago';
  if (difference.inHours < 24) return '${difference.inHours}h ago';
  if (difference.inDays < 7) return '${difference.inDays}d ago';
  return '${time.day}/${time.month}/${time.year}';
}

class NewsSentimentBadge extends StatelessWidget {
  const NewsSentimentBadge({super.key, required this.sentiment});

  final NewsSentiment sentiment;

  @override
  Widget build(BuildContext context) {
    final (label, color) = switch (sentiment) {
      NewsSentiment.positive => (
        'Positive',
        AppColors.accent,
      ),
      NewsSentiment.negative => ('Negative', AppColors.loss),
      NewsSentiment.neutral => ('Neutral', AppColors.muted),
    };
    return _Badge(text: label, color: color);
  }
}

class NewsImpactBadge extends StatelessWidget {
  const NewsImpactBadge({super.key, required this.impact});

  final NewsImpact impact;

  @override
  Widget build(BuildContext context) {
    final (label, color) = switch (impact) {
      NewsImpact.critical => ('Critical', AppColors.loss),
      NewsImpact.high => ('High', AppColors.accent),
      NewsImpact.medium => ('Medium', AppColors.onCard),
      NewsImpact.low => ('Low', AppColors.muted),
    };
    return _Badge(text: label, color: color);
  }
}

class _Badge extends StatelessWidget {
  const _Badge({required this.text, required this.color});

  final String text;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.14),
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: color.withValues(alpha: 0.5)),
      ),
      child: Text(
        text,
        style: TextStyle(
          color: color,
          fontSize: 11,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }
}

class NewsArticleCard extends StatelessWidget {
  const NewsArticleCard({
    super.key,
    required this.article,
    required this.onTap,
  });

  final NewsArticle article;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: AppColors.card,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    article.sourceName.toUpperCase(),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: AppColors.muted,
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 0.6,
                    ),
                  ),
                ),
                Text(
                  timeAgoLabel(article.publishedAt),
                  style: const TextStyle(color: AppColors.muted, fontSize: 11),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              article.title,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                color: AppColors.onBackground,
                fontSize: 15,
                fontWeight: FontWeight.w800,
              ),
            ),
            if (article.summary != null && article.summary!.isNotEmpty) ...[
              const SizedBox(height: 6),
              Text(
                article.summary!,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(color: AppColors.muted, fontSize: 13),
              ),
            ],
            const SizedBox(height: 10),
            Row(
              children: [
                NewsSentimentBadge(sentiment: article.sentiment),
                const SizedBox(width: 6),
                NewsImpactBadge(impact: article.impactLevel),
                const SizedBox(width: 6),
                _CategoryBadge(category: article.category),
                const Spacer(),
                if (article.newsScore != null)
                  Text(
                    'Score ${article.newsScore!.toStringAsFixed(1)}',
                    style: const TextStyle(
                      color: AppColors.muted,
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
              ],
            ),
            if (article.assets.isNotEmpty) ...[
              const SizedBox(height: 10),
              Wrap(
                spacing: 6,
                runSpacing: 6,
                children: [
                  for (final asset in article.assets)
                    _AssetChip(asset: asset),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _CategoryBadge extends StatelessWidget {
  const _CategoryBadge({required this.category});

  final NewsCategory category;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: AppColors.onCard.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(
        category.label,
        style: const TextStyle(color: AppColors.muted, fontSize: 11),
      ),
    );
  }
}

class _AssetChip extends StatelessWidget {
  const _AssetChip({required this.asset});

  final NewsAsset asset;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: AppColors.accent.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(
        '${asset.symbol} · ${asset.relevanceScore?.toStringAsFixed(2) ?? '0.00'}',
        style: const TextStyle(
          color: AppColors.accent,
          fontSize: 11,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}