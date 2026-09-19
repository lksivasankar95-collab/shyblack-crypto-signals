import '../../domain/entities/app_settings.dart';

class AppSettingsModel {
  const AppSettingsModel(this.settings);

  final AppSettings settings;

  /// Builds an [AppSettings] from a merged backend payload.
  ///
  /// [settingsJson] comes from GET /api/v1/settings (SettingsResponse).
  /// [profileJson] comes from GET /api/users/me (UserResponse).
  /// Both are optional – whichever is present wins over the local cache.
  factory AppSettingsModel.fromBackend(
    Map<String, dynamic> settingsJson,
    Map<String, dynamic> profileJson,
  ) {
    final userId = settingsJson['userId'] as String? ?? '';
    final memberId = userId.length >= 8 ? 'SB-${userId.substring(0, 8).toUpperCase()}' : 'SB-UNKNOWN';

    final createdAtRaw = profileJson['createdAt'] as String?;
    final memberSince = _formatCreatedAt(createdAtRaw);

    return AppSettingsModel(
      AppSettings(
        fullName: settingsJson['fullName'] as String? ?? profileJson['fullName'] as String? ?? '',
        email: settingsJson['email'] as String? ?? profileJson['email'] as String? ?? '',
        phone: profileJson['phoneNumber'] as String? ?? '',
        country: profileJson['country'] as String? ?? '',
        timezone: profileJson['timezone'] as String? ?? 'UTC',
        memberId: memberId,
        memberSince: memberSince,
        membershipTier: 'Standard',
        tradingMode: _mode(settingsJson['tradingMode'] as String?),
        tradingAccount: _account(settingsJson['accountType'] as String?),
        quoteCurrency: settingsJson['quoteCurrency'] as String? ?? 'USDT',
        riskProfile: _risk(settingsJson['riskProfile'] as String?),
        positionSizingMode: _sizingMode(settingsJson['positionSizingMode'] as String?),
        defaultLeverageView: settingsJson['defaultLeverageView'] as String? ?? '1x',
        themeName: settingsJson['themeName'] as String? ?? 'dark',
        language: _languageDisplayName(settingsJson['languageCode'] as String?),
        liveTradingAllowed: settingsJson['liveTradingAllowed'] as bool? ?? false,
        hasVerifiedExchange: settingsJson['hasVerifiedExchange'] as bool? ?? false,
      ),
    );
  }

  /// Builds from local SharedPreferences cache (offline/fallback).
  factory AppSettingsModel.fromJson(Map<String, dynamic> json) {
    return AppSettingsModel(
      AppSettings(
        fullName: json['fullName'] as String? ?? AppSettings.defaults.fullName,
        email: json['email'] as String? ?? AppSettings.defaults.email,
        phone: json['phone'] as String? ?? AppSettings.defaults.phone,
        country: json['country'] as String? ?? AppSettings.defaults.country,
        timezone: json['timezone'] as String? ?? AppSettings.defaults.timezone,
        memberId: json['memberId'] as String? ?? AppSettings.defaults.memberId,
        memberSince: json['memberSince'] as String? ?? AppSettings.defaults.memberSince,
        membershipTier: json['membershipTier'] as String? ?? AppSettings.defaults.membershipTier,
        tradingMode: _mode(json['tradingMode'] as String?),
        tradingAccount: _account(json['tradingAccount'] as String?),
        quoteCurrency: json['quoteCurrency'] as String? ?? AppSettings.defaults.quoteCurrency,
        riskProfile: _risk(json['riskProfile'] as String?),
        positionSizingMode: _sizingMode(json['positionSizingMode'] as String?),
        defaultLeverageView: json['defaultLeverageView'] as String? ?? AppSettings.defaults.defaultLeverageView,
        themeName: json['themeName'] as String? ?? AppSettings.defaults.themeName,
        language: json['language'] as String? ?? AppSettings.defaults.language,
        liveTradingAllowed: json['liveTradingAllowed'] as bool? ?? false,
        hasVerifiedExchange: json['hasVerifiedExchange'] as bool? ?? false,
      ),
    );
  }

  Map<String, dynamic> toJson() => {
        'fullName': settings.fullName,
        'email': settings.email,
        'phone': settings.phone,
        'country': settings.country,
        'timezone': settings.timezone,
        'memberId': settings.memberId,
        'memberSince': settings.memberSince,
        'membershipTier': settings.membershipTier,
        'tradingMode': settings.tradingMode.name,
        'tradingAccount': settings.tradingAccount.name,
        'quoteCurrency': settings.quoteCurrency,
        'riskProfile': settings.riskProfile.name,
        'positionSizingMode': settings.positionSizingMode.name,
        'defaultLeverageView': settings.defaultLeverageView,
        'themeName': settings.themeName,
        'language': settings.language,
        'liveTradingAllowed': settings.liveTradingAllowed,
        'hasVerifiedExchange': settings.hasVerifiedExchange,
      };

  /// Builds the PATCH body for /api/v1/settings from current settings.
  Map<String, dynamic> toSettingsPatch() => {
        'quoteCurrency': settings.quoteCurrency,
        'positionSizingMode': _positionSizingModeApi(settings.positionSizingMode),
        'riskProfileOverride': settings.riskProfile.name.toUpperCase(),
        'defaultLeverageView': settings.defaultLeverageView,
        'themeName': settings.themeName,
        'languageCode': _languageCode(settings.language),
        'tradingMode': settings.tradingMode.name.toUpperCase(),
        'accountType': settings.tradingAccount == TradingAccount.paper ? 'PAPER' : 'LIVE',
      };

  /// Builds the PATCH body for /api/users/me from current settings.
  Map<String, dynamic> toProfilePatch() => {
        'fullName': settings.fullName.isEmpty ? null : settings.fullName,
        'phoneNumber': settings.phone.isEmpty ? null : settings.phone,
        'country': settings.country.isEmpty ? null : settings.country,
        'timezone': settings.timezone.isEmpty ? null : settings.timezone,
      };

  static TradingMode _mode(String? value) {
    if (value == null) return TradingMode.spot;
    return TradingMode.values.firstWhere(
      (m) => m.name.toUpperCase() == value.toUpperCase(),
      orElse: () => TradingMode.spot,
    );
  }

  static TradingAccount _account(String? value) {
    if (value == null) return TradingAccount.paper;
    return value.toUpperCase() == 'LIVE' ? TradingAccount.live : TradingAccount.paper;
  }

  static RiskProfile _risk(String? value) {
    if (value == null) return RiskProfile.moderate;
    return RiskProfile.values.firstWhere(
      (r) => r.name.toUpperCase() == value.toUpperCase(),
      orElse: () => RiskProfile.moderate,
    );
  }

  static PositionSizingMode _sizingMode(String? value) {
    if (value == null) return PositionSizingMode.fixedPercent;
    final normalized = value.toUpperCase().replaceAll('_', '');
    return PositionSizingMode.values.firstWhere(
      (m) => m.name.toUpperCase() == normalized,
      orElse: () => PositionSizingMode.fixedPercent,
    );
  }

  static String _positionSizingModeApi(PositionSizingMode mode) => switch (mode) {
        PositionSizingMode.fixedPercent => 'FIXED_PERCENT',
        PositionSizingMode.fixedAmount => 'FIXED_AMOUNT',
        PositionSizingMode.kellyPercent => 'KELLY_PERCENT',
      };

  static String _languageDisplayName(String? code) => switch (code) {
        'en' || null => 'English',
        final c => c,
      };

  static String _languageCode(String displayName) => switch (displayName) {
        'English' => 'en',
        _ => displayName.toLowerCase(),
      };

  static String _formatCreatedAt(String? iso) {
    if (iso == null) return '';
    try {
      final dt = DateTime.parse(iso);
      const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
      return '${months[dt.month - 1]} ${dt.year}';
    } catch (_) {
      return '';
    }
  }
}
