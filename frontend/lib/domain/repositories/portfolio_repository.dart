import '../entities/portfolio.dart';

abstract class PortfolioRepository {
  Future<List<Portfolio>> getPortfolios();
}
