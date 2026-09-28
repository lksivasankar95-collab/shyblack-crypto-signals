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
    await patch(current.copyWith(tradingMode: mode, selectedTradingModes: [mode]));
  }

  /// Multi-select update. At least one mode must remain selected; the singular
  /// [AppSettings.tradingMode] mirrors the first entry for legacy consumers.
  Future<void> setSelectedTradingModes(List<TradingMode> modes) async {
    if (modes.isEmpty) {
      return;
    }
    final current = state.value;
    if (current == null) {
      return;
    }
    final normalized = [for (final mode in TradingMode.values) if (modes.contains(mode)) mode];
    await patch(current.copyWith(
      selectedTradingModes: List.unmodifiable(normalized),
      tradingMode: normalized.first,
    ));
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
