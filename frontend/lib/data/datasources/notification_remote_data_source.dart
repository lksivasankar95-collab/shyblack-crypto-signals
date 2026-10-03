import '../../../core/constants/api_constants.dart';
import '../../../core/network/api_client.dart';
import '../models/notification_model.dart';

class NotificationRemoteDataSource {
  NotificationRemoteDataSource(this._apiClient);
  final ApiClient _apiClient;

  Future<List<NotificationModel>> getNotifications() async {
    final response = await _apiClient.dio.get<List<dynamic>>(ApiConstants.notifications);
    return (response.data ?? [])
        .map((item) => NotificationModel.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  Future<void> registerDeviceToken(String token) async {
    await _apiClient.dio.post(ApiConstants.deviceTokens, data: {"token": token});
  }

  Future<void> removeDeviceToken(String token) async {
    await _apiClient.dio.delete(ApiConstants.deviceTokens, data: {"token": token});
  }
}
