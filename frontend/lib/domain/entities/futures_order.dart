enum FuturesSide { long, short }

enum FuturesOrderType { market, limit, stopMarket, takeProfitMarket }

enum FuturesOrderPurpose { entry, stopLoss, takeProfit, manualClose, emergencyClose }

enum FuturesOrderStatus {
  created, submitting, submitted, acknowledged,
  partiallyFilled, filled,
  cancelRequested, cancelled,
  rejected, expired, failed, unknown, reconciling,
}

class FuturesOrder {
  const FuturesOrder({
    required this.id,
    this.signalId,
    this.parentOrderId,
    required this.clientOrderId,
    this.exchangeOrderId,
    required this.symbol,
    required this.side,
    required this.positionSide,
    required this.type,
    required this.purpose,
    required this.status,
    required this.reduceOnly,
    required this.leverage,
    required this.requestedQuantity,
    required this.executedQuantity,
    this.avgFillPrice,
    this.cumulativeQuoteQty,
    this.fees,
    this.feeAsset,
    this.rejectReason,
    this.submittedAt,
    this.lastFillAt,
    this.completedAt,
    this.createdAt,
  });

  final String id;
  final String? signalId;
  final String? parentOrderId;
  final String clientOrderId;
  final String? exchangeOrderId;
  final String symbol;
  final FuturesSide side;
  final FuturesSide positionSide;
  final FuturesOrderType type;
  final FuturesOrderPurpose purpose;
  final FuturesOrderStatus status;
  final bool reduceOnly;
  final int leverage;
  final double requestedQuantity;
  final double executedQuantity;
  final double? avgFillPrice;
  final double? cumulativeQuoteQty;
  final double? fees;
  final String? feeAsset;
  final String? rejectReason;
  final DateTime? submittedAt;
  final DateTime? lastFillAt;
  final DateTime? completedAt;
  final DateTime? createdAt;

  bool get isTerminal => switch (status) {
        FuturesOrderStatus.filled ||
        FuturesOrderStatus.cancelled ||
        FuturesOrderStatus.rejected ||
        FuturesOrderStatus.expired ||
        FuturesOrderStatus.failed =>
          true,
        _ => false,
      };
}
