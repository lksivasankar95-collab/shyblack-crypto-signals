enum PaperPositionSide { long, short }

enum PaperPositionStatus { open, closed }

enum PaperCloseReason {
  stopLoss,
  takeProfit,
  manual,
  signalExpired,
  system,
  reset,
}

class PaperPosition {
  const PaperPosition({
    required this.id,
    this.signalId,
    required this.symbol,
    required this.side,
    required this.quantity,
    required this.entryPrice,
    this.currentPrice,
    this.exitPrice,
    this.stopLoss,
    this.takeProfit1,
    this.takeProfit2,
    this.takeProfit3,
    this.notional,
    this.entryFee,
    this.exitFee,
    required this.realizedPnl,
    required this.unrealizedPnl,
    required this.unrealizedPnlPct,
    required this.status,
    this.closeReason,
    this.openedAt,
    this.closedAt,
    this.createdAt,
  });

  final String id;
  final String? signalId;
  final String symbol;
  final PaperPositionSide side;
  final double quantity;
  final double entryPrice;
  final double? currentPrice;
  final double? exitPrice;
  final double? stopLoss;
  final double? takeProfit1;
  final double? takeProfit2;
  final double? takeProfit3;
  final double? notional;
  final double? entryFee;
  final double? exitFee;
  final double realizedPnl;
  final double unrealizedPnl;
  final double unrealizedPnlPct;
  final PaperPositionStatus status;
  final PaperCloseReason? closeReason;
  final DateTime? openedAt;
  final DateTime? closedAt;
  final DateTime? createdAt;
}
