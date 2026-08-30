enum TradingMode { spot, futures, options }

enum TradingAccount { paper, live }

enum RiskProfile { conservative, moderate, aggressive }

extension TradingModeLabel on TradingMode {
  String get label => switch (this) {
        TradingMode.spot => 'Spot',
        TradingMode.futures => 'Futures',
        TradingMode.options => 'Options',
      };

  /// Query value for GET /api/markets?mode=
  String get apiParam => name.toUpperCase();
}

extension TradingAccountLabel on TradingAccount {
  String get label => switch (this) {
        TradingAccount.paper => 'Paper Trading Account',
        TradingAccount.live => 'Live Trading Account',
      };

  String get subtitle => switch (this) {
        TradingAccount.paper => 'Practice with virtual funds',
        TradingAccount.live => 'Connect exchange, real funds',
      };
}

extension RiskProfileLabel on RiskProfile {
  String get label => switch (this) {
        RiskProfile.conservative => 'Conservative',
        RiskProfile.moderate => 'Moderate',
        RiskProfile.aggressive => 'Aggressive',
      };
}

class AppSettings {
  const AppSettings({
    required this.fullName,
    required this.email,
    required this.phone,
    required this.country,
    required this.timezone,
    required this.memberId,
    required this.memberSince,
    required this.membershipTier,
    required this.subscriptionValidTill,
    required this.tagline,
    required this.isPremium,
    required this.tradingMode,
    required this.tradingAccount,
    required this.quoteCurrency,
    required this.riskProfile,
    required this.defaultLeverageView,
    required this.themeName,
    required this.language,
  });

  final String fullName;
  final String email;
  final String phone;
  final String country;
  final String timezone;
  final String memberId;
  final String memberSince;
  final String membershipTier;
  final String subscriptionValidTill;
  final String tagline;
  final bool isPremium;
  final TradingMode tradingMode;
  final TradingAccount tradingAccount;
  final String quoteCurrency;
  final RiskProfile riskProfile;
  final String defaultLeverageView;
  final String themeName;
  final String language;

  static const AppSettings defaults = AppSettings(
    fullName: 'Ada Trader',
    email: 'trader@example.com',
    phone: '+1 555 0100',
    country: 'United States',
    timezone: 'UTC',
    memberId: 'SB-100001',
    memberSince: 'Aug 2026',
    membershipTier: 'Premium',
    subscriptionValidTill: '30 Aug 2027',
    tagline: 'Analyze. Predict. Profit.',
    isPremium: true,
    tradingMode: TradingMode.spot,
    tradingAccount: TradingAccount.paper,
    quoteCurrency: 'USDT',
    riskProfile: RiskProfile.moderate,
    defaultLeverageView: 'Isolated',
    themeName: 'Dark',
    language: 'English',
  );

  AppSettings copyWith({
    String? fullName,
    String? email,
    String? phone,
    String? country,
    String? timezone,
    String? memberId,
    String? memberSince,
    String? membershipTier,
    String? subscriptionValidTill,
    String? tagline,
    bool? isPremium,
    TradingMode? tradingMode,
    TradingAccount? tradingAccount,
    String? quoteCurrency,
    RiskProfile? riskProfile,
    String? defaultLeverageView,
    String? themeName,
    String? language,
  }) {
    return AppSettings(
      fullName: fullName ?? this.fullName,
      email: email ?? this.email,
      phone: phone ?? this.phone,
      country: country ?? this.country,
      timezone: timezone ?? this.timezone,
      memberId: memberId ?? this.memberId,
      memberSince: memberSince ?? this.memberSince,
      membershipTier: membershipTier ?? this.membershipTier,
      subscriptionValidTill: subscriptionValidTill ?? this.subscriptionValidTill,
      tagline: tagline ?? this.tagline,
      isPremium: isPremium ?? this.isPremium,
      tradingMode: tradingMode ?? this.tradingMode,
      tradingAccount: tradingAccount ?? this.tradingAccount,
      quoteCurrency: quoteCurrency ?? this.quoteCurrency,
      riskProfile: riskProfile ?? this.riskProfile,
      defaultLeverageView: defaultLeverageView ?? this.defaultLeverageView,
      themeName: themeName ?? this.themeName,
      language: language ?? this.language,
    );
  }
}
