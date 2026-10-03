enum BacktestStatus { queued, running, completed, failed, cancelled }

enum BacktestTradingMode { spot, futures, options }

enum BacktestExecutionModel { nextCandleOpen, sameCandleClose }

enum BacktestSameCandlePolicy { conservative, slFirst, tpFirst, requireLowerTimeframe }

class BacktestRun {
  const BacktestRun({
    required this.id,
    required this.status,
    required this.strategyId,
    required this.strategyVersion,
    required this.engineVersion,
    required this.tradingMode,
    required this.symbol,
    required this.timeframe,
    required this.startDate,
    required this.endDate,
    required this.initialCapital,
    required this.riskPerTradePct,
    required this.feePct,
    required this.slippagePct,
    required this.leverage,
    required this.executionModel,
    required this.sameCandlePolicy,
    required this.configurationHash,
    required this.processedCandles,
    required this.totalCandles,
    this.failureReason,
    this.startedAt,
    this.completedAt,
    this.createdAt,
    this.finalEquity,
    this.totalNetPnl,
    this.totalReturnPct,
    this.maxDrawdown,
    this.maxDrawdownPct,
    this.winRatePct,
    this.profitFactor,
    this.sharpeRatio,
    this.sortinoRatio,
    this.totalFees,
    this.grossProfit,
    this.grossLoss,
    this.totalTrades = 0,
    this.winningTrades = 0,
    this.losingTrades = 0,
    this.liquidations = 0,
    this.averageWin,
    this.averageLoss,
    this.largestWin,
    this.largestLoss,
    this.expectancy,
  });

  final String id;
  final BacktestStatus status;
  final String strategyId;
  final String strategyVersion;
  final String engineVersion;
  final BacktestTradingMode tradingMode;
  final String symbol;
  final String timeframe;
  final DateTime startDate;
  final DateTime endDate;
  final double initialCapital;
  final double riskPerTradePct;
  final double feePct;
  final double slippagePct;
  final int leverage;
  final BacktestExecutionModel executionModel;
  final BacktestSameCandlePolicy sameCandlePolicy;
  final String configurationHash;
  final int processedCandles;
  final int totalCandles;
  final String? failureReason;
  final DateTime? startedAt;
  final DateTime? completedAt;
  final DateTime? createdAt;
  final double? finalEquity;
  final double? totalNetPnl;
  final double? totalReturnPct;
  final double? maxDrawdown;
  final double? maxDrawdownPct;
  final double? winRatePct;
  final double? profitFactor;
  final double? sharpeRatio;
  final double? sortinoRatio;
  final double? totalFees;
  final double? grossProfit;
  final double? grossLoss;
  final int totalTrades;
  final int winningTrades;
  final int losingTrades;
  final int liquidations;
  final double? averageWin;
  final double? averageLoss;
  final double? largestWin;
  final double? largestLoss;
  final double? expectancy;

  bool get isTerminal =>
      status == BacktestStatus.completed ||
      status == BacktestStatus.failed ||
      status == BacktestStatus.cancelled;

  double get progressPct =>
      totalCandles == 0 ? 0.0 : (processedCandles / totalCandles).clamp(0.0, 1.0);
}
