import '../entities/app_settings.dart';

abstract class SettingsRepository {
  Future<AppSettings> load();
  Future<void> save(AppSettings settings);

  Future<List<Map<String, dynamic>>> listExchanges();
  Future<Map<String, dynamic>> connectExchange(Map<String, dynamic> body);
  Future<Map<String, dynamic>> testExchangeConnection(String id);
  Future<void> deleteExchange(String id);

  Future<Map<String, dynamic>> getNotificationPrefs();
  Future<Map<String, dynamic>> updateNotificationPrefs(Map<String, dynamic> body);

  Future<List<Map<String, dynamic>>> listDeviceTokens();
}
