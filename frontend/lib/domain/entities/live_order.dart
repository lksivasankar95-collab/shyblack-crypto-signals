enum LiveOrderStatus {
  created, submitting, submitted, acknowledged,
  partiallyFilled, filled,
  cancelRequested, cancelled,
  rejected, expired, failed, unknown, reconciling,
}

enum LiveOrderPurpose { entry, stopLoss, takeProfit, manualClose, emergencyClose }

enum LiveOrderType { market, limit, stopLossLimit, takeProfitLimit }

enum LiveSide { long, short }

class LiveOrder {
  const LiveOrder({
    required this.id,
    this.signalId,
    this.parentOrderId,
    required this.clientOrderId,
    this.exchangeOrderId,
    required this.symbol,
    required this.side,
    required this.type,
    required this.purpose,
    required this.status,
    required this.requestedQuantity,
    required this.executedQuantity,
    required this.remainingQuantity,
    this.price,
    this.stopPrice,
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
  final LiveSide side;
  final LiveOrderType type;
  final LiveOrderPurpose purpose;
  final LiveOrderStatus status;
  final double requestedQuantity;
  final double executedQuantity;
  final double remainingQuantity;
  final double? price;
  final double? stopPrice;
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
        LiveOrderStatus.filled ||
        LiveOrderStatus.cancelled ||
        LiveOrderStatus.rejected ||
        LiveOrderStatus.expired ||
        LiveOrderStatus.failed =>
          true,
        _ => false,
      };
}
