import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/di/providers.dart';

enum AuthStatus { authenticated, unauthenticated }

class AuthSessionController extends AsyncNotifier<AuthStatus> {
  @override
  Future<AuthStatus> build() async {
    final restored = await ref.read(restoreSessionProvider).call();
    return restored ? AuthStatus.authenticated : AuthStatus.unauthenticated;
  }

  void setAuthenticated() {
    state = const AsyncData(AuthStatus.authenticated);
  }

  Future<void> signOut() async {
    await ref.read(logoutUserProvider).call();
    state = const AsyncData(AuthStatus.unauthenticated);
  }
}

final authSessionProvider = AsyncNotifierProvider<AuthSessionController, AuthStatus>(
  AuthSessionController.new,
);
