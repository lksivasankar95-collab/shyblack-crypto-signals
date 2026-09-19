class PaperAccount {
  const PaperAccount({
    required this.id,
    required this.quoteCurrency,
    required this.initialBalance,
    required this.availableBalance,
    required this.invested,
    required this.totalBalance,
    required this.equity,
    required this.realizedPnl,
    required this.unrealizedPnl,
    required this.totalFees,
    required this.totalTrades,
    required this.winningTrades,
    required this.losingTrades,
    required this.winRatePct,
    this.createdAt,
  });

  final String id;
  final String quoteCurrency;
  final double initialBalance;
  final double availableBalance;
  final double invested;
  final double totalBalance;
  final double equity;
  final double realizedPnl;
  final double unrealizedPnl;
  final double totalFees;
  final int totalTrades;
  final int winningTrades;
  final int losingTrades;
  final double winRatePct;
  final DateTime? createdAt;

  double get returnPct {
    if (initialBalance == 0) return 0;
    return ((equity - initialBalance) / initialBalance) * 100;
  }
}
