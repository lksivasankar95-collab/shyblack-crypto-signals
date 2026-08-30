import '../../domain/entities/app_settings.dart';

class AppSettingsModel {
  const AppSettingsModel(this.settings);

  final AppSettings settings;

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
        subscriptionValidTill:
            json['subscriptionValidTill'] as String? ?? AppSettings.defaults.subscriptionValidTill,
        tagline: json['tagline'] as String? ?? AppSettings.defaults.tagline,
        isPremium: json['isPremium'] as bool? ?? true,
        tradingMode: _mode(json['tradingMode'] as String?),
        tradingAccount: _account(json['tradingAccount'] as String?),
        quoteCurrency: json['quoteCurrency'] as String? ?? AppSettings.defaults.quoteCurrency,
        riskProfile: _risk(json['riskProfile'] as String?),
        defaultLeverageView:
            json['defaultLeverageView'] as String? ?? AppSettings.defaults.defaultLeverageView,
        themeName: json['themeName'] as String? ?? AppSettings.defaults.themeName,
        language: json['language'] as String? ?? AppSettings.defaults.language,
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
        'subscriptionValidTill': settings.subscriptionValidTill,
        'tagline': settings.tagline,
        'isPremium': settings.isPremium,
        'tradingMode': settings.tradingMode.name,
        'tradingAccount': settings.tradingAccount.name,
        'quoteCurrency': settings.quoteCurrency,
        'riskProfile': settings.riskProfile.name,
        'defaultLeverageView': settings.defaultLeverageView,
        'themeName': settings.themeName,
        'language': settings.language,
      };

  static TradingMode _mode(String? value) => TradingMode.values.firstWhere(
        (item) => item.name == value,
        orElse: () => TradingMode.spot,
      );

  static TradingAccount _account(String? value) => TradingAccount.values.firstWhere(
        (item) => item.name == value,
        orElse: () => TradingAccount.paper,
      );

  static RiskProfile _risk(String? value) => RiskProfile.values.firstWhere(
        (item) => item.name == value,
        orElse: () => RiskProfile.moderate,
      );
}
