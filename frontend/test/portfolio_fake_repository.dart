import 'package:cryptosignals/domain/entities/portfolio_account.dart';
import 'package:cryptosignals/domain/repositories/portfolio_repository.dart';

/// Deterministic in-memory Portfolio repository for widget and unit tests.
///
/// Records every call so a test can assert the exact mode and category that were
/// requested, and can return different payloads per scope so PAPER and LIVE can
/// never be confused with one another.
class FakePortfolioRepository implements PortfolioRepository {
  FakePortfolioRepository({
    Map<String, PortfolioAccount>? accounts,
    Map<String, PortfolioPositions>? positions,
    Map<String, PortfolioHoldings>? holdings,
    Map<String, PortfolioHistory>? history,
    Map<String, PortfolioSyncStatus>? syncStatus,
    Map<String, PortfolioOrders>? openOrders,
    Map<String, PortfolioClosedPositions>? closedPositions,
    Map<String, PortfolioHistory>? transactions,
    Map<String, PortfolioHistory>? fundingFees,
  }) : accounts = accounts ?? <String, PortfolioAccount>{},
       positions = positions ?? <String, PortfolioPositions>{},
       holdings = holdings ?? <String, PortfolioHoldings>{},
       history = history ?? <String, PortfolioHistory>{},
       syncStatus = syncStatus ?? <String, PortfolioSyncStatus>{},
       openOrders = openOrders ?? <String, PortfolioOrders>{},
       closedPositions =
           closedPositions ?? <String, PortfolioClosedPositions>{},
       transactions = transactions ?? <String, PortfolioHistory>{},
       fundingFees = fundingFees ?? <String, PortfolioHistory>{};

  /// Keyed by "MODE:CATEGORY".
  final Map<String, PortfolioAccount> accounts;
  final Map<String, PortfolioPositions> positions;
  final Map<String, PortfolioHoldings> holdings;
  final Map<String, PortfolioHistory> history;
  final Map<String, PortfolioSyncStatus> syncStatus;
  final Map<String, PortfolioOrders> openOrders;
  final Map<String, PortfolioClosedPositions> closedPositions;
  final Map<String, PortfolioHistory> transactions;
  final Map<String, PortfolioHistory> fundingFees;

  final List<String> accountCalls = [];
  final List<String> positionCalls = [];
  final List<String> holdingsCalls = [];
  final List<String> syncStatusCalls = [];
  final List<String> openOrderCalls = [];
  final List<String> closedPositionCalls = [];
  final List<String> transactionCalls = [];
  final List<String> fundingCalls = [];

  /// Recorded as "MODE:CATEGORY:TYPE:SYMBOL" so a test can assert the exact
  /// window and symbol that were requested.
  final List<String> historyCalls = [];

  /// When set, the next call throws this instead of returning data.
  Object? failWith;

  /// When set, only [getHistory] throws. Scoped separately so a test can prove
  /// that a history failure leaves the account figures intact, without also
  /// breaking the holdings and sync-status reads on the same screen.
  Object? historyFailWith;

  static String key(PortfolioMode mode, PortfolioCategory category) =>
      '${mode.apiValue}:${category.apiValue}';

  @override
  Future<PortfolioAccount> getAccount(
    PortfolioMode mode,
    PortfolioCategory category,
  ) async {
    accountCalls.add(key(mode, category));
    if (failWith != null) throw failWith!;
    return accounts[key(mode, category)] ??
        PortfolioAccount(
          accountMode: mode,
          accountCategory: category,
          availability: PortfolioAvailability.available,
        );
  }

  @override
  Future<PortfolioPositions> getPositions(
    PortfolioMode mode,
    PortfolioCategory category,
  ) async {
    positionCalls.add(key(mode, category));
    if (failWith != null) throw failWith!;
    return positions[key(mode, category)] ??
        PortfolioPositions(
          accountMode: mode,
          accountCategory: category,
          availability: PortfolioAvailability.available,
          positions: const [],
        );
  }

  @override
  Future<PortfolioOverview> getOverview(PortfolioMode mode) async {
    final list = <PortfolioAccount>[];
    for (final category in PortfolioCategory.values) {
      list.add(await getAccount(mode, category));
    }
    return PortfolioOverview(accountMode: mode, accounts: list);
  }

  @override
  Future<PortfolioHoldings> getHoldings(
    PortfolioMode mode,
    PortfolioCategory category,
  ) async {
    holdingsCalls.add(key(mode, category));
    if (failWith != null) throw failWith!;
    return holdings[key(mode, category)] ??
        // Mirrors the backend contract: only a live spot wallet exposes holdings,
        // so a paper or futures scope is unsupported rather than empty. Returning
        // AVAILABLE here would let a test pass against a payload the real API
        // never sends.
        PortfolioHoldings(
          accountMode: mode,
          accountCategory: category,
          availability:
              mode == PortfolioMode.live && category == PortfolioCategory.spot
              ? PortfolioAvailability.available
              : PortfolioAvailability.unsupported,
          holdings: const [],
          statusMessage: mode == PortfolioMode.paper
              ? 'A simulated account holds capital, not exchange assets, so it has no '
                    'per-asset wallet holdings.'
              : null,
        );
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
    historyCalls.add(
      '${key(mode, category)}:${type.apiValue}:${symbol ?? '-'}',
    );
    if (historyFailWith != null) throw historyFailWith!;
    if (failWith != null) throw failWith!;
    return history[key(mode, category)] ??
        PortfolioHistory(
          accountMode: mode,
          accountCategory: category,
          availability: PortfolioAvailability.available,
          entries: const [],
        );
  }

  @override
  Future<PortfolioOrders> getOpenOrders({
    required PortfolioMode mode,
    required PortfolioCategory category,
    String? symbol,
  }) async {
    openOrderCalls.add(key(mode, category));
    if (failWith != null) throw failWith!;
    return openOrders[key(mode, category)] ??
        // A paper account works no order book, so the backend reports it as
        // unsupported rather than as an empty book.
        PortfolioOrders(
          accountMode: mode,
          accountCategory: category,
          availability: mode == PortfolioMode.paper
              ? PortfolioAvailability.unsupported
              : PortfolioAvailability.available,
          orders: const [],
          statusMessage: mode == PortfolioMode.paper
              ? 'A simulated account works no order book.'
              : null,
        );
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
    closedPositionCalls.add(key(mode, category));
    if (failWith != null) throw failWith!;
    return closedPositions[key(mode, category)] ??
        // Live spot has no leveraged position lifecycle to close, so the backend
        // reports it as unsupported rather than as "nothing was ever closed".
        PortfolioClosedPositions(
          accountMode: mode,
          accountCategory: category,
          availability:
              mode == PortfolioMode.live && category == PortfolioCategory.spot
              ? PortfolioAvailability.unsupported
              : PortfolioAvailability.available,
          positions: const [],
          statusMessage:
              mode == PortfolioMode.live && category == PortfolioCategory.spot
              ? 'Spot holds wallet assets rather than leveraged positions.'
              : null,
        );
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
    transactionCalls.add(key(mode, category));
    if (failWith != null) throw failWith!;
    return transactions[key(mode, category)] ??
        // Mirrors the backend contract: Binance Spot publishes no account income
        // endpoint at all, so the spot scope is reported as unsupported rather than as
        // an empty ledger that would read as "no transactions happened".
        PortfolioHistory(
          accountMode: mode,
          accountCategory: category,
          availability:
              mode == PortfolioMode.live && category == PortfolioCategory.spot
              ? PortfolioAvailability.unsupported
              : PortfolioAvailability.available,
          entries: const [],
          statusMessage:
              mode == PortfolioMode.live && category == PortfolioCategory.spot
              ? 'Not available: Binance Spot publishes no account income or transaction '
                    'endpoint.'
              : null,
        );
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
    fundingCalls.add(key(mode, category));
    if (failWith != null) throw failWith!;
    return fundingFees[key(mode, category)] ??
        PortfolioHistory(
          accountMode: mode,
          accountCategory: category,
          availability: PortfolioAvailability.available,
          entries: const [],
        );
  }

  @override
  Future<PortfolioSyncStatus> getSyncStatus(
    PortfolioMode mode,
    PortfolioCategory category,
  ) async {
    syncStatusCalls.add(key(mode, category));
    if (failWith != null) throw failWith!;
    return syncStatus[key(mode, category)] ??
        PortfolioSyncStatus(
          accountMode: mode,
          accountCategory: category,
          availability: PortfolioAvailability.available,
          stale: false,
        );
  }

  // ── Paper capital + position actions ─────────────────────────────

  /// Seeded capital ledger returned by [getPaperCapitalHistory].
  List<PaperCapitalEvent> capitalHistoryRows = const [];

  /// Recorded so a test can prove a simulated-funds action never fires outside
  /// PAPER, and that the exact amount/reason reached the repository.
  final List<String> capitalCalls = [];
  final List<String> positionRiskCalls = [];
  final List<String> positionCloseCalls = [];

  /// When set, every paper mutation throws this, standing in for the server
  /// rejecting the request (e.g. withdrawing more than free cash).
  Object? paperFailWith;

  @override
  Future<PaperCapitalEvent> addPaperCapital({
    required double amount,
    String? reason,
  }) async {
    capitalCalls.add('ADD:$amount:${reason ?? ''}');
    if (paperFailWith != null) throw paperFailWith!;
    return PaperCapitalEvent(
      id: 'evt-${capitalCalls.length}',
      type: PaperCapitalEventType.add,
      amount: amount,
      previousBalance: 0,
      newBalance: amount,
      reason: reason,
    );
  }

  @override
  Future<PaperCapitalEvent> reducePaperCapital({
    required double amount,
    String? reason,
  }) async {
    capitalCalls.add('REDUCE:$amount:${reason ?? ''}');
    if (paperFailWith != null) throw paperFailWith!;
    return PaperCapitalEvent(
      id: 'evt-${capitalCalls.length}',
      type: PaperCapitalEventType.reduce,
      amount: -amount,
      previousBalance: amount,
      newBalance: 0,
      reason: reason,
    );
  }

  @override
  Future<List<PaperCapitalEvent>> getPaperCapitalHistory({
    int limit = 50,
  }) async {
    capitalCalls.add('HISTORY');
    if (paperFailWith != null) throw paperFailWith!;
    return capitalHistoryRows;
  }

  @override
  Future<void> resetPaperAccount() async {
    capitalCalls.add('RESET');
    if (paperFailWith != null) throw paperFailWith!;
  }

  @override
  Future<void> updatePaperPositionRisk(
    String positionId, {
    double? stopLoss,
    double? takeProfit,
  }) async {
    positionRiskCalls.add('$positionId:$stopLoss:$takeProfit');
    if (paperFailWith != null) throw paperFailWith!;
  }

  @override
  Future<void> closePaperPosition(String positionId) async {
    positionCloseCalls.add(positionId);
    if (paperFailWith != null) throw paperFailWith!;
  }

  /// Recorded as "MODE:CATEGORY:id" so a test can prove a cancellation was routed
  /// to the correct scope and addressed the correct record.
  final List<String> cancelOrderCalls = [];

  @override
  Future<void> cancelOrder({
    required PortfolioMode mode,
    required PortfolioCategory category,
    required String cancelId,
  }) async {
    cancelOrderCalls.add('${mode.apiValue}:${category.apiValue}:$cancelId');
    if (paperFailWith != null) throw paperFailWith!;
  }
}
