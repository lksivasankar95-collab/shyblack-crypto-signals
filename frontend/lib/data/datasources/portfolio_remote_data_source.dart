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
}
