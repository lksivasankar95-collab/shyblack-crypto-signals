import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/di/providers.dart';
import '../../core/error/auth_exception.dart';
import 'auth_form_state.dart';

class LoginController extends Notifier<AuthFormState> {
  @override
  AuthFormState build() => const AuthFormState();

  Future<bool> submit({required String email, required String password}) async {
    state = const AuthFormState(loading: true);
    try {
      await ref.read(loginUserProvider).call(email: email, password: password);
      state = const AuthFormState();
      return true;
    } on AuthException catch (error) {
      state = AuthFormState(error: error.message);
      return false;
    } catch (_) {
      state = const AuthFormState(error: 'Something went wrong. Please try again.');
      return false;
    }
  }

  void clearError() {
    if (state.error != null) {
      state = state.copyWith(clearError: true);
    }
  }
}

final loginControllerProvider = NotifierProvider<LoginController, AuthFormState>(
  LoginController.new,
);
