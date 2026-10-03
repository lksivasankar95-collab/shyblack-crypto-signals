import '../entities/news.dart';
import '../repositories/news_repository.dart';

class GetAssetNews {
  const GetAssetNews(this._repository);
  final NewsRepository _repository;

  Future<NewsPage> call(
    String symbol, {
    String? category,
    String? sentiment,
    String? impact,
    int page = 0,
    int size = 20,
    String? sort,
    String? direction,
  }) {
    return _repository.getAssetNews(
      symbol,
      category: category,
      sentiment: sentiment,
      impact: impact,
      page: page,
      size: size,
      sort: sort,
      direction: direction,
    );
  }
}