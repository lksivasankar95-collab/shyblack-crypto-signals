import '../../domain/entities/paper_position.dart';

class PaperPositionModel {
  const PaperPositionModel(this.position);

  final PaperPosition position;

  factory PaperPositionModel.fromJson(Map<String, dynamic> json) {
    return PaperPositionModel(
      PaperPosition(
        id: json['id'] as String,
        signalId: json['signalId'] as String?,
        symbol: json['symbol'] as String,
        side: _side(json['side'] as String?),
        quantity: _num(json['quantity']),
        entryPrice: _num(json['entryPrice']),
        currentPrice: _numOrNull(json['currentPrice']),
        exitPrice: _numOrNull(json['exitPrice']),
        stopLoss: _numOrNull(json['stopLoss']),
        takeProfit1: _numOrNull(json['takeProfit1']),
        takeProfit2: _numOrNull(json['takeProfit2']),
        takeProfit3: _numOrNull(json['takeProfit3']),
        notional: _numOrNull(json['notional']),
        entryFee: _numOrNull(json['entryFee']),
        exitFee: _numOrNull(json['exitFee']),
        realizedPnl: _num(json['realizedPnl']),
        unrealizedPnl: _num(json['unrealizedPnl']),
        unrealizedPnlPct: _num(json['unrealizedPnlPct']),
        status: _status(json['status'] as String?),
        closeReason: _closeReason(json['closeReason'] as String?),
        openedAt: _date(json['openedAt']),
        closedAt: _date(json['closedAt']),
        createdAt: _date(json['createdAt']),
        strategyId: json['strategyId'] as String?,
        strategyVersion: (json['strategyVersion'] as num?)?.toInt(),
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

  static PaperPositionSide _side(String? raw) {
    if (raw == null) return PaperPositionSide.long;
    return raw.toUpperCase() == 'SHORT'
        ? PaperPositionSide.short
        : PaperPositionSide.long;
  }

  static PaperPositionStatus _status(String? raw) {
    return raw?.toUpperCase() == 'CLOSED'
        ? PaperPositionStatus.closed
        : PaperPositionStatus.open;
  }

  static PaperCloseReason? _closeReason(String? raw) {
    if (raw == null) return null;
    return switch (raw.toUpperCase()) {
      'STOP_LOSS' => PaperCloseReason.stopLoss,
      'TAKE_PROFIT' => PaperCloseReason.takeProfit,
      'MANUAL' => PaperCloseReason.manual,
      'SIGNAL_EXPIRED' => PaperCloseReason.signalExpired,
      'RESET' => PaperCloseReason.reset,
      _ => PaperCloseReason.system,
    };
  }
}
