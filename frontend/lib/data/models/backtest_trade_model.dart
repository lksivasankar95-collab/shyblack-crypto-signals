import '../../domain/entities/backtest_trade.dart';

class BacktestTradeModel {
  const BacktestTradeModel(this.trade);
  final BacktestTrade trade;

  factory BacktestTradeModel.fromJson(Map<String, dynamic> json) {
    return BacktestTradeModel(BacktestTrade(
      id: json['id'] as String,
      signalId: json['signalId'] as String?,
      symbol: json['symbol'] as String,
      side: json['side']?.toString().toUpperCase() == 'SHORT'
          ? BacktestSide.short : BacktestSide.long,
      quantity: _num(json['quantity']),
      entryPrice: _num(json['entryPrice']),
      exitPrice: _num(json['exitPrice']),
      notional: _num(json['notional']),
      stopLoss: _numOrNull(json['stopLoss']),
      takeProfit: _numOrNull(json['takeProfit']),
      entryFee: _num(json['entryFee']),
      exitFee: _num(json['exitFee']),
      grossPnl: _num(json['grossPnl']),
      netPnl: _num(json['netPnl']),
      rMultiple: _numOrNull(json['rMultiple']),
      leverage: (json['leverage'] as num?)?.toInt() ?? 1,
      exitReason: _reason(json['exitReason'] as String?),
      entryTime: DateTime.parse(json['entryTime'].toString()),
      exitTime: DateTime.parse(json['exitTime'].toString()),
      holdingSeconds: (json['holdingSeconds'] as num?)?.toInt() ?? 0,
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

  static BacktestExitReason _reason(String? raw) => switch (raw?.toUpperCase()) {
        'STOP_LOSS' => BacktestExitReason.stopLoss,
        'TAKE_PROFIT' => BacktestExitReason.takeProfit,
        'LIQUIDATION' => BacktestExitReason.liquidation,
        'END_OF_TEST' => BacktestExitReason.endOfTest,
        'MANUAL_SIMULATED' => BacktestExitReason.manualSimulated,
        'CANCELLED_RUN' => BacktestExitReason.cancelledRun,
        _ => BacktestExitReason.endOfTest,
      };
}
