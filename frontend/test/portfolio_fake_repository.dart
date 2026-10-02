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
  })  : accounts = accounts ?? <String, PortfolioAccount>{},
        positions = positions ?? <String, PortfolioPositions>{},
        holdings = holdings ?? <String, PortfolioHoldings>{},
        history = history ?? <String, PortfolioHistory>{},
        syncStatus = syncStatus ?? <String, PortfolioSyncStatus>{};

  /// Keyed by "MODE:CATEGORY".
  final Map<String, PortfolioAccount> accounts;
  final Map<String, PortfolioPositions> positions;
  final Map<String, PortfolioHoldings> holdings;
  final Map<String, PortfolioHistory> history;
  final Map<String, PortfolioSyncStatus> syncStatus;

  final List<String> accountCalls = [];
  final List<String> positionCalls = [];
  final List<String> holdingsCalls = [];
  final List<String> syncStatusCalls = [];

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
          availability: mode == PortfolioMode.live &&
                  category == PortfolioCategory.spot
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
  }) async {
    historyCalls.add('${key(mode, category)}:${type.apiValue}:${symbol ?? '-'}');
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
}
