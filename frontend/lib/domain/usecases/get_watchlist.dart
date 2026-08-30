import '../entities/watchlist_item.dart';
import '../repositories/watchlist_repository.dart';

class GetWatchlist {
  const GetWatchlist(this._repository);
  final WatchlistRepository _repository;

  Future<List<WatchlistItem>> call() => _repository.getWatchlist();
}
