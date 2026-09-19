import '../../core/constants/api_constants.dart';
import '../../core/network/api_client.dart';

class SettingsRemoteDataSource {
  SettingsRemoteDataSource(this._client);

  final ApiClient _client;

  Future<Map<String, dynamic>> fetchSettings() async {
    final response = await _client.dio.get<Map<String, dynamic>>(ApiConstants.settings);
    return response.data ?? {};
  }

  Future<Map<String, dynamic>> patchSettings(Map<String, dynamic> patch) async {
    final response = await _client.dio.patch<Map<String, dynamic>>(
      ApiConstants.settings,
      data: patch,
    );
    return response.data ?? {};
  }

  Future<Map<String, dynamic>> fetchProfile() async {
    final response = await _client.dio.get<Map<String, dynamic>>(ApiConstants.usersMe);
    return response.data ?? {};
  }

  Future<Map<String, dynamic>> patchProfile(Map<String, dynamic> patch) async {
    final response = await _client.dio.patch<Map<String, dynamic>>(
      ApiConstants.usersMe,
      data: patch,
    );
    return response.data ?? {};
  }

  Future<List<Map<String, dynamic>>> fetchExchanges() async {
    final response = await _client.dio.get<List<dynamic>>(ApiConstants.settingsExchanges);
    final list = response.data ?? [];
    return list.cast<Map<String, dynamic>>();
  }

  Future<Map<String, dynamic>> createExchange(Map<String, dynamic> body) async {
    final response = await _client.dio.post<Map<String, dynamic>>(
      ApiConstants.settingsExchanges,
      data: body,
    );
    return response.data ?? {};
  }

  Future<Map<String, dynamic>> testExchangeConnection(String id) async {
    final response = await _client.dio.post<Map<String, dynamic>>(
      ApiConstants.settingsExchangeTest(id),
    );
    return response.data ?? {};
  }

  Future<void> deleteExchange(String id) async {
    await _client.dio.delete<void>(ApiConstants.settingsExchangeDelete(id));
  }

  Future<Map<String, dynamic>> fetchNotificationPrefs() async {
    final response = await _client.dio.get<Map<String, dynamic>>(ApiConstants.settingsNotifications);
    return response.data ?? {};
  }

  Future<Map<String, dynamic>> updateNotificationPrefs(Map<String, dynamic> body) async {
    final response = await _client.dio.put<Map<String, dynamic>>(
      ApiConstants.settingsNotifications,
      data: body,
    );
    return response.data ?? {};
  }

  Future<List<Map<String, dynamic>>> fetchDeviceTokens() async {
    final response = await _client.dio.get<List<dynamic>>(ApiConstants.deviceTokens);
    final list = response.data ?? [];
    return list.cast<Map<String, dynamic>>();
  }
}
