import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/di/providers.dart';
import '../../domain/entities/news.dart';

class NewsFeedData {
  const NewsFeedData({
    required this.articles,
    required this.hasNext,
    this.lastUpdated,
  });

  final List<NewsArticle> articles;
  final bool hasNext;
  final DateTime? lastUpdated;

  static const NewsFeedData empty = NewsFeedData(
    articles: [],
    hasNext: false,
  );
}

class NewsFeedController extends AsyncNotifier<NewsFeedData> {
  static const _pageSize = 20;

  String? _category;
  String? _sentiment;
  String? _impact;
  String? _source;
  String? _query;
  int _page = 0;

  @override
  Future<NewsFeedData> build() async {
    _page = 0;
    final result = await _fetch(0);
    return NewsFeedData(
      articles: result.items,
      hasNext: result.hasNext,
      lastUpdated: DateTime.now(),
    );
  }

  void applyFilters({
    NewsCategory? category,
    NewsSentiment? sentiment,
    NewsImpact? impact,
    String? source,
    String? query,
  }) {
    _category = category?.apiValue;
    _sentiment = sentiment?.apiValue;
    _impact = impact?.apiValue;
    _source = source;
    _query = query;
    refresh();
  }

  Future<void> refresh({bool silent = false}) async {
    // Reloading page 0 resets the pagination cursor; otherwise a later
    // loadMore() would request `stalePage + 1` and silently skip pages.
    _page = 0;
    try {
      final result = await _fetch(0);
      state = AsyncData(
        NewsFeedData(
          articles: result.items,
          hasNext: result.hasNext,
          lastUpdated: DateTime.now(),
        ),
      );
    } catch (error, stack) {
      if (!silent) {
        state = AsyncError(error, stack);
      }
    }
  }

  Future<void> loadMore() async {
    final current = state.value;
    if (current == null || !current.hasNext) {
      return;
    }
    try {
      final nextPage = _page + 1;
      final result = await _fetch(nextPage);
      _page = nextPage;
      final known = <String>{for (final item in current.articles) item.id};
      state = AsyncData(
        NewsFeedData(
          articles: [
            ...current.articles,
            for (final item in result.items)
              if (!known.contains(item.id)) item,
          ],
          hasNext: result.hasNext,
          lastUpdated: DateTime.now(),
        ),
      );
    } catch (_) {
      // Keep the current list; a later pull-to-refresh retries.
    }
  }

  /// Real-time insertion: fetch the exact article by id and put it at the top.
  /// No-op if it is already present. Does NOT touch [_page] or hasNext, so the
  /// pagination cursor and loadMore() stay correct.
  Future<void> prependById(String id) async {
    final current = state.value;
    if (current != null && current.articles.any((a) => a.id == id)) {
      return;
    }
    try {
      final detail = await ref.read(getNewsDetailProvider).call(id);
      final article = detail.article;
      final base = state.value ?? NewsFeedData.empty;
      if (base.articles.any((a) => a.id == article.id)) {
        return;
      }
      state = AsyncData(
        NewsFeedData(
          articles: [article, ...base.articles],
          hasNext: base.hasNext,
          lastUpdated: DateTime.now(),
        ),
      );
    } catch (_) {
      // Ignore: the next refresh/scroll will pick the article up.
    }
  }

  /// Test hook: publish articles as if a refresh just completed.
  void ingestArticles(List<NewsArticle> articles) {
    state = AsyncData(
      NewsFeedData(
        articles: articles,
        hasNext: false,
        lastUpdated: DateTime.now(),
      ),
    );
  }

  Future<NewsPage> _fetch(int page) {
    return ref.read(getNewsProvider).call(
      category: _category,
      sentiment: _sentiment,
      impact: _impact,
      source: _source,
      query: _query,
      page: page,
      size: _pageSize,
      sort: 'publishedAt',
      direction: 'desc',
    );
  }
}

final newsFeedControllerProvider =
    AsyncNotifierProvider<NewsFeedController, NewsFeedData>(
      NewsFeedController.new,
    );

final newsMetaProvider = FutureProvider<NewsMeta>((ref) {
  return ref.watch(getNewsMetaProvider).call();
});

final newsDetailProvider = FutureProvider.family<NewsArticleDetail, String>((
  ref,
  id,
) {
  return ref.watch(getNewsDetailProvider).call(id);
});

final newsContextProvider = FutureProvider.family<NewsContext, String>((
  ref,
  symbol,
) {
  return ref.watch(getNewsContextProvider).call(symbol, windowHours: 48);
});

final assetNewsProvider = FutureProvider.family<NewsPage, String>((
  ref,
  symbol,
) {
  return ref.watch(getAssetNewsProvider).call(symbol, size: 20);
});