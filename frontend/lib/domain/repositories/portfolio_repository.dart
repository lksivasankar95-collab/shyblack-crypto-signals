import '../entities/portfolio_account.dart';

/// Read-only contract for the unified Portfolio.
///
/// One flow covers every mode and category, so PAPER and LIVE can never be
/// served by separate, independently cached code paths.
abstract class PortfolioRepository {
  Future<PortfolioOverview> getOverview(PortfolioMode mode);

  Future<PortfolioAccount> getAccount(
    PortfolioMode mode,
    PortfolioCategory category,
  );

  Future<PortfolioPositions> getPositions(
    PortfolioMode mode,
    PortfolioCategory category,
  );
}
