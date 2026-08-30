import '../entities/portfolio.dart';
import '../repositories/portfolio_repository.dart';

class GetPortfolios {
  const GetPortfolios(this._repository);
  final PortfolioRepository _repository;

  Future<List<Portfolio>> call() => _repository.getPortfolios();
}
