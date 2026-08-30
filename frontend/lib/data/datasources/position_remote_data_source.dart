import '../../../core/constants/api_constants.dart';
import '../../../core/network/api_client.dart';
import '../models/position_model.dart';

class PositionRemoteDataSource {
  PositionRemoteDataSource(this._apiClient);
  final ApiClient _apiClient;

  Future<List<PositionModel>> getPositions() async {
    final response = await _apiClient.dio.get<List<dynamic>>(ApiConstants.positions);
    return (response.data ?? [])
        .map((item) => PositionModel.fromJson(item as Map<String, dynamic>))
        .toList();
  }
}
