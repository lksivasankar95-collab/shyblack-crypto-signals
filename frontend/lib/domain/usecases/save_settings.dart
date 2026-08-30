import '../../domain/entities/app_settings.dart';
import '../../domain/repositories/settings_repository.dart';

class SaveSettings {
  const SaveSettings(this._repository);

  final SettingsRepository _repository;

  Future<void> call(AppSettings settings) => _repository.save(settings);
}
