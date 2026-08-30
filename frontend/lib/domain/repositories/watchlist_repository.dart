import '../entities/watchlist_item.dart';

abstract class WatchlistRepository {
  Future<List<WatchlistItem>> getWatchlist();
}
