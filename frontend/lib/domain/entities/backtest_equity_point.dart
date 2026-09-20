class BacktestEquityPoint {
  const BacktestEquityPoint({
    required this.time,
    required this.equity,
    required this.availableBalance,
    required this.unrealizedPnl,
    required this.realizedPnl,
    required this.peakEquity,
    required this.drawdown,
    this.drawdownPct,
  });

  final DateTime time;
  final double equity;
  final double availableBalance;
  final double unrealizedPnl;
  final double realizedPnl;
  final double peakEquity;
  final double drawdown;
  final double? drawdownPct;
}
