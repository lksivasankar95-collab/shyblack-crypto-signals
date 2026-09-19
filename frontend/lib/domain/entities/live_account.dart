enum LiveConnectionStatus { notConnected, connecting, connected, failed, revoked }

enum LiveExchange { binance, bybit, okx, coinbase }

class LiveAccount {
  const LiveAccount({
    required this.id,
    required this.exchange,
    required this.connectionStatus,
    required this.enabled,
    required this.killSwitchActive,
    required this.quoteCurrency,
    this.cachedAvailableBalance,
    this.cachedTotalBalance,
    this.maxNotionalPerTrade,
    required this.maxActivePositions,
    this.dailyLossLimitPct,
    this.lastValidatedAt,
    this.lastValidationMessage,
  });

  final String id;
  final LiveExchange exchange;
  final LiveConnectionStatus connectionStatus;
  final bool enabled;
  final bool killSwitchActive;
  final String quoteCurrency;
  final double? cachedAvailableBalance;
  final double? cachedTotalBalance;
  final double? maxNotionalPerTrade;
  final int maxActivePositions;
  final double? dailyLossLimitPct;
  final DateTime? lastValidatedAt;
  final String? lastValidationMessage;

  bool get isTradingReady => enabled && !killSwitchActive
      && connectionStatus == LiveConnectionStatus.connected;
}
