import '../entities/news.dart';
import '../repositories/news_repository.dart';

class GetNews {
  const GetNews(this._repository);
  final NewsRepository _repository;

  Future<NewsPage> call({
    String? category,
    String? sentiment,
    String? impact,
    String? source,
    String? query,
    int page = 0,
    int size = 20,
    String? sort,
    String? direction,
  }) {
    return _repository.getNews(
      category: category,
      sentiment: sentiment,
      impact: impact,
      source: source,
      query: query,
      page: page,
      size: size,
      sort: sort,
      direction: direction,
    );
  }
}