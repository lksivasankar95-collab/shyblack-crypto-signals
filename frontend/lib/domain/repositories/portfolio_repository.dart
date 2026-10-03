import '../entities/portfolio_account.dart';

/// Read-only contract for the unified Portfolio.
///
/// One flow covers every mode and category, so PAPER and LIVE can never be
/// served by separate, independently cached code paths.
///
/// The read methods are pure reads. The only mutating methods are the paper
/// capital and paper position actions at the bottom, which touch simulated
/// funds exclusively; nothing here can reach a live balance.
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

  // ── Paper capital management ───────────────────────────────────
  //
  // Simulated funds only. The backend rejects every one of these against a live
  // account, and the UI hides the control outside PAPER mode, so a real balance
  // is unreachable from here.

  /// Adds simulated capital and records an audited ADD event.
  Future<PaperCapitalEvent> addPaperCapital({
    required double amount,
    String? reason,
  });

  /// Withdraws simulated capital, bounded by free cash server side.
  Future<PaperCapitalEvent> reducePaperCapital({
    required double amount,
    String? reason,
  });

  /// The audited capital ledger, newest first.
  Future<List<PaperCapitalEvent>> getPaperCapitalHistory({int limit});

  /// Resets the simulated account to its starting balance.
  ///
  /// The server closes open positions and zeroes the counters; the ledger is
  /// preserved so the balance stays explainable afterwards.
  Future<void> resetPaperAccount();

  /// Repositions the stop-loss / take-profit of an open paper position.
  ///
  /// A null level means "leave unchanged"; protection can be moved but never
  /// cleared from the client.
  Future<void> updatePaperPositionRisk(
    String positionId, {
    double? stopLoss,
    double? takeProfit,
  });

  /// Closes an open paper position at the current market price.
  Future<void> closePaperPosition(String positionId);

  /// Cancels a resting LIVE order through the existing execution architecture.
  ///
  /// Routed per market rather than through a invented generic endpoint, because
  /// the backend deliberately separates spot and futures cancellation: each
  /// delegates to the scope's own service, which re-verifies ownership and
  /// rejects a non-terminal order.
  ///
  /// [cancelId] must be the order's published `cancelId` — the local record the
  /// backend actually addresses. PAPER is rejected outright: it works no order
  /// book, so there is nothing to cancel.
  Future<void> cancelOrder({
    required PortfolioMode mode,
    required PortfolioCategory category,
    required String cancelId,
  });
}

/// One audited capital movement on a paper account.
class PaperCapitalEvent {
  const PaperCapitalEvent({
    required this.id,
    required this.type,
    required this.amount,
    required this.previousBalance,
    required this.newBalance,
    this.reason,
    this.createdAt,
  });

  final String id;

  /// ADD, REDUCE, RESET or INITIAL.
  final PaperCapitalEventType type;

  /// Signed: ADD positive, REDUCE negative.
  final double amount;
  final double previousBalance;
  final double newBalance;
  final String? reason;
  final DateTime? createdAt;
}

enum PaperCapitalEventType {
  initial,
  add,
  reduce,
  reset,
  unknown;

  /// Never throws on an unrecognised value from the server: an unknown type is
  /// still shown, labelled, rather than crashing or being silently dropped.
  static PaperCapitalEventType parse(String? raw) {
    switch (raw?.toUpperCase()) {
      case 'INITIAL':
        return PaperCapitalEventType.initial;
      case 'ADD':
        return PaperCapitalEventType.add;
      case 'REDUCE':
        return PaperCapitalEventType.reduce;
      case 'RESET':
        return PaperCapitalEventType.reset;
      default:
        return PaperCapitalEventType.unknown;
    }
  }

  String get label {
    switch (this) {
      case PaperCapitalEventType.initial:
        return 'INITIAL';
      case PaperCapitalEventType.add:
        return 'ADD';
      case PaperCapitalEventType.reduce:
        return 'REDUCE';
      case PaperCapitalEventType.reset:
        return 'RESET';
      case PaperCapitalEventType.unknown:
        return 'UNKNOWN';
    }
  }
}
