import '../../domain/entities/paper_performance.dart';

class PaperPerformanceModel {
  const PaperPerformanceModel(this.performance);

  final PaperPerformance performance;

  factory PaperPerformanceModel.fromJson(Map<String, dynamic> json) {
    return PaperPerformanceModel(
      PaperPerformance(
        totalTrades: (json['totalTrades'] as num?)?.toInt() ?? 0,
        winningTrades: (json['winningTrades'] as num?)?.toInt() ?? 0,
        losingTrades: (json['losingTrades'] as num?)?.toInt() ?? 0,
        winRatePct: _num(json['winRatePct']),
        totalNetPnl: _num(json['totalNetPnl']),
        averageWin: _num(json['averageWin']),
        averageLoss: _num(json['averageLoss']),
        profitFactor: _num(json['profitFactor']),
        bestTradePnl: _num(json['bestTradePnl']),
        worstTradePnl: _num(json['worstTradePnl']),
        totalFees: _num(json['totalFees']),
        returnPct: _num(json['returnPct']),
      ),
    );
  }

  static double _num(dynamic v) {
    if (v == null) return 0;
    if (v is num) return v.toDouble();
    return double.tryParse(v.toString()) ?? 0;
  }
}
