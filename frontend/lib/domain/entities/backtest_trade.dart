enum BacktestSide { long, short }

enum BacktestExitReason {
  stopLoss,
  takeProfit,
  liquidation,
  endOfTest,
  manualSimulated,
  cancelledRun,
}

class BacktestTrade {
  const BacktestTrade({
    required this.id,
    this.signalId,
    required this.symbol,
    required this.side,
    required this.quantity,
    required this.entryPrice,
    required this.exitPrice,
    required this.notional,
    this.stopLoss,
    this.takeProfit,
    required this.entryFee,
    required this.exitFee,
    required this.grossPnl,
    required this.netPnl,
    this.rMultiple,
    required this.leverage,
    required this.exitReason,
    required this.entryTime,
    required this.exitTime,
    required this.holdingSeconds,
  });

  final String id;
  final String? signalId;
  final String symbol;
  final BacktestSide side;
  final double quantity;
  final double entryPrice;
  final double exitPrice;
  final double notional;
  final double? stopLoss;
  final double? takeProfit;
  final double entryFee;
  final double exitFee;
  final double grossPnl;
  final double netPnl;
  final double? rMultiple;
  final int leverage;
  final BacktestExitReason exitReason;
  final DateTime entryTime;
  final DateTime exitTime;
  final int holdingSeconds;
}
