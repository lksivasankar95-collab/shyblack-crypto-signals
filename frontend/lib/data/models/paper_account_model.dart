import '../../domain/entities/paper_account.dart';

class PaperAccountModel {
  const PaperAccountModel(this.account);

  final PaperAccount account;

  factory PaperAccountModel.fromJson(Map<String, dynamic> json) {
    return PaperAccountModel(
      PaperAccount(
        id: json['id'] as String,
        quoteCurrency: json['quoteCurrency'] as String? ?? 'USDT',
        initialBalance: _num(json['initialBalance']),
        availableBalance: _num(json['availableBalance']),
        invested: _num(json['invested']),
        totalBalance: _num(json['totalBalance']),
        equity: _num(json['equity']),
        realizedPnl: _num(json['realizedPnl']),
        unrealizedPnl: _num(json['unrealizedPnl']),
        totalFees: _num(json['totalFees']),
        totalTrades: (json['totalTrades'] as num?)?.toInt() ?? 0,
        winningTrades: (json['winningTrades'] as num?)?.toInt() ?? 0,
        losingTrades: (json['losingTrades'] as num?)?.toInt() ?? 0,
        winRatePct: _num(json['winRatePct']),
        createdAt: _date(json['createdAt']),
      ),
    );
  }

  static double _num(dynamic v) {
    if (v == null) return 0;
    if (v is num) return v.toDouble();
    return double.tryParse(v.toString()) ?? 0;
  }

  static DateTime? _date(dynamic v) {
    if (v == null) return null;
    return DateTime.tryParse(v.toString());
  }
}
