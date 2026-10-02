import '../../data/datasources/portfolio_remote_data_source.dart';
import '../../data/models/portfolio_account_model.dart';
import '../../domain/entities/portfolio_account.dart';
import '../../domain/repositories/portfolio_repository.dart';

class PortfolioRepositoryImpl implements PortfolioRepository {
  PortfolioRepositoryImpl(this._remote);

  final PortfolioRemoteDataSource _remote;

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
}
