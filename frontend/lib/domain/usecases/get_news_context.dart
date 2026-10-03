import '../entities/news.dart';
import '../repositories/news_repository.dart';

class GetNewsContext {
  const GetNewsContext(this._repository);
  final NewsRepository _repository;

  Future<NewsContext> call(String symbol, {int? windowHours}) {
    return _repository.getAssetContext(symbol, windowHours: windowHours);
  }
}