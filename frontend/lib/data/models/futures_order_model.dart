import '../../domain/entities/futures_order.dart';

class FuturesOrderModel {
  const FuturesOrderModel(this.order);
  final FuturesOrder order;

  factory FuturesOrderModel.fromJson(Map<String, dynamic> json) {
    return FuturesOrderModel(FuturesOrder(
      id: json['id'] as String,
      signalId: json['signalId'] as String?,
      parentOrderId: json['parentOrderId'] as String?,
      clientOrderId: json['clientOrderId'] as String,
      exchangeOrderId: json['exchangeOrderId'] as String?,
      symbol: json['symbol'] as String,
      side: _side(json['side'] as String?),
      positionSide: _side(json['positionSide'] as String?),
      type: _type(json['type'] as String?),
      purpose: _purpose(json['purpose'] as String?),
      status: _status(json['status'] as String?),
      reduceOnly: json['reduceOnly'] as bool? ?? false,
      leverage: (json['leverage'] as num?)?.toInt() ?? 1,
      requestedQuantity: _num(json['requestedQuantity']),
      executedQuantity: _num(json['executedQuantity']),
      avgFillPrice: _numOrNull(json['avgFillPrice']),
      cumulativeQuoteQty: _numOrNull(json['cumulativeQuoteQty']),
      fees: _numOrNull(json['fees']),
      feeAsset: json['feeAsset'] as String?,
      rejectReason: json['rejectReason'] as String?,
      submittedAt: _date(json['submittedAt']),
      lastFillAt: _date(json['lastFillAt']),
      completedAt: _date(json['completedAt']),
      createdAt: _date(json['createdAt']),
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

  static DateTime? _date(dynamic v) => v == null ? null : DateTime.tryParse(v.toString());

  static FuturesSide _side(String? raw) =>
      raw?.toUpperCase() == 'SHORT' ? FuturesSide.short : FuturesSide.long;

  static FuturesOrderType _type(String? raw) => switch (raw?.toUpperCase()) {
        'MARKET' => FuturesOrderType.market,
        'LIMIT' => FuturesOrderType.limit,
        'STOP_MARKET' => FuturesOrderType.stopMarket,
        'TAKE_PROFIT_MARKET' => FuturesOrderType.takeProfitMarket,
        _ => FuturesOrderType.market,
      };

  static FuturesOrderPurpose _purpose(String? raw) => switch (raw?.toUpperCase()) {
        'ENTRY' => FuturesOrderPurpose.entry,
        'STOP_LOSS' => FuturesOrderPurpose.stopLoss,
        'TAKE_PROFIT' => FuturesOrderPurpose.takeProfit,
        'MANUAL_CLOSE' => FuturesOrderPurpose.manualClose,
        'EMERGENCY_CLOSE' => FuturesOrderPurpose.emergencyClose,
        _ => FuturesOrderPurpose.entry,
      };

  static FuturesOrderStatus _status(String? raw) => switch (raw?.toUpperCase()) {
        'CREATED' => FuturesOrderStatus.created,
        'SUBMITTING' => FuturesOrderStatus.submitting,
        'SUBMITTED' => FuturesOrderStatus.submitted,
        'ACKNOWLEDGED' => FuturesOrderStatus.acknowledged,
        'PARTIALLY_FILLED' => FuturesOrderStatus.partiallyFilled,
        'FILLED' => FuturesOrderStatus.filled,
        'CANCEL_REQUESTED' => FuturesOrderStatus.cancelRequested,
        'CANCELLED' => FuturesOrderStatus.cancelled,
        'REJECTED' => FuturesOrderStatus.rejected,
        'EXPIRED' => FuturesOrderStatus.expired,
        'FAILED' => FuturesOrderStatus.failed,
        'RECONCILING' => FuturesOrderStatus.reconciling,
        _ => FuturesOrderStatus.unknown,
      };
}
