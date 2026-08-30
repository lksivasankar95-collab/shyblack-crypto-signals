import '../../../core/constants/api_constants.dart';
import '../../../core/network/api_client.dart';
import '../models/watchlist_model.dart';

class WatchlistRemoteDataSource {
  WatchlistRemoteDataSource(this._apiClient);
  final ApiClient _apiClient;

  Future<List<WatchlistModel>> getWatchlist() async {
    final response = await _apiClient.dio.get<List<dynamic>>(ApiConstants.watchlist);
    return (response.data ?? [])
        .map((item) => WatchlistModel.fromJson(item as Map<String, dynamic>))
        .toList();
  }
}
