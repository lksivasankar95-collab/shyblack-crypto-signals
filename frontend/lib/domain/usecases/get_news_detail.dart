import '../entities/news.dart';
import '../repositories/news_repository.dart';

class GetNewsDetail {
  const GetNewsDetail(this._repository);
  final NewsRepository _repository;

  Future<NewsArticleDetail> call(String id) => _repository.getDetail(id);
}