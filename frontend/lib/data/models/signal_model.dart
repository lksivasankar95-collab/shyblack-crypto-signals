import '../../domain/entities/signal.dart';

class SignalModel {
  const SignalModel({
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

  factory SignalModel.fromJson(Map<String, dynamic> json) {
    return SignalModel(
      id: json['id'] as String,
      symbol: json['symbol'] as String,
      status: SignalStatusLabel.parse(json['status'] as String? ?? ''),
      side: PositionSideLabel.parse(json['side'] as String? ?? 'LONG'),
      confidence: (json['confidence'] as num?)?.toInt(),
      entryPrice: (json['entryPrice'] as num?)?.toDouble(),
      targetPrice: (json['targetPrice'] as num?)?.toDouble(),
      stopLoss: (json['stopLoss'] as num?)?.toDouble(),
      strategy: json['strategy'] as String?,
      strategyWinRate: (json['strategyWinRate'] as num?)?.toDouble(),
      suggestedRiskPercent: (json['suggestedRiskPercent'] as num?)?.toDouble(),
      closedAt: _parseDate(json['closedAt']),
      technicalSummary: json['technicalSummary'] as String?,
      disclaimer: json['disclaimer'] as String?,
      createdAt:
          _parseDate(json['createdAt']) ??
          DateTime.fromMillisecondsSinceEpoch(0),
    );
  }

  static DateTime? _parseDate(dynamic value) {
    if (value == null) {
      return null;
    }
    if (value is int) {
      return DateTime.fromMillisecondsSinceEpoch(value);
    }
    return DateTime.tryParse(value.toString());
  }

  Signal toEntity() {
    return Signal(
      id: id,
      symbol: symbol,
      status: status,
      side: side,
      confidence: confidence,
      entryPrice: entryPrice,
      targetPrice: targetPrice,
      stopLoss: stopLoss,
      strategy: strategy,
      strategyWinRate: strategyWinRate,
      suggestedRiskPercent: suggestedRiskPercent,
      closedAt: closedAt,
      technicalSummary: technicalSummary,
      disclaimer: disclaimer,
      createdAt: createdAt,
    );
  }
}
