import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:cryptosignals/core/di/providers.dart';
import 'package:cryptosignals/domain/entities/news.dart';
import 'package:cryptosignals/domain/repositories/news_repository.dart';
import 'package:cryptosignals/presentation/providers/news_providers.dart';

void main() {
  test(
    'refresh resets the pagination cursor so loadMore resumes at page 1',
    () async {
      final repo = _PagingNewsRepository();
      final container = ProviderContainer(
        overrides: [newsRepositoryProvider.overrideWith((ref) => repo)],
      );
      addTearDown(container.dispose);

      // Initial build -> page 0.
      await container.read(newsFeedControllerProvider.future);
      final controller = container.read(newsFeedControllerProvider.notifier);

      await controller.loadMore(); // page 1
      await controller.refresh(); // page 0 (must reset the cursor)
      await controller.loadMore(); // must be page 1 again, not page 2

      expect(repo.requestedPages, [0, 1, 0, 1]);
    },
  );

  test('prependById inserts at index 0, dedups, and preserves pagination',
      () async {
    final repo = _PagingNewsRepository();
    final container = ProviderContainer(
      overrides: [newsRepositoryProvider.overrideWith((ref) => repo)],
    );
    addTearDown(container.dispose);

    final feed0 = await container.read(newsFeedControllerProvider.future);
    final existingFirstId = feed0.articles.first.id;
    final ctrl = container.read(newsFeedControllerProvider.notifier);

    await ctrl.prependById('realtime-1');
    var items = container.read(newsFeedControllerProvider).value!.articles;
    expect(items.first.id, 'realtime-1');
    expect(items.where((a) => a.id == 'realtime-1').length, 1);

    // Duplicate real-time event is ignored.
    await ctrl.prependById('realtime-1');
    items = container.read(newsFeedControllerProvider).value!.articles;
    expect(items.where((a) => a.id == 'realtime-1').length, 1);

    // Pagination cursor is not corrupted by the insertion.
    await ctrl.loadMore();
    expect(repo.requestedPages.last, 1);
    expect(
      container
          .read(newsFeedControllerProvider)
          .value!
          .articles
          .any((a) => a.id == existingFirstId),
      isTrue,
    );
  });

  test('loadMore does not render duplicate article ids', () async {
    final repo = _PagingNewsRepository(repeatIds: true);
    final container = ProviderContainer(
      overrides: [newsRepositoryProvider.overrideWith((ref) => repo)],
    );
    addTearDown(container.dispose);

    await container.read(newsFeedControllerProvider.future);
    final controller = container.read(newsFeedControllerProvider.notifier);
    await controller.loadMore();

    final ids = container
        .read(newsFeedControllerProvider)
        .value!
        .articles
        .map((a) => a.id)
        .toList();
    expect(ids.toSet().length, ids.length);
  });
}

NewsArticle _article(String id, DateTime publishedAt) => NewsArticle(
  id: id,
  sourceName: 'CoinDesk',
  sourceUrl: 'https://coindesk.com/$id',
  title: 'Title $id',
  summary: 'summary',
  publishedAt: publishedAt,
  category: NewsCategory.market,
  sentiment: NewsSentiment.neutral,
  impactLevel: NewsImpact.low,
  assets: const [],
);

class _PagingNewsRepository implements NewsRepository {
  _PagingNewsRepository({this.repeatIds = false});

  final bool repeatIds;
  final List<int> requestedPages = [];
  static const int _total = 45; // 3 pages of 20

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
    requestedPages.add(page);
    final base = DateTime.utc(2026, 9, 27, 8);
    final items = [
      for (var i = 0; i < size; i++)
        _article(
          repeatIds ? 'dup-$i' : 'p$page-$i',
          base.subtract(Duration(minutes: page * size + i)),
        ),
    ];
    return NewsPage(
      items: items,
      page: page,
      size: size,
      totalElements: _total,
      totalPages: 3,
      hasNext: (page + 1) * size < _total,
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
  Future<NewsArticleDetail> getDetail(String id) async => NewsArticleDetail(
    article: _article(id, DateTime.utc(2026, 9, 27, 8)),
    content: 'body',
    fetchedAt: DateTime.now(),
    processingStatus: NewsProcessingStatus.processed,
  );

  @override
  Future<NewsMeta> getMeta() async => NewsMeta(
    totalArticles: 0,
    sources: const [],
    categories: const [],
    sentiments: const [],
    impacts: const [],
  );

  @override
  Future<NewsContext> getAssetContext(String symbol, {int? windowHours}) =>
      throw UnimplementedError();
}
