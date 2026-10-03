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
    // Spot Morning Plan fields
    this.score,
    this.signalGrade,
    this.entryType,
    this.marketRegime,
    this.targetPrice2,
    this.targetPrice3,
    this.riskReward,
    this.tradingMode,
    this.nfmContext,
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

  // Spot Morning Plan fields
  final int? score;
  final SignalGrade? signalGrade;
  final EntryType? entryType;
  final MarketRegime? marketRegime;
  final double? targetPrice2;
  final double? targetPrice3;
  final double? riskReward;
  final String? tradingMode;
  final NfmContext? nfmContext;

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
      // Spot Morning Plan fields
      score: (json['score'] as num?)?.toInt(),
      signalGrade: SignalGradeLabel.parse(json['signalGrade'] as String?),
      entryType: EntryTypeLabel.parse(json['entryType'] as String?),
      marketRegime: MarketRegimeLabel.parse(json['marketRegime'] as String?),
      targetPrice2: (json['targetPrice2'] as num?)?.toDouble(),
      targetPrice3: (json['targetPrice3'] as num?)?.toDouble(),
      riskReward: (json['riskReward'] as num?)?.toDouble(),
      tradingMode: json['tradingMode'] as String?,
      nfmContext: _parseNfmContext(json['nfmContext']),
    );
  }

  static NfmContext? _parseNfmContext(dynamic value) {
    if (value is! Map) {
      return null;
    }
    final json = Map<String, dynamic>.from(value);
    return NfmContext(
      newsEventId: json['newsEventId'] as String?,
      eventType: json['eventType'] as String?,
      eventCategory: json['eventCategory'] as String?,
      eventStage: json['eventStage'] as String?,
      sourceTier: json['sourceTier'] as String?,
      source: json['source'] as String?,
      eventTime: _parseDate(json['eventTime']),
      expectedValue: (json['expectedValue'] as num?)?.toDouble(),
      actualValue: (json['actualValue'] as num?)?.toDouble(),
      surpriseValue: (json['surpriseValue'] as num?)?.toDouble(),
      surpriseDirection: json['surpriseDirection'] as String?,
      priceReactionPct: (json['priceReactionPct'] as num?)?.toDouble(),
      volumeMultiplier: (json['volumeMultiplier'] as num?)?.toDouble(),
      openInterestChangePct: (json['openInterestChangePct'] as num?)
          ?.toDouble(),
      fundingState: json['fundingState'] as String?,
      liquidationState: json['liquidationState'] as String?,
      eventConfluenceScore: (json['eventConfluenceScore'] as num?)?.toInt(),
      marketInterpretation: json['marketInterpretation'] as String?,
      configVersion: json['configVersion'] as String?,
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
      score: score,
      signalGrade: signalGrade,
      entryType: entryType,
      marketRegime: marketRegime,
      targetPrice2: targetPrice2,
      targetPrice3: targetPrice3,
      riskReward: riskReward,
      tradingMode: tradingMode,
      nfmContext: nfmContext,
    );
  }
}
