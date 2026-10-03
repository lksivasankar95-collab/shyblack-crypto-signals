import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:cryptosignals/core/di/providers.dart';
import 'package:cryptosignals/core/theme/app_theme.dart';
import 'package:cryptosignals/domain/entities/news.dart';
import 'package:cryptosignals/domain/repositories/news_repository.dart';
import 'package:cryptosignals/presentation/screens/news/news_screen.dart';

void main() {
  SharedPreferences.setMockInitialValues({});

  testWidgets('news screen lists articles from the feed', (tester) async {
    final container = ProviderContainer(
      overrides: [
        newsRepositoryProvider.overrideWith(
          (ref) => _FakeNewsRepository(),
        ),
      ],
    );
    addTearDown(container.dispose);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(
          theme: AppTheme.dark(),
          darkTheme: AppTheme.dark(),
          themeMode: ThemeMode.dark,
          home: const Scaffold(body: NewsScreen()),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.text('Search news'), findsOneWidget);
    expect(find.text('Bitcoin ETF inflows hit record high'), findsOneWidget);
    expect(find.text('Ethereum upgrade ships on mainnet'), findsOneWidget);
    expect(find.text('COINDESK'), findsWidgets);
    expect(find.text('Positive'), findsWidgets);
    expect(find.textContaining('BTC'), findsOneWidget);
  });

  testWidgets('tapping an article opens the detail route', (tester) async {
    final container = ProviderContainer(
      overrides: [
        newsRepositoryProvider.overrideWith(
          (ref) => _FakeNewsRepository(),
        ),
      ],
    );
    addTearDown(container.dispose);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(
          theme: AppTheme.dark(),
          darkTheme: AppTheme.dark(),
          themeMode: ThemeMode.dark,
          home: const Scaffold(body: NewsScreen()),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    await tester.tap(find.text('Bitcoin ETF inflows hit record high'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.text('Affected assets'), findsOneWidget);
    expect(find.text('Open original article'), findsOneWidget);
  });

  testWidgets('news screen shows empty state when there is no news', (
    tester,
  ) async {
    final container = ProviderContainer(
      overrides: [
        newsRepositoryProvider.overrideWith(
          (ref) => _EmptyNewsRepositoryStub(),
        ),
      ],
    );
    addTearDown(container.dispose);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(
          theme: AppTheme.dark(),
          darkTheme: AppTheme.dark(),
          themeMode: ThemeMode.dark,
          home: const Scaffold(body: NewsScreen()),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.text('No news matches your filters'), findsOneWidget);
  });
}

NewsArticle _article({
  required String id,
  required String title,
  required NewsCategory category,
  required NewsSentiment sentiment,
  required NewsImpact impact,
  List<NewsAsset> assets = const [],
}) {
  return NewsArticle(
    id: id,
    sourceName: 'CoinDesk',
    sourceUrl: 'https://coindesk.com/$id',
    title: title,
    summary: 'A short summary for testing purposes.',
    publishedAt: DateTime.now().subtract(const Duration(hours: 1)),
    category: category,
    sentiment: sentiment,
    sentimentScore: 0.7,
    impactLevel: impact,
    impactScore: 6.0,
    newsScore: 7.2,
    confidenceScore: 0.85,
    assets: assets,
  );
}

class _FakeNewsRepository implements NewsRepository {
  @override
  Future<NewsPage> getNews({
    String? category,
    String? sentiment,
    String? impact,
    String? source,
    String? query,
    int page = 0,
    int size = 20,
    String? sort,
    String? direction,
  }) async {
    return NewsPage(
      items: [
        _article(
          id: '1',
          title: 'Bitcoin ETF inflows hit record high',
          category: NewsCategory.etf,
          sentiment: NewsSentiment.positive,
          impact: NewsImpact.high,
          assets: const [
            NewsAsset(
              symbol: 'BTC',
              name: 'Bitcoin',
              relevanceScore: 1.0,
              relationshipType: NewsAssetRelationshipType.primary,
            ),
          ],
        ),
        _article(
          id: '2',
          title: 'Ethereum upgrade ships on mainnet',
          category: NewsCategory.protocolUpdate,
          sentiment: NewsSentiment.neutral,
          impact: NewsImpact.medium,
        ),
      ],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
      hasNext: false,
    );
  }

  @override
  Future<NewsPage> getAssetNews(
    String symbol, {
    String? category,
    String? sentiment,
    String? impact,
    int page = 0,
    int size = 20,
    String? sort,
    String? direction,
  }) async => NewsPage.empty;

  @override
  Future<NewsArticleDetail> getDetail(String id) async {
    final article = _article(
      id: id,
      title: 'Bitcoin ETF inflows hit record high',
      category: NewsCategory.etf,
      sentiment: NewsSentiment.positive,
      impact: NewsImpact.high,
      assets: const [
        NewsAsset(
          symbol: 'BTC',
          name: 'Bitcoin',
          relevanceScore: 1.0,
          relationshipType: NewsAssetRelationshipType.primary,
        ),
      ],
    );
    return NewsArticleDetail(
      article: article,
      content: 'Full detail body of the article for the test.',
      fetchedAt: DateTime.now(),
      processingStatus: NewsProcessingStatus.processed,
    );
  }

  @override
  Future<NewsMeta> getMeta() async => NewsMeta(
    totalArticles: 2,
    sources: const ['CoinDesk'],
    categories: const [NewsCategory.etf],
    sentiments: const [NewsSentiment.positive],
    impacts: const [NewsImpact.high],
  );

  @override
  Future<NewsContext> getAssetContext(String symbol, {int? windowHours}) {
    throw UnimplementedError();
  }
}

class _EmptyNewsRepositoryStub implements NewsRepository {
  @override
  Future<NewsPage> getNews({
    String? category,
    String? sentiment,
    String? impact,
    String? source,
    String? query,
    int page = 0,
    int size = 20,
    String? sort,
    String? direction,
  }) async => NewsPage.empty;

  @override
  Future<NewsPage> getAssetNews(
    String symbol, {
    String? category,
    String? sentiment,
    String? impact,
    int page = 0,
    int size = 20,
    String? sort,
    String? direction,
  }) async => NewsPage.empty;

  @override
  Future<NewsArticleDetail> getDetail(String id) {
    throw UnimplementedError();
  }

  @override
  Future<NewsMeta> getMeta() async => NewsMeta(
    totalArticles: 0,
    sources: const [],
    categories: const [],
    sentiments: const [],
    impacts: const [],
  );

  @override
  Future<NewsContext> getAssetContext(String symbol, {int? windowHours}) {
    throw UnimplementedError();
  }
}