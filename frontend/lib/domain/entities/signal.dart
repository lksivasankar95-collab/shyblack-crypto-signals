enum SignalStatus { active, pending, closed, draft }

extension SignalStatusLabel on SignalStatus {
  String get apiValue => name.toUpperCase();

  String get label => switch (this) {
    SignalStatus.active => 'Active',
    SignalStatus.pending => 'Pending',
    SignalStatus.closed => 'Closed',
    SignalStatus.draft => 'Draft',
  };

  static SignalStatus parse(String value) {
    return switch (value.toUpperCase()) {
      'PENDING' => SignalStatus.pending,
      'CLOSED' => SignalStatus.closed,
      'DRAFT' => SignalStatus.draft,
      _ => SignalStatus.active,
    };
  }
}

enum PositionSide { long, short }

extension PositionSideLabel on PositionSide {
  String get apiValue => name.toUpperCase();

  String get label => name.toUpperCase();

  static PositionSide parse(String value) {
    return value.toUpperCase() == 'SHORT'
        ? PositionSide.short
        : PositionSide.long;
  }
}

enum SignalGrade { strongBuy, buy, watch, noTrade }

extension SignalGradeLabel on SignalGrade {
  String get label => switch (this) {
    SignalGrade.strongBuy => 'STRONG BUY',
    SignalGrade.buy => 'BUY',
    SignalGrade.watch => 'WATCH',
    SignalGrade.noTrade => 'NO TRADE',
  };

  static SignalGrade? parse(String? value) {
    if (value == null) return null;
    return switch (value.toUpperCase()) {
      'STRONG_BUY' => SignalGrade.strongBuy,
      'BUY' => SignalGrade.buy,
      'WATCH' => SignalGrade.watch,
      'NO_TRADE' => SignalGrade.noTrade,
      _ => null,
    };
  }
}

enum EntryType { preBreakout, breakout, breakoutRetest }

extension EntryTypeLabel on EntryType {
  String get label => switch (this) {
    EntryType.preBreakout => 'Pre-Breakout',
    EntryType.breakout => 'Breakout',
    EntryType.breakoutRetest => 'Breakout Retest',
  };

  static EntryType? parse(String? value) {
    if (value == null) return null;
    return switch (value.toUpperCase()) {
      'BREAKOUT' => EntryType.breakout,
      'BREAKOUT_RETEST' => EntryType.breakoutRetest,
      _ => EntryType.preBreakout,
    };
  }
}

enum MarketRegime { bullish, neutral, bearish }

extension MarketRegimeLabel on MarketRegime {
  String get label => switch (this) {
    MarketRegime.bullish => 'Bullish',
    MarketRegime.neutral => 'Neutral',
    MarketRegime.bearish => 'Bearish',
  };

  static MarketRegime? parse(String? value) {
    if (value == null) return null;
    return switch (value.toUpperCase()) {
      'BULLISH' => MarketRegime.bullish,
      'BEARISH' => MarketRegime.bearish,
      _ => MarketRegime.neutral,
    };
  }
}

class Signal {
  const Signal({
    required this.id,
    required this.symbol,
    required this.status,
    required this.side,
    this.confidence,
    this.entryPrice,
    this.targetPrice,
    this.stopLoss,
    this.strategy,
    this.strategyWinRate,
    this.suggestedRiskPercent,
    this.closedAt,
    this.technicalSummary,
    this.disclaimer,
    required this.createdAt,
    // Spot Morning Plan fields
    this.score,
    this.signalGrade,
    this.entryType,
    this.marketRegime,
    this.targetPrice2,
    this.targetPrice3,
    this.riskReward,
  });

  final String id;
  final String symbol;
  final SignalStatus status;
  final PositionSide side;
  final int? confidence;
  final double? entryPrice;
  final double? targetPrice;
  final double? stopLoss;
  final String? strategy;
  final double? strategyWinRate;
  final double? suggestedRiskPercent;
  final DateTime? closedAt;
  final String? technicalSummary;
  final String? disclaimer;
  final DateTime createdAt;

  // Spot Morning Plan
  final int? score;
  final SignalGrade? signalGrade;
  final EntryType? entryType;
  final MarketRegime? marketRegime;
  final double? targetPrice2;
  final double? targetPrice3;
  final double? riskReward;
}
