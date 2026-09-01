abstract final class GoogleAuthConfig {
  /// Google Cloud Console Web OAuth 2.0 Client ID (ends with
  /// `.apps.googleusercontent.com`). Same value as backend `GOOGLE_CLIENT_ID`.
  ///
  /// Paste your Client ID as the defaultValue, or pass `--dart-define=GOOGLE_CLIENT_ID=...`.
  static const String webClientId = String.fromEnvironment(
    'GOOGLE_CLIENT_ID',
    defaultValue: 'PASTE_WEB_CLIENT_ID.apps.googleusercontent.com',
  );

  static bool get isConfigured =>
      webClientId.isNotEmpty &&
      webClientId.endsWith('.apps.googleusercontent.com') &&
      !webClientId.startsWith('PASTE_');
}
