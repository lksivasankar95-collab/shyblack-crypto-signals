import '../../domain/entities/app_settings.dart';
import '../../domain/repositories/settings_repository.dart';
import '../datasources/settings_local_data_source.dart';
import '../datasources/settings_remote_data_source.dart';
import '../models/app_settings_model.dart';

class SettingsRepositoryImpl implements SettingsRepository {
  SettingsRepositoryImpl(this._local, this._remote);

  final SettingsLocalDataSource _local;
  final SettingsRemoteDataSource _remote;

  @override
  Future<AppSettings> load() async {
    try {
      final settingsJson = await _remote.fetchSettings();
      final profileJson = await _remote.fetchProfile();
      final model = AppSettingsModel.fromBackend(settingsJson, profileJson);
      await _local.write(model.toJson());
      return model.settings;
    } catch (_) {
      // Network unavailable — fall back to local cache.
      final json = await _local.read();
      if (json == null) return AppSettings.defaults;
      return AppSettingsModel.fromJson(json).settings;
    }
  }

  @override
  Future<void> save(AppSettings settings) async {
    final model = AppSettingsModel(settings);
    await _local.write(model.toJson());
    try {
      await _remote.patchSettings(model.toSettingsPatch());
      await _remote.patchProfile(model.toProfilePatch());
    } catch (_) {
      // Saved locally; backend sync will happen on next load.
    }
  }

  @override
  Future<List<Map<String, dynamic>>> listExchanges() =>
      _remote.fetchExchanges();

  @override
  Future<Map<String, dynamic>> connectExchange(Map<String, dynamic> body) =>
      _remote.createExchange(body);

  @override
  Future<Map<String, dynamic>> testExchangeConnection(String id) =>
      _remote.testExchangeConnection(id);

  @override
  Future<void> deleteExchange(String id) => _remote.deleteExchange(id);

  @override
  Future<Map<String, dynamic>> getNotificationPrefs() =>
      _remote.fetchNotificationPrefs();

  @override
  Future<Map<String, dynamic>> updateNotificationPrefs(Map<String, dynamic> body) =>
      _remote.updateNotificationPrefs(body);

  @override
  Future<List<Map<String, dynamic>>> listDeviceTokens() =>
      _remote.fetchDeviceTokens();
}
