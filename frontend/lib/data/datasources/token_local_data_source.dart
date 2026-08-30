import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class TokenLocalDataSource {
  TokenLocalDataSource({FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage();

  static const _accessKey = 'accessToken';
  static const _refreshKey = 'refreshToken';

  final FlutterSecureStorage _storage;

  Future<void> saveTokens({
    required String accessToken,
    required String refreshToken,
  }) async {
    await _storage.write(key: _accessKey, value: accessToken);
    await _storage.write(key: _refreshKey, value: refreshToken);
  }

  Future<String?> readAccessToken() => _storage.read(key: _accessKey);

  Future<void> clear() async {
    await _storage.delete(key: _accessKey);
    await _storage.delete(key: _refreshKey);
  }
}
