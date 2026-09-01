import 'dart:async';

import 'package:google_sign_in/google_sign_in.dart';

import '../../core/constants/google_auth_config.dart';
import '../../core/error/auth_exception.dart';
import '../../core/error/google_sign_in_cancelled_exception.dart';

class GoogleSignInDataSource {
  Future<void>? _initializing;
  StreamSubscription<GoogleSignInAuthenticationEvent>? _events;
  final StreamController<String> _idTokens = StreamController<String>.broadcast();

  Stream<String> get idTokens => _idTokens.stream;

  bool get supportsInteractiveAuthenticate {
    try {
      return GoogleSignIn.instance.supportsAuthenticate();
    } catch (_) {
      return false;
    }
  }

  Future<void> ensureInitialized() {
    return _initializing ??= _initialize();
  }

  Future<void> _initialize() async {
    if (!GoogleAuthConfig.isConfigured) {
      return;
    }
    await GoogleSignIn.instance.initialize(
      clientId: GoogleAuthConfig.webClientId,
      serverClientId: GoogleAuthConfig.webClientId,
    );
    _events = GoogleSignIn.instance.authenticationEvents.listen((event) {
      if (event is GoogleSignInAuthenticationEventSignIn) {
        final token = event.user.authentication.idToken;
        if (token != null && token.isNotEmpty) {
          _idTokens.add(token);
        }
      }
    });
  }

  Future<String> authenticateInteractive() async {
    await ensureInitialized();
    if (!GoogleAuthConfig.isConfigured) {
      throw const AuthException(
        'Google Sign-In is not configured. Paste your Web Client ID in google_auth_config.dart.',
      );
    }
    if (!supportsInteractiveAuthenticate) {
      throw const AuthException('Google Sign-In is not available on this platform.');
    }
    try {
      await GoogleSignIn.instance.signOut();
      final account = await GoogleSignIn.instance.authenticate(
        scopeHint: const ['email', 'profile', 'openid'],
      );
      final token = account.authentication.idToken;
      if (token == null || token.isEmpty) {
        throw const AuthException('Google did not return an ID token. Try again.');
      }
      return token;
    } on GoogleSignInException catch (error) {
      if (error.code == GoogleSignInExceptionCode.canceled ||
          error.code == GoogleSignInExceptionCode.interrupted) {
        throw const GoogleSignInCancelledException();
      }
      throw AuthException(error.description ?? 'Google Sign-In failed. Please try again.');
    }
  }

  Future<void> dispose() async {
    await _events?.cancel();
    await _idTokens.close();
  }
}
