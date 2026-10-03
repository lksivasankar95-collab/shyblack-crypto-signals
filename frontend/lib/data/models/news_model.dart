import '../../domain/entities/news.dart';

class NewsAssetModel {
  const NewsAssetModel({
    required this.symbol,
    required this.name,
    this.relevanceScore,
    required this.relationshipType,
  });

  final String symbol;
  final String name;
  final double? relevanceScore;
  final NewsAssetRelationshipType relationshipType;

  factory NewsAssetModel.fromJson(Map<String, dynamic> json) {
    return NewsAssetModel(
      symbol: json['symbol'] as String? ?? '',
      name: json['name'] as String? ?? '',
      relevanceScore: (json['relevanceScore'] as num?)?.toDouble(),
      relationshipType: NewsAssetRelationshipTypeLabel.parse(
        json['relationshipType'] as String?,
      ),
    );
  }

  NewsAsset toEntity() {
    return NewsAsset(
      symbol: symbol,
      name: name,
      relevanceScore: relevanceScore,
      relationshipType: relationshipType,
    );
  }
}

class NewsArticleModel {
  const NewsArticleModel({
    required this.id,
    required this.sourceName,
    this.sourceUrl,
    required this.title,
    this.summary,
    this.imageUrl,
    this.author,
    required this.publishedAt,
    required this.category,
    required this.sentiment,
    this.sentimentScore,
    required this.impactLevel,
    this.impactScore,
    this.newsScore,
    this.confidenceScore,
    this.assets = const [],
  });

  final String id;
  final String sourceName;
  final String? sourceUrl;
  final String title;
  final String? summary;
  final String? imageUrl;
  final String? author;
  final DateTime publishedAt;
  final NewsCategory category;
  final NewsSentiment sentiment;
  final double? sentimentScore;
  final NewsImpact impactLevel;
  final double? impactScore;
  final double? newsScore;
  final double? confidenceScore;
  final List<NewsAssetModel> assets;

  factory NewsArticleModel.fromJson(Map<String, dynamic> json) {
    return NewsArticleModel(
      id: json['id'] as String? ?? '',
      sourceName: json['sourceName'] as String? ?? '',
      sourceUrl: json['sourceUrl'] as String?,
      title: json['title'] as String? ?? '',
      summary: json['summary'] as String?,
      imageUrl: json['imageUrl'] as String?,
      author: json['author'] as String?,
      publishedAt:
          _parseDate(json['publishedAt']) ?? DateTime.fromMillisecondsSinceEpoch(0),
      category: NewsCategoryLabel.parse(json['category'] as String?),
      sentiment: NewsSentimentLabel.parse(json['sentiment'] as String?),
      sentimentScore: (json['sentimentScore'] as num?)?.toDouble(),
      impactLevel: NewsImpactLabel.parse(json['impactLevel'] as String?),
      impactScore: (json['impactScore'] as num?)?.toDouble(),
      newsScore: (json['newsScore'] as num?)?.toDouble(),
      confidenceScore: (json['confidenceScore'] as num?)?.toDouble(),
      assets: [
        for (final item in (json['assets'] as List<dynamic>? ?? const []))
          NewsAssetModel.fromJson(item as Map<String, dynamic>),
      ],
    );
  }

  NewsArticle toEntity() {
    return NewsArticle(
      id: id,
      sourceName: sourceName,
      sourceUrl: sourceUrl,
      title: title,
      summary: summary,
      imageUrl: imageUrl,
      author: author,
      publishedAt: publishedAt,
      category: category,
      sentiment: sentiment,
      sentimentScore: sentimentScore,
      impactLevel: impactLevel,
      impactScore: impactScore,
      newsScore: newsScore,
      confidenceScore: confidenceScore,
      assets: [for (final asset in assets) asset.toEntity()],
    );
  }

  static DateTime? _parseDate(dynamic value) {
    if (value == null) {
      return null;
    }
    if (value is int) {
      return DateTime.fromMillisecondsSinceEpoch(value);
    }
    return DateTime.tryParse(value.toString());
  }
}

class NewsArticleDetailModel {
  const NewsArticleDetailModel({
    required this.article,
    this.content,
    required this.fetchedAt,
    required this.processingStatus,
  });

  final NewsArticleModel article;
  final String? content;
  final DateTime fetchedAt;
  final NewsProcessingStatus processingStatus;

  static NewsArticleDetailModel fromJson(Map<String, dynamic> json) {
    return NewsArticleDetailModel(
      article: NewsArticleModel.fromJson(json),
      content: json['content'] as String?,
      fetchedAt:
          NewsArticleModel._parseDate(json['fetchedAt']) ??
          DateTime.fromMillisecondsSinceEpoch(0),
      processingStatus: NewsProcessingStatusLabel.parse(
        json['processingStatus'] as String?,
      ),
    );
  }

  NewsArticleDetail toEntity() {
    return NewsArticleDetail(
      article: article.toEntity(),
      content: content,
      fetchedAt: fetchedAt,
      processingStatus: processingStatus,
    );
  }
}

class NewsPageModel {
  const NewsPageModel({
    required this.items,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
    required this.hasNext,
  });

  final List<NewsArticleModel> items;
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;
  final bool hasNext;

  factory NewsPageModel.fromJson(Map<String, dynamic> json) {
    final itemsJson = json['items'] as List<dynamic>? ?? const [];
    return NewsPageModel(
      items: [
        for (final item in itemsJson)
          NewsArticleModel.fromJson(item as Map<String, dynamic>),
      ],
      page: (json['page'] as num?)?.toInt() ?? 0,
      size: (json['size'] as num?)?.toInt() ?? 0,
      totalElements: (json['totalElements'] as num?)?.toInt() ?? 0,
      totalPages: (json['totalPages'] as num?)?.toInt() ?? 0,
      hasNext: json['hasNext'] as bool? ?? false,
    );
  }

  NewsPage toEntity() {
    return NewsPage(
      items: [for (final item in items) item.toEntity()],
      page: page,
      size: size,
      totalElements: totalElements,
      totalPages: totalPages,
      hasNext: hasNext,
    );
  }
}

class NewsMetaModel {
  const NewsMetaModel({
    required this.totalArticles,
    required this.sources,
    required this.categories,
    required this.sentiments,
    required this.impacts,
    this.latestFetchedAt,
  });

  final int totalArticles;
  final List<String> sources;
  final List<NewsCategory> categories;
  final List<NewsSentiment> sentiments;
  final List<NewsImpact> impacts;
  final DateTime? latestFetchedAt;

  factory NewsMetaModel.fromJson(Map<String, dynamic> json) {
    return NewsMetaModel(
      totalArticles: (json['totalArticles'] as num?)?.toInt() ?? 0,
      sources: [
        for (final item in (json['sources'] as List<dynamic>? ?? const []))
          item.toString(),
      ],
      categories: [
        for (final item in (json['categories'] as List<dynamic>? ?? const []))
          NewsCategoryLabel.parse(item.toString()),
      ],
      sentiments: [
        for (final item in (json['sentiments'] as List<dynamic>? ?? const []))
          NewsSentimentLabel.parse(item.toString()),
      ],
      impacts: [
        for (final item in (json['impacts'] as List<dynamic>? ?? const []))
          NewsImpactLabel.parse(item.toString()),
      ],
      latestFetchedAt: NewsArticleModel._parseDate(json['latestFetchedAt']),
    );
  }

  NewsMeta toEntity() {
    return NewsMeta(
      totalArticles: totalArticles,
      sources: sources,
      categories: categories,
      sentiments: sentiments,
      impacts: impacts,
      latestFetchedAt: latestFetchedAt,
    );
  }
}

class NewsContextModel {
  const NewsContextModel({
    required this.symbol,
    this.newsScore,
    required this.sentiment,
    this.sentimentScore,
    required this.impactLevel,
    this.confidenceScore,
    required this.articleCount,
    this.latestHighImpactNews,
    required this.calculatedAt,
  });

  final String symbol;
  final double? newsScore;
  final NewsSentiment sentiment;
  final double? sentimentScore;
  final NewsImpact impactLevel;
  final double? confidenceScore;
  final int articleCount;
  final NewsArticleModel? latestHighImpactNews;
  final DateTime calculatedAt;

  factory NewsContextModel.fromJson(Map<String, dynamic> json) {
    final latest = json['latestHighImpactNews'];
    return NewsContextModel(
      symbol: json['symbol'] as String? ?? '',
      newsScore: (json['newsScore'] as num?)?.toDouble(),
      sentiment: NewsSentimentLabel.parse(json['sentiment'] as String?),
      sentimentScore: (json['sentimentScore'] as num?)?.toDouble(),
      impactLevel: NewsImpactLabel.parse(json['impactLevel'] as String?),
      confidenceScore: (json['confidenceScore'] as num?)?.toDouble(),
      articleCount: (json['articleCount'] as num?)?.toInt() ?? 0,
      latestHighImpactNews: latest == null
          ? null
          : NewsArticleModel.fromJson(latest as Map<String, dynamic>),
      calculatedAt:
          NewsArticleModel._parseDate(json['calculatedAt']) ??
          DateTime.fromMillisecondsSinceEpoch(0),
    );
  }

  NewsContext toEntity() {
    return NewsContext(
      symbol: symbol,
      newsScore: newsScore,
      sentiment: sentiment,
      sentimentScore: sentimentScore,
      impactLevel: impactLevel,
      confidenceScore: confidenceScore,
      articleCount: articleCount,
      latestHighImpactNews: latestHighImpactNews?.toEntity(),
      calculatedAt: calculatedAt,
    );
  }
}