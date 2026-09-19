import '../entities/news.dart';

abstract class NewsRepository {
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
  });

  Future<NewsPage> getAssetNews(
    String symbol, {
    String? category,
    String? sentiment,
    String? impact,
    int page = 0,
    int size = 20,
    String? sort,
    String? direction,
  });

  Future<NewsArticleDetail> getDetail(String id);

  Future<NewsMeta> getMeta();

  Future<NewsContext> getAssetContext(String symbol, {int? windowHours});
}