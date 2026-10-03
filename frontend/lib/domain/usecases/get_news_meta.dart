import '../entities/news.dart';
import '../repositories/news_repository.dart';

class GetNewsMeta {
  const GetNewsMeta(this._repository);
  final NewsRepository _repository;

  Future<NewsMeta> call() => _repository.getMeta();
}