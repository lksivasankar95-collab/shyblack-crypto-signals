import '../../../core/constants/api_constants.dart';
import '../../../core/network/api_client.dart';
import '../models/signal_model.dart';

class SignalRemoteDataSource {
  SignalRemoteDataSource(this._apiClient);
  final ApiClient _apiClient;

  Future<List<SignalModel>> getSignals() async {
    final response = await _apiClient.dio.get<List<dynamic>>(ApiConstants.signals);
    return (response.data ?? [])
        .map((item) => SignalModel.fromJson(item as Map<String, dynamic>))
        .toList();
  }
}
