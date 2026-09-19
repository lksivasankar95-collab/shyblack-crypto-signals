import '../../domain/entities/news.dart';
import '../../domain/repositories/news_repository.dart';
import '../datasources/news_remote_data_source.dart';

class NewsRepositoryImpl implements NewsRepository {
  NewsRepositoryImpl(this._remote);
  final NewsRemoteDataSource _remote;

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
    final model = await _remote.getNews(
      NewsQueryParams(
        category: category,
        sentiment: sentiment,
        impact: impact,
        source: source,
        query: query,
        page: page,
        size: size,
        sort: sort,
        direction: direction,
      ),
    );
    return model.toEntity();
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
  }) async {
    final model = await _remote.getAssetNews(
      symbol,
      NewsQueryParams(
        category: category,
        sentiment: sentiment,
        impact: impact,
        page: page,
        size: size,
        sort: sort,
        direction: direction,
      ),
    );
    return model.toEntity();
  }

  @override
  Future<NewsArticleDetail> getDetail(String id) async {
    return (await _remote.getDetail(id)).toEntity();
  }

  @override
  Future<NewsMeta> getMeta() async {
    return (await _remote.getMeta()).toEntity();
  }

  @override
  Future<NewsContext> getAssetContext(String symbol, {int? windowHours}) async {
    return (await _remote.getAssetContext(symbol, windowHours: windowHours))
        .toEntity();
  }
}