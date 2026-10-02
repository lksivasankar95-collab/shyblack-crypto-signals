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
    String? side,
    String? orderType,
    String? status,
    String? positionSide,
  });

  /// Orders the exchange currently reports as resting for one scope.
  ///
  /// Read-only: nothing here places, modifies or cancels an order.
  Future<PortfolioOrders> getOpenOrders({
    required PortfolioMode mode,
    required PortfolioCategory category,
    String? symbol,
  });

  /// Positions closed within an explicit window.
  Future<PortfolioClosedPositions> getClosedPositions({
    required PortfolioMode mode,
    required PortfolioCategory category,
    String? symbol,
    DateTime? from,
    DateTime? to,
    int? limit,
  });

  /// Every account income record the exchange published in the window, with the
  /// exchange's own income type preserved.
  Future<PortfolioHistory> getTransactionHistory({
    required PortfolioMode mode,
    required PortfolioCategory category,
    String? symbol,
    DateTime? from,
    DateTime? to,
    int? limit,
  });

  /// Funding fees paid or received in the window.
  Future<PortfolioHistory> getFundingFees({
    required PortfolioMode mode,
    required PortfolioCategory category,
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
