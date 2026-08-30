import '../../../core/constants/api_constants.dart';
import '../../../core/network/api_client.dart';
import '../models/portfolio_model.dart';

class PortfolioRemoteDataSource {
  PortfolioRemoteDataSource(this._apiClient);
  final ApiClient _apiClient;

  Future<List<PortfolioModel>> getPortfolios() async {
    final response = await _apiClient.dio.get<List<dynamic>>(ApiConstants.portfolios);
    return (response.data ?? [])
        .map((item) => PortfolioModel.fromJson(item as Map<String, dynamic>))
        .toList();
  }
}
