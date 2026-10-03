import '../../domain/entities/backtest_run.dart';

class BacktestRunModel {
  const BacktestRunModel(this.run);
  final BacktestRun run;

  factory BacktestRunModel.fromJson(Map<String, dynamic> json) {
    return BacktestRunModel(BacktestRun(
      id: json['id'] as String,
      status: _status(json['status'] as String?),
      strategyId: json['strategyId'] as String? ?? '',
      strategyVersion: json['strategyVersion'] as String? ?? '',
      engineVersion: json['engineVersion'] as String? ?? '',
      tradingMode: _mode(json['tradingMode'] as String?),
      symbol: json['symbol'] as String? ?? '',
      timeframe: json['timeframe'] as String? ?? '',
      startDate: _date(json['startDate'])!,
      endDate: _date(json['endDate'])!,
      initialCapital: _num(json['initialCapital']),
      riskPerTradePct: _num(json['riskPerTradePct']),
      feePct: _num(json['feePct']),
      slippagePct: _num(json['slippagePct']),
      leverage: (json['leverage'] as num?)?.toInt() ?? 1,
      executionModel: _exec(json['executionModel'] as String?),
      sameCandlePolicy: _scp(json['sameCandlePolicy'] as String?),
      configurationHash: json['configurationHash'] as String? ?? '',
      processedCandles: (json['processedCandles'] as num?)?.toInt() ?? 0,
      totalCandles: (json['totalCandles'] as num?)?.toInt() ?? 0,
      failureReason: json['failureReason'] as String?,
      startedAt: _date(json['startedAt']),
      completedAt: _date(json['completedAt']),
      createdAt: _date(json['createdAt']),
      finalEquity: _numOrNull(json['finalEquity']),
      totalNetPnl: _numOrNull(json['totalNetPnl']),
      totalReturnPct: _numOrNull(json['totalReturnPct']),
      maxDrawdown: _numOrNull(json['maxDrawdown']),
      maxDrawdownPct: _numOrNull(json['maxDrawdownPct']),
      winRatePct: _numOrNull(json['winRatePct']),
      profitFactor: _numOrNull(json['profitFactor']),
      sharpeRatio: _numOrNull(json['sharpeRatio']),
      sortinoRatio: _numOrNull(json['sortinoRatio']),
      totalFees: _numOrNull(json['totalFees']),
      grossProfit: _numOrNull(json['grossProfit']),
      grossLoss: _numOrNull(json['grossLoss']),
      totalTrades: (json['totalTrades'] as num?)?.toInt() ?? 0,
      winningTrades: (json['winningTrades'] as num?)?.toInt() ?? 0,
      losingTrades: (json['losingTrades'] as num?)?.toInt() ?? 0,
      liquidations: (json['liquidations'] as num?)?.toInt() ?? 0,
      averageWin: _numOrNull(json['averageWin']),
      averageLoss: _numOrNull(json['averageLoss']),
      largestWin: _numOrNull(json['largestWin']),
      largestLoss: _numOrNull(json['largestLoss']),
      expectancy: _numOrNull(json['expectancy']),
    ));
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

  static DateTime? _date(dynamic v) => v == null ? null : DateTime.tryParse(v.toString());

  static BacktestStatus _status(String? raw) => switch (raw?.toUpperCase()) {
        'QUEUED' => BacktestStatus.queued,
        'RUNNING' => BacktestStatus.running,
        'COMPLETED' => BacktestStatus.completed,
        'FAILED' => BacktestStatus.failed,
        'CANCELLED' => BacktestStatus.cancelled,
        _ => BacktestStatus.queued,
      };

  static BacktestTradingMode _mode(String? raw) => switch (raw?.toUpperCase()) {
        'FUTURES' => BacktestTradingMode.futures,
        'OPTIONS' => BacktestTradingMode.options,
        _ => BacktestTradingMode.spot,
      };

  static BacktestExecutionModel _exec(String? raw) =>
      raw?.toUpperCase() == 'SAME_CANDLE_CLOSE'
          ? BacktestExecutionModel.sameCandleClose
          : BacktestExecutionModel.nextCandleOpen;

  static BacktestSameCandlePolicy _scp(String? raw) => switch (raw?.toUpperCase()) {
        'CONSERVATIVE' => BacktestSameCandlePolicy.conservative,
        'TP_FIRST' => BacktestSameCandlePolicy.tpFirst,
        'REQUIRE_LOWER_TIMEFRAME' => BacktestSameCandlePolicy.requireLowerTimeframe,
        _ => BacktestSameCandlePolicy.slFirst,
      };
}
