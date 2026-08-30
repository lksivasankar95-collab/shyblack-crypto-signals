import '../../domain/entities/app_settings.dart';
import '../../domain/repositories/settings_repository.dart';
import '../datasources/settings_local_data_source.dart';
import '../models/app_settings_model.dart';

class SettingsRepositoryImpl implements SettingsRepository {
  SettingsRepositoryImpl(this._local);

  final SettingsLocalDataSource _local;

  @override
  Future<AppSettings> load() async {
    final json = await _local.read();
    if (json == null) {
      return AppSettings.defaults;
    }
    return AppSettingsModel.fromJson(json).settings;
  }

  @override
  Future<void> save(AppSettings settings) {
    return _local.write(AppSettingsModel(settings).toJson());
  }
}
