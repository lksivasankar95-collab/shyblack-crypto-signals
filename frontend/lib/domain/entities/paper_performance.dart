class PaperPerformance {
  const PaperPerformance({
    required this.totalTrades,
    required this.winningTrades,
    required this.losingTrades,
    required this.winRatePct,
    required this.totalNetPnl,
    required this.averageWin,
    required this.averageLoss,
    required this.profitFactor,
    required this.bestTradePnl,
    required this.worstTradePnl,
    required this.totalFees,
    required this.returnPct,
  });

  final int totalTrades;
  final int winningTrades;
  final int losingTrades;
  final double winRatePct;
  final double totalNetPnl;
  final double averageWin;
  final double averageLoss;
  final double profitFactor;
  final double bestTradePnl;
  final double worstTradePnl;
  final double totalFees;
  final double returnPct;
}
