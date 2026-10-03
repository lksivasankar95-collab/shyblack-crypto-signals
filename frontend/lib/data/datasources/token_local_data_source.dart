import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class TokenLocalDataSource {
  TokenLocalDataSource({FlutterSecureStorage? storage})
      : _storage = storage ??
            const FlutterSecureStorage(
              webOptions: WebOptions(
                dbName: 'shyblackSecureStorage',
                publicKey: 'ShyBlackSecureStorage',
              ),
            );

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

  Future<void> saveAccessToken(String accessToken) async {
    await _storage.write(key: _accessKey, value: accessToken);
  }

  Future<String?> readAccessToken() => _read(_accessKey);

  Future<String?> readRefreshToken() => _read(_refreshKey);

  Future<void> clear() async {
    await _storage.delete(key: _accessKey);
    await _storage.delete(key: _refreshKey);
  }

  Future<String?> _read(String key) async {
    try {
      final value = await _storage.read(key: key);
      if (value == null || value.isEmpty) {
        return null;
      }
      return value;
    } catch (_) {
      return null;
    }
  }
}
