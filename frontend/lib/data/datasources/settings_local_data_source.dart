import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

class SettingsLocalDataSource {
  SettingsLocalDataSource({this._preferences});

  static const storageKey = 'app_settings';

  SharedPreferences? _preferences;

  Future<SharedPreferences> _prefs() async {
    return _preferences ??= await SharedPreferences.getInstance();
  }

  Future<Map<String, dynamic>?> read() async {
    final raw = (await _prefs()).getString(storageKey);
    if (raw == null || raw.isEmpty) {
      return null;
    }
    final decoded = jsonDecode(raw);
    if (decoded is Map<String, dynamic>) {
      return decoded;
    }
    if (decoded is Map) {
      return decoded.map((key, value) => MapEntry(key.toString(), value));
    }
    return null;
  }

  Future<void> write(Map<String, dynamic> json) async {
    await (await _prefs()).setString(storageKey, jsonEncode(json));
  }
}
