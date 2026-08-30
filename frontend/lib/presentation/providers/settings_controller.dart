import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/di/providers.dart';
import '../../domain/entities/app_settings.dart';

class SettingsController extends AsyncNotifier<AppSettings> {
  @override
  Future<AppSettings> build() => ref.read(getSettingsProvider).call();

  Future<void> patch(AppSettings next) async {
    state = AsyncData(next);
    await ref.read(saveSettingsProvider).call(next);
  }

  Future<void> setTradingMode(TradingMode mode) async {
    final current = state.value;
    if (current == null) {
      return;
    }
    await patch(current.copyWith(tradingMode: mode));
  }

  Future<void> setTradingAccount(TradingAccount account) async {
    final current = state.value;
    if (current == null) {
      return;
    }
    await patch(current.copyWith(tradingAccount: account));
  }

  Future<void> logout() => ref.read(logoutUserProvider).call();
}

final settingsControllerProvider = AsyncNotifierProvider<SettingsController, AppSettings>(
  SettingsController.new,
);
