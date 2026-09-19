import '../../domain/entities/live_order.dart';

class LiveOrderModel {
  const LiveOrderModel(this.order);

  final LiveOrder order;

  factory LiveOrderModel.fromJson(Map<String, dynamic> json) {
    return LiveOrderModel(
      LiveOrder(
        id: json['id'] as String,
        signalId: json['signalId'] as String?,
        parentOrderId: json['parentOrderId'] as String?,
        clientOrderId: json['clientOrderId'] as String,
        exchangeOrderId: json['exchangeOrderId'] as String?,
        symbol: json['symbol'] as String,
        side: _side(json['side'] as String?),
        type: _type(json['type'] as String?),
        purpose: _purpose(json['purpose'] as String?),
        status: _status(json['status'] as String?),
        protectionStatus: _protection(json['protectionStatus'] as String?),
        requestedQuantity: _num(json['requestedQuantity']),
        executedQuantity: _num(json['executedQuantity']),
        remainingQuantity: _num(json['remainingQuantity']),
        price: _numOrNull(json['price']),
        stopPrice: _numOrNull(json['stopPrice']),
        avgFillPrice: _numOrNull(json['avgFillPrice']),
        cumulativeQuoteQty: _numOrNull(json['cumulativeQuoteQty']),
        fees: _numOrNull(json['fees']),
        feeAsset: json['feeAsset'] as String?,
        rejectReason: json['rejectReason'] as String?,
        submittedAt: _date(json['submittedAt']),
        lastFillAt: _date(json['lastFillAt']),
        completedAt: _date(json['completedAt']),
        createdAt: _date(json['createdAt']),
      ),
    );
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

  static DateTime? _date(dynamic v) {
    if (v == null) return null;
    return DateTime.tryParse(v.toString());
  }

  static LiveSide _side(String? raw) =>
      raw?.toUpperCase() == 'SHORT' ? LiveSide.short : LiveSide.long;

  static LiveOrderType _type(String? raw) => switch (raw?.toUpperCase()) {
        'MARKET' => LiveOrderType.market,
        'LIMIT' => LiveOrderType.limit,
        'STOP_LOSS_LIMIT' => LiveOrderType.stopLossLimit,
        'TAKE_PROFIT_LIMIT' => LiveOrderType.takeProfitLimit,
        _ => LiveOrderType.market,
      };

  static LiveOrderPurpose _purpose(String? raw) => switch (raw?.toUpperCase()) {
        'ENTRY' => LiveOrderPurpose.entry,
        'STOP_LOSS' => LiveOrderPurpose.stopLoss,
        'TAKE_PROFIT' => LiveOrderPurpose.takeProfit,
        'MANUAL_CLOSE' => LiveOrderPurpose.manualClose,
        'EMERGENCY_CLOSE' => LiveOrderPurpose.emergencyClose,
        _ => LiveOrderPurpose.entry,
      };

  static LiveProtectionStatus _protection(String? raw) => switch (raw?.toUpperCase()) {
        'PENDING' => LiveProtectionStatus.pending,
        'PROTECTED' => LiveProtectionStatus.protected_,
        'PROTECTION_FAILED' => LiveProtectionStatus.protectionFailed,
        _ => LiveProtectionStatus.notApplicable,
      };

  static LiveOrderStatus _status(String? raw) => switch (raw?.toUpperCase()) {
        'CREATED' => LiveOrderStatus.created,
        'SUBMITTING' => LiveOrderStatus.submitting,
        'SUBMITTED' => LiveOrderStatus.submitted,
        'ACKNOWLEDGED' => LiveOrderStatus.acknowledged,
        'PARTIALLY_FILLED' => LiveOrderStatus.partiallyFilled,
        'FILLED' => LiveOrderStatus.filled,
        'CANCEL_REQUESTED' => LiveOrderStatus.cancelRequested,
        'CANCELLED' => LiveOrderStatus.cancelled,
        'REJECTED' => LiveOrderStatus.rejected,
        'EXPIRED' => LiveOrderStatus.expired,
        'FAILED' => LiveOrderStatus.failed,
        'RECONCILING' => LiveOrderStatus.reconciling,
        _ => LiveOrderStatus.unknown,
      };
}
