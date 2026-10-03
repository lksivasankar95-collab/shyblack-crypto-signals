import '../../core/constants/api_constants.dart';
import '../../core/network/api_client.dart';

class PaperTradingRemoteDataSource {
  PaperTradingRemoteDataSource(this._api);

  final ApiClient _api;

  Future<Map<String, dynamic>> getAccount() async {
    final res = await _api.dio.get<Map<String, dynamic>>(
      ApiConstants.paperAccount,
    );
    return res.data ?? const {};
  }

  Future<List<Map<String, dynamic>>> listOpenPositions() async {
    final res = await _api.dio.get<List<dynamic>>(
      ApiConstants.paperPositions,
    );
    return (res.data ?? const [])
        .whereType<Map<String, dynamic>>()
        .toList();
  }

  Future<List<Map<String, dynamic>>> listHistory() async {
    final res = await _api.dio.get<List<dynamic>>(
      ApiConstants.paperHistory,
    );
    return (res.data ?? const [])
        .whereType<Map<String, dynamic>>()
        .toList();
  }

  Future<Map<String, dynamic>> getPerformance() async {
    final res = await _api.dio.get<Map<String, dynamic>>(
      ApiConstants.paperPerformance,
    );
    return res.data ?? const {};
  }

  Future<Map<String, dynamic>> closePosition(String id) async {
    final res = await _api.dio.post<Map<String, dynamic>>(
      ApiConstants.paperClose(id),
    );
    return res.data ?? const {};
  }

  Future<Map<String, dynamic>> resetAccount() async {
    final res = await _api.dio.delete<Map<String, dynamic>>(
      ApiConstants.paperAccount,
    );
    return res.data ?? const {};
  }

  Future<Map<String, dynamic>> updateInitialCapital(double initialCapital) async {
    final res = await _api.dio.patch<Map<String, dynamic>>(
      ApiConstants.paperAccountCapital,
      data: {'initialCapital': initialCapital},
    );
    return res.data ?? const {};
  }
}
