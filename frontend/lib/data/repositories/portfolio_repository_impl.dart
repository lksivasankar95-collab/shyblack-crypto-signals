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
}
