import '../entities/auth_tokens.dart';
import '../repositories/auth_repository.dart';

class LoginWithGoogle {
  const LoginWithGoogle(this._repository);

  final AuthRepository _repository;

  Future<AuthTokens> call({String? idToken}) {
    return _repository.loginWithGoogle(idToken: idToken);
  }
}
