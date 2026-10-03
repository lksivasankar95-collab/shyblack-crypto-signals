import '../../../core/constants/api_constants.dart';
import '../../../core/network/api_client.dart';
import '../models/signal_model.dart';

class SignalRemoteDataSource {
  SignalRemoteDataSource(this._apiClient);
  final ApiClient _apiClient;

  /// Fetches signals for a trading mode (`SPOT` / `FUTURES`). When [mode] is
  /// null the backend default (SPOT) is used.
  Future<List<SignalModel>> getSignals({String? mode}) async {
    final response = await _apiClient.dio.get<List<dynamic>>(
      ApiConstants.signals,
      queryParameters: {if (mode != null && mode.isNotEmpty) 'mode': mode},
    );
    return (response.data ?? [])
        .map((item) => SignalModel.fromJson(item as Map<String, dynamic>))
        .toList();
  }
}
