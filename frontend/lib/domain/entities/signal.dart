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
}
