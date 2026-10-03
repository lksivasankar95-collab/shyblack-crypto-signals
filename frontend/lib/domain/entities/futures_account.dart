enum FuturesConnectionStatus { notConnected, connecting, connected, failed, revoked }

enum FuturesMarginMode { isolated, cross }

enum FuturesPositionMode { oneWay, hedge }

class FuturesAccount {
  const FuturesAccount({
    required this.id,
    required this.exchange,
    required this.connectionStatus,
    required this.enabled,
    required this.killSwitchActive,
    required this.acknowledged,
    required this.marginAsset,
    required this.marginMode,
    required this.positionMode,
    required this.maxLeverage,
    this.maxNotionalPerTrade,
    required this.maxActivePositions,
    this.dailyLossLimitPct,
    this.walletBalance,
    this.availableBalance,
    this.marginBalance,
    this.usedMargin,
    this.maintenanceMargin,
    this.unrealizedPnl,
    this.realizedPnlToday,
    this.totalFundingPaid,
    this.lastValidatedAt,
    this.lastValidationMessage,
  });

  final String id;
  final String exchange;
  final FuturesConnectionStatus connectionStatus;
  final bool enabled;
  final bool killSwitchActive;
  final bool acknowledged;
  final String marginAsset;
  final FuturesMarginMode marginMode;
  final FuturesPositionMode positionMode;
  final int maxLeverage;
  final double? maxNotionalPerTrade;
  final int maxActivePositions;
  final double? dailyLossLimitPct;
  final double? walletBalance;
  final double? availableBalance;
  final double? marginBalance;
  final double? usedMargin;
  final double? maintenanceMargin;
  final double? unrealizedPnl;
  final double? realizedPnlToday;
  final double? totalFundingPaid;
  final DateTime? lastValidatedAt;
  final String? lastValidationMessage;

  bool get isTradingReady =>
      enabled && !killSwitchActive && acknowledged &&
      connectionStatus == FuturesConnectionStatus.connected;
}
