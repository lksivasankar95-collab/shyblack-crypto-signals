import '../../core/constants/api_constants.dart';
import '../../core/network/api_client.dart';

/// Read-only data source for the unified Portfolio API.
///
/// Consumes the Phase 5 contract exactly. It never connects to Binance and
/// never receives, stores or forwards any credential: the backend is the only
/// component that talks to an exchange.
class PortfolioRemoteDataSource {
  PortfolioRemoteDataSource(this._api);

  final ApiClient _api;

  Future<Map<String, dynamic>> getOverview(String mode) async {
    final res = await _api.dio.get<Map<String, dynamic>>(
      ApiConstants.portfolioOverview,
      queryParameters: {'mode': mode},
    );
    return res.data ?? const {};
  }

  Future<Map<String, dynamic>> getAccount(String mode, String category) async {
    final res = await _api.dio.get<Map<String, dynamic>>(
      ApiConstants.portfolioAccount(category),
      queryParameters: {'mode': mode},
    );
    return res.data ?? const {};
  }

  Future<Map<String, dynamic>> getPositions(String mode, String category) async {
    final res = await _api.dio.get<Map<String, dynamic>>(
      ApiConstants.portfolioPositions(category),
      queryParameters: {'mode': mode},
    );
    return res.data ?? const {};
  }

  /// Per-asset wallet holdings. Only LIVE spot has them; every other scope
  /// reports its own availability instead of an empty wallet.
  Future<Map<String, dynamic>> getHoldings(String mode, String category) async {
    final res = await _api.dio.get<Map<String, dynamic>>(
      ApiConstants.portfolioHoldings(category),
      queryParameters: {'mode': mode},
    );
    return res.data ?? const {};
  }

  /// Orders, fills or income over an explicit bounded window.
  ///
  /// [symbol] is required by the exchange for spot orders and fills, so it is
  /// passed through when known. The window bounds and the limit are sent
  /// explicitly rather than left to a server-side default.
  Future<Map<String, dynamic>> getHistory({
    required String mode,
    required String category,
    required String type,
    String? symbol,
    DateTime? from,
    DateTime? to,
    int? limit,
  }) async {
    final params = <String, dynamic>{'mode': mode, 'type': type};
    if (symbol != null && symbol.isNotEmpty) params['symbol'] = symbol;
    if (from != null) params['from'] = from.toUtc().toIso8601String();
    if (to != null) params['to'] = to.toUtc().toIso8601String();
    if (limit != null) params['limit'] = limit;

    final res = await _api.dio.get<Map<String, dynamic>>(
      ApiConstants.portfolioHistory(category),
      queryParameters: params,
    );
    return res.data ?? const {};
  }

  /// Connection state, data freshness and the reason a scope is not current.
  Future<Map<String, dynamic>> getSyncStatus(String mode, String category) async {
    final res = await _api.dio.get<Map<String, dynamic>>(
      ApiConstants.portfolioSyncStatus(category),
      queryParameters: {'mode': mode},
    );
    return res.data ?? const {};
  }
}
