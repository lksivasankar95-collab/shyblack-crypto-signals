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

  /// Per-asset wallet holdings. Only LIVE spot provides these; other scopes
  /// report their availability rather than an empty list.
  Future<PortfolioHoldings> getHoldings(
    PortfolioMode mode,
    PortfolioCategory category,
  );

  /// Exchange history for one scope over an explicit window.
  Future<PortfolioHistory> getHistory({
    required PortfolioMode mode,
    required PortfolioCategory category,
    required PortfolioHistoryType type,
    String? symbol,
    DateTime? from,
    DateTime? to,
    int? limit,
  });

  /// Connection state, freshness and the reason a scope is not current.
  Future<PortfolioSyncStatus> getSyncStatus(
    PortfolioMode mode,
    PortfolioCategory category,
  );
}
