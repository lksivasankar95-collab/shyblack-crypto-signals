import 'futures_account.dart' show FuturesMarginMode;
import 'futures_order.dart' show FuturesSide;

enum FuturesPositionStatus { open, closed }

enum FuturesProtectionStatus { notApplicable, pending, protected_, protectionFailed }

class FuturesPosition {
  const FuturesPosition({
    required this.id,
    this.signalId,
    this.entryOrderId,
    this.stopOrderId,
    required this.symbol,
    required this.positionSide,
    required this.marginMode,
    required this.leverage,
    required this.quantity,
    this.entryPrice,
    this.exitPrice,
    this.stopLoss,
    this.takeProfit,
    this.initialMargin,
    this.liquidationPrice,
    required this.realizedPnl,
    required this.unrealizedPnl,
    required this.tradingFees,
    required this.fundingFees,
    required this.status,
    required this.protectionStatus,
    this.openedAt,
    this.closedAt,
    this.createdAt,
  });

  final String id;
  final String? signalId;
  final String? entryOrderId;
  final String? stopOrderId;
  final String symbol;
  final FuturesSide positionSide;
  final FuturesMarginMode marginMode;
  final int leverage;
  final double quantity;
  final double? entryPrice;
  final double? exitPrice;
  final double? stopLoss;
  final double? takeProfit;
  final double? initialMargin;
  final double? liquidationPrice;
  final double realizedPnl;
  final double unrealizedPnl;
  final double tradingFees;
  final double fundingFees;
  final FuturesPositionStatus status;
  final FuturesProtectionStatus protectionStatus;
  final DateTime? openedAt;
  final DateTime? closedAt;
  final DateTime? createdAt;
}
