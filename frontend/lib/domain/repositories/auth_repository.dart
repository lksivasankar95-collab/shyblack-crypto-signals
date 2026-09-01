import '../entities/auth_tokens.dart';

abstract class AuthRepository {
  Future<AuthTokens> login({required String email, required String password});

  Future<AuthTokens> loginWithGoogle({String? idToken});

  Future<void> signup({
    required String fullName,
    required String email,
    required String password,
  });

  Future<bool> restoreSession();

  Future<void> logout();
}
