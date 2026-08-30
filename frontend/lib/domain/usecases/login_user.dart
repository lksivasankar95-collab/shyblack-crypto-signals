import '../entities/auth_tokens.dart';
import '../repositories/auth_repository.dart';

class LoginUser {
  const LoginUser(this._repository);

  final AuthRepository _repository;

  Future<AuthTokens> call({required String email, required String password}) {
    return _repository.login(email: email, password: password);
  }
}
