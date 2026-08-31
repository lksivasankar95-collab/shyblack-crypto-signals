import '../../core/error/auth_exception.dart';
import '../../domain/entities/auth_tokens.dart';
import '../../domain/repositories/auth_repository.dart';
import '../datasources/auth_remote_data_source.dart';
import '../datasources/token_local_data_source.dart';

class AuthRepositoryImpl implements AuthRepository {
  AuthRepositoryImpl(this._remote, this._tokens);

  final AuthRemoteDataSource _remote;
  final TokenLocalDataSource _tokens;

  @override
  Future<AuthTokens> login({required String email, required String password}) async {
    final model = await _remote.login(email: email, password: password);
    await _tokens.saveTokens(
      accessToken: model.accessToken,
      refreshToken: model.refreshToken,
    );
    return model.toEntity();
  }

  @override
  Future<void> signup({
    required String fullName,
    required String email,
    required String password,
  }) {
    return _remote.signup(fullName: fullName, email: email, password: password);
  }

  @override
  Future<bool> restoreSession() async {
    final access = await _tokens.readAccessToken();
    final refresh = await _tokens.readRefreshToken();
    if (access == null && refresh == null) {
      return false;
    }

    if (access != null) {
      final ok = await _probeCurrentUser(allowOffline: true);
      if (ok) {
        return true;
      }
    }

    if (refresh != null) {
      try {
        final next = await _remote.refresh(refreshToken: refresh);
        await _tokens.saveAccessToken(next.accessToken);
        final ok = await _probeCurrentUser(allowOffline: true);
        if (ok) {
          return true;
        }
      } on AuthException {
        await _tokens.clear();
        return false;
      }
    }

    await _tokens.clear();
    return false;
  }

  Future<bool> _probeCurrentUser({required bool allowOffline}) async {
    try {
      await _remote.getCurrentUser();
      return true;
    } on AuthException catch (error) {
      if (error.unreachable && allowOffline) {
        return true;
      }
      if (error.unauthorized) {
        return false;
      }
      return false;
    }
  }

  @override
  Future<void> logout() => _tokens.clear();
}
