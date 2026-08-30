class AuthFormState {
  const AuthFormState({
    this.loading = false,
    this.error,
  });

  final bool loading;
  final String? error;

  AuthFormState copyWith({bool? loading, String? error, bool clearError = false}) {
    return AuthFormState(
      loading: loading ?? this.loading,
      error: clearError ? null : (error ?? this.error),
    );
  }
}
