import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/di/providers.dart';
import '../../core/error/auth_exception.dart';
import '../../core/error/google_sign_in_cancelled_exception.dart';
import 'auth_form_state.dart';
import 'auth_session.dart';

class SignupController extends Notifier<AuthFormState> {
  @override
  AuthFormState build() => const AuthFormState();

  Future<bool> submit({
    required String fullName,
    required String email,
    required String password,
  }) async {
    state = const AuthFormState(loading: true);
    try {
      await ref.read(signupUserProvider).call(
            fullName: fullName,
            email: email,
            password: password,
          );
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

  Future<bool> signInWithGoogle({String? idToken}) async {
    state = const AuthFormState(loading: true);
    try {
      await ref.read(loginWithGoogleProvider).call(idToken: idToken);
      ref.read(authSessionProvider.notifier).setAuthenticated();
      state = const AuthFormState();
      return true;
    } on GoogleSignInCancelledException {
      state = const AuthFormState();
      return false;
    } on AuthException catch (error) {
      state = AuthFormState(error: error.message);
      return false;
    } catch (_) {
      state = const AuthFormState(error: 'Google Sign-In failed. Please try again.');
      return false;
    }
  }
}

final signupControllerProvider = NotifierProvider<SignupController, AuthFormState>(
  SignupController.new,
);
