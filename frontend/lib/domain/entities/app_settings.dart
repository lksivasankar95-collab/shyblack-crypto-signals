enum TradingMode { spot, futures, options }

enum TradingAccount { paper, live }

enum RiskProfile { conservative, moderate, aggressive }

enum PositionSizingMode { fixedPercent, fixedAmount, kellyPercent }

extension TradingModeLabel on TradingMode {
  String get label => switch (this) {
        TradingMode.spot => 'Spot',
        TradingMode.futures => 'Futures',
        TradingMode.options => 'Options',
      };

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

extension PositionSizingModeLabel on PositionSizingMode {
  String get label => switch (this) {
        PositionSizingMode.fixedPercent => 'Fixed %',
        PositionSizingMode.fixedAmount => 'Fixed Amount',
        PositionSizingMode.kellyPercent => 'Kelly %',
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
    required this.tradingMode,
    required this.tradingAccount,
    required this.quoteCurrency,
    required this.riskProfile,
    required this.positionSizingMode,
    required this.defaultLeverageView,
    required this.themeName,
    required this.language,
    this.liveTradingAllowed = false,
    this.hasVerifiedExchange = false,
  });

  final String fullName;
  final String email;
  final String phone;
  final String country;
  final String timezone;
  final String memberId;
  final String memberSince;
  final String membershipTier;
  final TradingMode tradingMode;
  final TradingAccount tradingAccount;
  final String quoteCurrency;
  final RiskProfile riskProfile;
  final PositionSizingMode positionSizingMode;
  final String defaultLeverageView;
  final String themeName;
  final String language;
  final bool liveTradingAllowed;
  final bool hasVerifiedExchange;

  static const AppSettings defaults = AppSettings(
    fullName: '',
    email: '',
    phone: '',
    country: '',
    timezone: 'UTC',
    memberId: '',
    memberSince: '',
    membershipTier: 'Standard',
    tradingMode: TradingMode.spot,
    tradingAccount: TradingAccount.paper,
    quoteCurrency: 'USDT',
    riskProfile: RiskProfile.moderate,
    positionSizingMode: PositionSizingMode.fixedPercent,
    defaultLeverageView: '1x',
    themeName: 'dark',
    language: 'English',
    liveTradingAllowed: false,
    hasVerifiedExchange: false,
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
    TradingMode? tradingMode,
    TradingAccount? tradingAccount,
    String? quoteCurrency,
    RiskProfile? riskProfile,
    PositionSizingMode? positionSizingMode,
    String? defaultLeverageView,
    String? themeName,
    String? language,
    bool? liveTradingAllowed,
    bool? hasVerifiedExchange,
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
      tradingMode: tradingMode ?? this.tradingMode,
      tradingAccount: tradingAccount ?? this.tradingAccount,
      quoteCurrency: quoteCurrency ?? this.quoteCurrency,
      riskProfile: riskProfile ?? this.riskProfile,
      positionSizingMode: positionSizingMode ?? this.positionSizingMode,
      defaultLeverageView: defaultLeverageView ?? this.defaultLeverageView,
      themeName: themeName ?? this.themeName,
      language: language ?? this.language,
      liveTradingAllowed: liveTradingAllowed ?? this.liveTradingAllowed,
      hasVerifiedExchange: hasVerifiedExchange ?? this.hasVerifiedExchange,
    );
  }
}
