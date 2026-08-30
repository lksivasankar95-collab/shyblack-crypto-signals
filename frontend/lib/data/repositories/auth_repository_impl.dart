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
}
