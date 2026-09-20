import '../../domain/entities/backtest_equity_point.dart';

class BacktestEquityModel {
  const BacktestEquityModel(this.point);
  final BacktestEquityPoint point;

  factory BacktestEquityModel.fromJson(Map<String, dynamic> json) {
    return BacktestEquityModel(BacktestEquityPoint(
      time: DateTime.parse(json['time'].toString()),
      equity: _num(json['equity']),
      availableBalance: _num(json['availableBalance']),
      unrealizedPnl: _num(json['unrealizedPnl']),
      realizedPnl: _num(json['realizedPnl']),
      peakEquity: _num(json['peakEquity']),
      drawdown: _num(json['drawdown']),
      drawdownPct: _numOrNull(json['drawdownPct']),
    ));
  }

  static double _num(dynamic v) {
    if (v == null) return 0;
    if (v is num) return v.toDouble();
    return double.tryParse(v.toString()) ?? 0;
  }

  static double? _numOrNull(dynamic v) {
    if (v == null) return null;
    if (v is num) return v.toDouble();
    return double.tryParse(v.toString());
  }
}
