import '../../domain/entities/portfolio.dart';
import '../../domain/repositories/portfolio_repository.dart';
import '../datasources/portfolio_remote_data_source.dart';

class PortfolioRepositoryImpl implements PortfolioRepository {
  PortfolioRepositoryImpl(this._remote);
  final PortfolioRemoteDataSource _remote;

  @override
  Future<List<Portfolio>> getPortfolios() async {
    final models = await _remote.getPortfolios();
    return models.map((model) => model.toEntity()).toList();
  }
}
