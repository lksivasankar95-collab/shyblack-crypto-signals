import '../../domain/entities/futures_account.dart' show FuturesMarginMode;
import '../../domain/entities/futures_order.dart' show FuturesSide;
import '../../domain/entities/futures_position.dart';

class FuturesPositionModel {
  const FuturesPositionModel(this.position);
  final FuturesPosition position;

  factory FuturesPositionModel.fromJson(Map<String, dynamic> json) {
    return FuturesPositionModel(FuturesPosition(
      id: json['id'] as String,
      signalId: json['signalId'] as String?,
      entryOrderId: json['entryOrderId'] as String?,
      stopOrderId: json['stopOrderId'] as String?,
      symbol: json['symbol'] as String,
      positionSide: _side(json['positionSide'] as String?),
      marginMode: _margin(json['marginMode'] as String?),
      leverage: (json['leverage'] as num?)?.toInt() ?? 1,
      quantity: _num(json['quantity']),
      entryPrice: _numOrNull(json['entryPrice']),
      exitPrice: _numOrNull(json['exitPrice']),
      stopLoss: _numOrNull(json['stopLoss']),
      takeProfit: _numOrNull(json['takeProfit']),
      initialMargin: _numOrNull(json['initialMargin']),
      liquidationPrice: _numOrNull(json['liquidationPrice']),
      realizedPnl: _num(json['realizedPnl']),
      unrealizedPnl: _num(json['unrealizedPnl']),
      tradingFees: _num(json['tradingFees']),
      fundingFees: _num(json['fundingFees']),
      status: json['status']?.toString().toUpperCase() == 'CLOSED'
          ? FuturesPositionStatus.closed : FuturesPositionStatus.open,
      protectionStatus: _protection(json['protectionStatus'] as String?),
      openedAt: _date(json['openedAt']),
      closedAt: _date(json['closedAt']),
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

  static FuturesMarginMode _margin(String? raw) =>
      raw?.toUpperCase() == 'CROSS' ? FuturesMarginMode.cross : FuturesMarginMode.isolated;

  static FuturesProtectionStatus _protection(String? raw) => switch (raw?.toUpperCase()) {
        'PENDING' => FuturesProtectionStatus.pending,
        'PROTECTED' => FuturesProtectionStatus.protected_,
        'PROTECTION_FAILED' => FuturesProtectionStatus.protectionFailed,
        _ => FuturesProtectionStatus.notApplicable,
      };
}
