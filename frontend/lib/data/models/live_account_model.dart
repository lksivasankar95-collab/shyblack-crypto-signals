import '../../domain/entities/live_account.dart';

class LiveAccountModel {
  const LiveAccountModel(this.account);

  final LiveAccount account;

  factory LiveAccountModel.fromJson(Map<String, dynamic> json) {
    return LiveAccountModel(
      LiveAccount(
        id: json['id'] as String,
        exchange: _exchange(json['exchange'] as String?),
        connectionStatus: _status(json['connectionStatus'] as String?),
        enabled: json['enabled'] as bool? ?? false,
        killSwitchActive: json['killSwitchActive'] as bool? ?? false,
        quoteCurrency: json['quoteCurrency'] as String? ?? 'USDT',
        cachedAvailableBalance: _numOrNull(json['cachedAvailableBalance']),
        cachedTotalBalance: _numOrNull(json['cachedTotalBalance']),
        maxNotionalPerTrade: _numOrNull(json['maxNotionalPerTrade']),
        maxActivePositions: (json['maxActivePositions'] as num?)?.toInt() ?? 3,
        dailyLossLimitPct: _numOrNull(json['dailyLossLimitPct']),
        lastValidatedAt: _date(json['lastValidatedAt']),
        lastValidationMessage: json['lastValidationMessage'] as String?,
      ),
    );
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

  static LiveExchange _exchange(String? raw) {
    return switch (raw?.toUpperCase()) {
      'BINANCE' => LiveExchange.binance,
      'BYBIT' => LiveExchange.bybit,
      'OKX' => LiveExchange.okx,
      'COINBASE' => LiveExchange.coinbase,
      _ => LiveExchange.binance,
    };
  }

  static LiveConnectionStatus _status(String? raw) {
    return switch (raw?.toUpperCase()) {
      'CONNECTED' => LiveConnectionStatus.connected,
      'CONNECTING' => LiveConnectionStatus.connecting,
      'FAILED' => LiveConnectionStatus.failed,
      'REVOKED' => LiveConnectionStatus.revoked,
      _ => LiveConnectionStatus.notConnected,
    };
  }
}
