import 'package:cryptosignals/domain/entities/app_settings.dart';
import 'package:cryptosignals/domain/repositories/settings_repository.dart';

/// In-memory Settings repository for tests.
///
/// The Portfolio reads its account mode from Settings, so a Portfolio test has to be
/// able to stand up a Settings state. Overriding the real settings repository (rather
/// than the settings provider) keeps the whole Settings → Portfolio path under test,
/// including the controller that resolves the mode.
class FakeSettingsRepository implements SettingsRepository {
  FakeSettingsRepository({TradingAccount account = TradingAccount.paper})
    : _settings = AppSettings.defaults.copyWith(tradingAccount: account);

  AppSettings _settings;

  /// When set, [load] throws this, which is how a test produces the "account mode
  /// unavailable" state.
  Object? failLoad;

  /// The account mode Settings currently reports.
  TradingAccount get account => _settings.tradingAccount;

  /// Simulates the user changing the account mode in Settings.
  void setAccount(TradingAccount account) {
    _settings = _settings.copyWith(tradingAccount: account);
  }

  @override
  Future<AppSettings> load() async {
    final failure = failLoad;
    if (failure != null) throw failure;
    return _settings;
  }

  @override
  Future<void> save(AppSettings settings) async => _settings = settings;

  @override
  Future<List<Map<String, dynamic>>> listExchanges() async => [];

  @override
  Future<Map<String, dynamic>> connectExchange(
    Map<String, dynamic> body,
  ) async => {};

  @override
  Future<Map<String, dynamic>> testExchangeConnection(String id) async => {
    'ok': true,
  };

  @override
  Future<void> deleteExchange(String id) async {}

  @override
  Future<Map<String, dynamic>> getNotificationPrefs() async => {};

  @override
  Future<Map<String, dynamic>> updateNotificationPrefs(
    Map<String, dynamic> body,
  ) async => {};

  @override
  Future<List<Map<String, dynamic>>> listDeviceTokens() async => [];
}
