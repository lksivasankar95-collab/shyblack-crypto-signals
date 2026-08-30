import '../../domain/entities/watchlist_item.dart';
import '../../domain/repositories/watchlist_repository.dart';
import '../datasources/watchlist_remote_data_source.dart';

class WatchlistRepositoryImpl implements WatchlistRepository {
  WatchlistRepositoryImpl(this._remote);
  final WatchlistRemoteDataSource _remote;

  @override
  Future<List<WatchlistItem>> getWatchlist() async {
    final models = await _remote.getWatchlist();
    return models.map((model) => model.toEntity()).toList();
  }
}
