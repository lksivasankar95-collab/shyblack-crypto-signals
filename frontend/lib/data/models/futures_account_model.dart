import '../../domain/entities/futures_account.dart';

class FuturesAccountModel {
  const FuturesAccountModel(this.account);
  final FuturesAccount account;

  factory FuturesAccountModel.fromJson(Map<String, dynamic> json) {
    return FuturesAccountModel(FuturesAccount(
      id: json['id'] as String,
      exchange: json['exchange'] as String? ?? 'BINANCE',
      connectionStatus: _status(json['connectionStatus'] as String?),
      enabled: json['enabled'] as bool? ?? false,
      killSwitchActive: json['killSwitchActive'] as bool? ?? false,
      acknowledged: json['acknowledged'] as bool? ?? false,
      marginAsset: json['marginAsset'] as String? ?? 'USDT',
      marginMode: _margin(json['marginMode'] as String?),
      positionMode: _posMode(json['positionMode'] as String?),
      maxLeverage: (json['maxLeverage'] as num?)?.toInt() ?? 1,
      maxNotionalPerTrade: _numOrNull(json['maxNotionalPerTrade']),
      maxActivePositions: (json['maxActivePositions'] as num?)?.toInt() ?? 2,
      dailyLossLimitPct: _numOrNull(json['dailyLossLimitPct']),
      walletBalance: _numOrNull(json['walletBalance']),
      availableBalance: _numOrNull(json['availableBalance']),
      marginBalance: _numOrNull(json['marginBalance']),
      usedMargin: _numOrNull(json['usedMargin']),
      maintenanceMargin: _numOrNull(json['maintenanceMargin']),
      unrealizedPnl: _numOrNull(json['unrealizedPnl']),
      realizedPnlToday: _numOrNull(json['realizedPnlToday']),
      totalFundingPaid: _numOrNull(json['totalFundingPaid']),
      lastValidatedAt: _date(json['lastValidatedAt']),
      lastValidationMessage: json['lastValidationMessage'] as String?,
    ));
  }

  static double? _numOrNull(dynamic v) {
    if (v == null) return null;
    if (v is num) return v.toDouble();
    return double.tryParse(v.toString());
  }

  static DateTime? _date(dynamic v) => v == null ? null : DateTime.tryParse(v.toString());

  static FuturesConnectionStatus _status(String? raw) => switch (raw?.toUpperCase()) {
        'CONNECTED' => FuturesConnectionStatus.connected,
        'CONNECTING' => FuturesConnectionStatus.connecting,
        'FAILED' => FuturesConnectionStatus.failed,
        'REVOKED' => FuturesConnectionStatus.revoked,
        _ => FuturesConnectionStatus.notConnected,
      };

  static FuturesMarginMode _margin(String? raw) =>
      raw?.toUpperCase() == 'CROSS' ? FuturesMarginMode.cross : FuturesMarginMode.isolated;

  static FuturesPositionMode _posMode(String? raw) =>
      raw?.toUpperCase() == 'HEDGE' ? FuturesPositionMode.hedge : FuturesPositionMode.oneWay;
}
