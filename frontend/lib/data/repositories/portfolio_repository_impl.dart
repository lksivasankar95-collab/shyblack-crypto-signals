import '../../data/datasources/portfolio_remote_data_source.dart';
import '../../data/models/portfolio_account_model.dart';
import '../../domain/entities/portfolio_account.dart';
import '../../domain/repositories/futures_trading_repository.dart';
import '../../domain/repositories/live_trading_repository.dart';
import '../../domain/repositories/portfolio_repository.dart';

class PortfolioRepositoryImpl implements PortfolioRepository {
  PortfolioRepositoryImpl(
    this._remote, {
    LiveTradingRepository? live,
    FuturesTradingRepository? futures,
  }) : _live = live,
       _futures = futures;

  final PortfolioRemoteDataSource _remote;

  /// Existing cancellation paths, reused rather than reimplemented. Null only in
  /// tests that never cancel; [cancelOrder] fails loudly if one is missing.
  final LiveTradingRepository? _live;
  final FuturesTradingRepository? _futures;

  @override
  Future<PortfolioOverview> getOverview(PortfolioMode mode) async {
    final json = await _remote.getOverview(mode.apiValue);
    return PortfolioAccountModel.overviewFromJson(json);
  }

  @override
  Future<PortfolioAccount> getAccount(
    PortfolioMode mode,
    PortfolioCategory category,
  ) async {
    final json = await _remote.getAccount(mode.apiValue, category.apiValue);
    return PortfolioAccountModel.accountFromJson(json);
  }

  @override
  Future<PortfolioPositions> getPositions(
    PortfolioMode mode,
    PortfolioCategory category,
  ) async {
    final json = await _remote.getPositions(mode.apiValue, category.apiValue);
    return PortfolioAccountModel.positionsFromJson(json);
  }

  @override
  Future<PortfolioHoldings> getHoldings(
    PortfolioMode mode,
    PortfolioCategory category,
  ) async {
    final json = await _remote.getHoldings(mode.apiValue, category.apiValue);
    return PortfolioAccountModel.holdingsFromJson(json);
  }

  @override
  Future<PortfolioHistory> getHistory({
    required PortfolioMode mode,
    required PortfolioCategory category,
    required PortfolioHistoryType type,
    String? symbol,
    DateTime? from,
    DateTime? to,
    int? limit,
    String? side,
    String? orderType,
    String? status,
    String? positionSide,
  }) async {
    final json = await _remote.getHistory(
      mode: mode.apiValue,
      category: category.apiValue,
      type: type.apiValue,
      symbol: symbol,
      from: from,
      to: to,
      limit: limit,
      side: side,
      orderType: orderType,
      status: status,
      positionSide: positionSide,
    );
    return PortfolioAccountModel.historyFromJson(json);
  }

  @override
  Future<PortfolioOrders> getOpenOrders({
    required PortfolioMode mode,
    required PortfolioCategory category,
    String? symbol,
  }) async {
    final json = await _remote.getOpenOrders(
      mode: mode.apiValue,
      category: category.apiValue,
      symbol: symbol,
    );
    return PortfolioAccountModel.ordersFromJson(json);
  }

  @override
  Future<PortfolioClosedPositions> getClosedPositions({
    required PortfolioMode mode,
    required PortfolioCategory category,
    String? symbol,
    DateTime? from,
    DateTime? to,
    int? limit,
  }) async {
    final json = await _remote.getClosedPositions(
      mode: mode.apiValue,
      category: category.apiValue,
      symbol: symbol,
      from: from,
      to: to,
      limit: limit,
    );
    return PortfolioAccountModel.closedPositionsFromJson(json);
  }

  @override
  Future<PortfolioHistory> getTransactionHistory({
    required PortfolioMode mode,
    required PortfolioCategory category,
    String? symbol,
    DateTime? from,
    DateTime? to,
    int? limit,
  }) async {
    final json = await _remote.getTransactionHistory(
      mode: mode.apiValue,
      category: category.apiValue,
      symbol: symbol,
      from: from,
      to: to,
      limit: limit,
    );
    return PortfolioAccountModel.historyFromJson(json);
  }

  @override
  Future<PortfolioHistory> getFundingFees({
    required PortfolioMode mode,
    required PortfolioCategory category,
    String? symbol,
    DateTime? from,
    DateTime? to,
    int? limit,
  }) async {
    final json = await _remote.getFundingFees(
      mode: mode.apiValue,
      category: category.apiValue,
      symbol: symbol,
      from: from,
      to: to,
      limit: limit,
    );
    return PortfolioAccountModel.historyFromJson(json);
  }

  @override
  Future<PortfolioSyncStatus> getSyncStatus(
    PortfolioMode mode,
    PortfolioCategory category,
  ) async {
    final json = await _remote.getSyncStatus(mode.apiValue, category.apiValue);
    return PortfolioAccountModel.syncStatusFromJson(json);
  }

  // ── Paper capital + position actions ─────────────────────────────

  @override
  Future<PaperCapitalEvent> addPaperCapital({
    required double amount,
    String? reason,
  }) async {
    final json = await _remote.addPaperCapital(amount: amount, reason: reason);
    return _capitalEvent(json);
  }

  @override
  Future<PaperCapitalEvent> reducePaperCapital({
    required double amount,
    String? reason,
  }) async {
    final json = await _remote.reducePaperCapital(
      amount: amount,
      reason: reason,
    );
    return _capitalEvent(json);
  }

  @override
  Future<List<PaperCapitalEvent>> getPaperCapitalHistory({
    int limit = 50,
  }) async {
    final rows = await _remote.getPaperCapitalHistory(limit: limit);
    return rows
        .whereType<Map<String, dynamic>>()
        .map(_capitalEvent)
        .toList(growable: false);
  }

  @override
  Future<void> resetPaperAccount() async {
    await _remote.resetPaperAccount();
  }

  @override
  Future<void> updatePaperPositionRisk(
    String positionId, {
    double? stopLoss,
    double? takeProfit,
  }) async {
    await _remote.updatePaperPositionRisk(
      positionId,
      stopLoss: stopLoss,
      takeProfit: takeProfit,
    );
  }

  @override
  Future<void> closePaperPosition(String positionId) async {
    await _remote.closePaperPosition(positionId);
  }

  @override
  Future<void> cancelOrder({
    required PortfolioMode mode,
    required PortfolioCategory category,
    required String cancelId,
  }) async {
    // PAPER works no order book. Refusing here as well as on the server keeps a
    // stale screen from reaching a mutation that could never succeed.
    if (mode != PortfolioMode.live) {
      throw StateError(
        'Order cancellation is only available on a live account',
      );
    }
    switch (category) {
      case PortfolioCategory.spot:
        final repo = _live;
        if (repo == null)
          throw StateError('Live spot trading is not configured');
        await repo.cancelOrder(cancelId);
      case PortfolioCategory.futures:
        final repo = _futures;
        if (repo == null) throw StateError('Futures trading is not configured');
        await repo.cancelOrder(cancelId);
      case PortfolioCategory.main:
      case PortfolioCategory.options:
        throw StateError('$category exposes no cancellable orders');
    }
  }

  /// The capital endpoints answer with the resulting account snapshot rather than
  /// the event itself, so the resulting balance is read from it; the audit row
  /// comes from the history endpoint. Both are only used for display, never to
  /// invent a figure.
  static PaperCapitalEvent _capitalEvent(Map<String, dynamic> json) {
    return PaperCapitalEvent(
      id: (json['id'] ?? '') as String,
      type: PaperCapitalEventType.parse(json['type'] as String?),
      amount: (json['amount'] as num?)?.toDouble() ?? 0,
      previousBalance: (json['previousBalance'] as num?)?.toDouble() ?? 0,
      newBalance: (json['newBalance'] as num?)?.toDouble() ?? 0,
      reason: json['reason'] as String?,
      createdAt: DateTime.tryParse((json['createdAt'] ?? '') as String),
    );
  }
}
