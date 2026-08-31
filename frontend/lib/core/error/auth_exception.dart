class AuthException implements Exception {
  const AuthException(
    this.message, {
    this.unauthorized = false,
    this.unreachable = false,
  });

  final String message;
  final bool unauthorized;
  final bool unreachable;

  @override
  String toString() => message;
}
