enum NewsCategory {
  regulation,
  etf,
  exchange,
  listing,
  delisting,
  partnership,
  adoption,
  technology,
  network,
  security,
  hack,
  exploit,
  funding,
  investment,
  tokenUnlock,
  tokenBurn,
  governance,
  protocolUpdate,
  defi,
  nft,
  macro,
  market,
  mining,
  legal,
  other,
}

extension NewsCategoryLabel on NewsCategory {
  String get apiValue => name.toUpperCase();

  String get label => switch (this) {
    NewsCategory.regulation => 'Regulation',
    NewsCategory.etf => 'ETF',
    NewsCategory.exchange => 'Exchange',
    NewsCategory.listing => 'Listing',
    NewsCategory.delisting => 'Delisting',
    NewsCategory.partnership => 'Partnership',
    NewsCategory.adoption => 'Adoption',
    NewsCategory.technology => 'Technology',
    NewsCategory.network => 'Network',
    NewsCategory.security => 'Security',
    NewsCategory.hack => 'Hack',
    NewsCategory.exploit => 'Exploit',
    NewsCategory.funding => 'Funding',
    NewsCategory.investment => 'Investment',
    NewsCategory.tokenUnlock => 'Token Unlock',
    NewsCategory.tokenBurn => 'Token Burn',
    NewsCategory.governance => 'Governance',
    NewsCategory.protocolUpdate => 'Protocol Update',
    NewsCategory.defi => 'DeFi',
    NewsCategory.nft => 'NFT',
    NewsCategory.macro => 'Macro',
    NewsCategory.market => 'Market',
    NewsCategory.mining => 'Mining',
    NewsCategory.legal => 'Legal',
    NewsCategory.other => 'Other',
  };

  static NewsCategory parse(String? value) {
    if (value == null) return NewsCategory.other;
    return switch (value.toUpperCase().replaceAll('-', '_').replaceAll(' ', '_')) {
      'REGULATION' => NewsCategory.regulation,
      'ETF' => NewsCategory.etf,
      'EXCHANGE' => NewsCategory.exchange,
      'LISTING' => NewsCategory.listing,
      'DELISTING' => NewsCategory.delisting,
      'PARTNERSHIP' => NewsCategory.partnership,
      'ADOPTION' => NewsCategory.adoption,
      'TECHNOLOGY' => NewsCategory.technology,
      'NETWORK' => NewsCategory.network,
      'SECURITY' => NewsCategory.security,
      'HACK' => NewsCategory.hack,
      'EXPLOIT' => NewsCategory.exploit,
      'FUNDING' => NewsCategory.funding,
      'INVESTMENT' => NewsCategory.investment,
      'TOKEN_UNLOCK' => NewsCategory.tokenUnlock,
      'TOKEN_BURN' => NewsCategory.tokenBurn,
      'GOVERNANCE' => NewsCategory.governance,
      'PROTOCOL_UPDATE' => NewsCategory.protocolUpdate,
      'DEFI' => NewsCategory.defi,
      'NFT' => NewsCategory.nft,
      'MACRO' => NewsCategory.macro,
      'MARKET' => NewsCategory.market,
      'MINING' => NewsCategory.mining,
      'LEGAL' => NewsCategory.legal,
      _ => NewsCategory.other,
    };
  }
}

enum NewsSentiment { positive, negative, neutral }

extension NewsSentimentLabel on NewsSentiment {
  String get apiValue => name.toUpperCase();

  String get label => switch (this) {
    NewsSentiment.positive => 'Positive',
    NewsSentiment.negative => 'Negative',
    NewsSentiment.neutral => 'Neutral',
  };

  static NewsSentiment parse(String? value) {
    return switch (value?.toUpperCase()) {
      'POSITIVE' => NewsSentiment.positive,
      'NEGATIVE' => NewsSentiment.negative,
      _ => NewsSentiment.neutral,
    };
  }
}

enum NewsImpact { low, medium, high, critical }

extension NewsImpactLabel on NewsImpact {
  String get apiValue => name.toUpperCase();

  String get label => switch (this) {
    NewsImpact.low => 'Low',
    NewsImpact.medium => 'Medium',
    NewsImpact.high => 'High',
    NewsImpact.critical => 'Critical',
  };

  static NewsImpact parse(String? value) {
    return switch (value?.toUpperCase()) {
      'MEDIUM' => NewsImpact.medium,
      'HIGH' => NewsImpact.high,
      'CRITICAL' => NewsImpact.critical,
      _ => NewsImpact.low,
    };
  }
}

enum NewsAssetRelationshipType { primary, mentioned }

extension NewsAssetRelationshipTypeLabel on NewsAssetRelationshipType {
  String get apiValue => name.toUpperCase();

  static NewsAssetRelationshipType parse(String? value) {
    return value?.toUpperCase() == 'PRIMARY'
        ? NewsAssetRelationshipType.primary
        : NewsAssetRelationshipType.mentioned;
  }
}

enum NewsProcessingStatus {
  new_,
  processed,
  partiallyProcessed,
  failed,
  unsupported,
}

extension NewsProcessingStatusLabel on NewsProcessingStatus {
  String get label => switch (this) {
    NewsProcessingStatus.new_ => 'New',
    NewsProcessingStatus.processed => 'Processed',
    NewsProcessingStatus.partiallyProcessed => 'Partially Processed',
    NewsProcessingStatus.failed => 'Failed',
    NewsProcessingStatus.unsupported => 'Unsupported',
  };

  static NewsProcessingStatus parse(String? value) {
    return switch (value?.toUpperCase()) {
      'PROCESSED' => NewsProcessingStatus.processed,
      'PARTIALLY_PROCESSED' => NewsProcessingStatus.partiallyProcessed,
      'FAILED' => NewsProcessingStatus.failed,
      'UNSUPPORTED' => NewsProcessingStatus.unsupported,
      _ => NewsProcessingStatus.new_,
    };
  }
}

class NewsAsset {
  const NewsAsset({
    required this.symbol,
    required this.name,
    this.relevanceScore,
    required this.relationshipType,
  });

  final String symbol;
  final String name;
  final double? relevanceScore;
  final NewsAssetRelationshipType relationshipType;
}

class NewsArticle {
  const NewsArticle({
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
  final List<NewsAsset> assets;
}

class NewsArticleDetail {
  const NewsArticleDetail({
    required this.article,
    this.content,
    required this.fetchedAt,
    required this.processingStatus,
  });

  final NewsArticle article;
  final String? content;
  final DateTime fetchedAt;
  final NewsProcessingStatus processingStatus;
}

class NewsPage {
  const NewsPage({
    required this.items,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
    required this.hasNext,
  });

  final List<NewsArticle> items;
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;
  final bool hasNext;

  static const NewsPage empty = NewsPage(
    items: [],
    page: 0,
    size: 0,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
  );
}

class NewsMeta {
  const NewsMeta({
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
}

class NewsContext {
  const NewsContext({
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
  final NewsArticle? latestHighImpactNews;
  final DateTime calculatedAt;
}