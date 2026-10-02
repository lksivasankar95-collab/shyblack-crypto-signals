import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/di/providers.dart';
import '../../domain/entities/portfolio_account.dart';

/// Selected Portfolio mode and category. This is Portfolio UI state only and is
/// deliberately independent of `User.accountType` and `User.tradingMode`, which
/// are not portfolio selector state.
class PortfolioScope {
  const PortfolioScope({required this.mode, required this.category});

  /// Matches the backend default, so the UI and the API agree before the first
  /// response arrives.
  const PortfolioScope.initial()
      : mode = PortfolioMode.paper,
        category = PortfolioCategory.main;

  final PortfolioMode mode;
  final PortfolioCategory category;

  PortfolioScope copyWith({PortfolioMode? mode, PortfolioCategory? category}) {
    return PortfolioScope(
      mode: mode ?? this.mode,
      category: category ?? this.category,
    );
  }

  @override
  bool operator ==(Object other) =>
      other is PortfolioScope &&
      other.mode == mode &&
      other.category == category;

  @override
  int get hashCode => Object.hash(mode, category);
}

class PortfolioSelection extends Notifier<PortfolioScope> {
  @override
  PortfolioScope build() => const PortfolioScope.initial();

  void selectMode(PortfolioMode mode) {
    state = state.copyWith(mode: mode);
  }

  void selectCategory(PortfolioCategory category) {
    state = state.copyWith(category: category);
  }
}

final portfolioSelectionProvider =
    NotifierProvider<PortfolioSelection, PortfolioScope>(PortfolioSelection.new);

/// Everything the Portfolio screen renders for one scope.
class PortfolioViewData {
  const PortfolioViewData({
    required this.scope,
    required this.account,
    required this.positions,
  });

  final PortfolioScope scope;
  final PortfolioAccount account;
  final PortfolioPositions positions;

  /// True when the payload belongs to the scope currently on screen. A response
  /// that does not match is never rendered, which is what prevents one mode's
  /// values appearing under another mode's label.
  bool get matchesScope =>
      account.accountMode == scope.mode &&
      account.accountCategory == scope.category;
}

/// Single unified data flow for the Portfolio.
///
/// One provider serves every mode and category, so PAPER and LIVE cannot drift
/// into separate caches. It watches [portfolioSelectionProvider], so changing the
/// mode or category rebuilds the request for that exact scope and the previous
/// scope's response is never shown underneath the new label.
///
/// There is no polling loop here. Refresh is explicit or pull-to-refresh, and
/// the provider is disposed when nothing watches it.
class PortfolioController extends AsyncNotifier<PortfolioViewData> {
  @override
  Future<PortfolioViewData> build() async {
    final scope = ref.watch(portfolioSelectionProvider);
    final repository = ref.watch(portfolioRepositoryProvider);

    // Both calls carry the same scope, so a SPOT request can never be served
    // futures data and vice versa.
    final results = await Future.wait<Object>([
      repository.getAccount(scope.mode, scope.category),
      repository.getPositions(scope.mode, scope.category),
    ]);

    return PortfolioViewData(
      scope: scope,
      account: results[0] as PortfolioAccount,
      positions: results[1] as PortfolioPositions,
    );
  }
}

final portfolioControllerProvider =
    AsyncNotifierProvider<PortfolioController, PortfolioViewData>(
  PortfolioController.new,
);

/// Wallet holdings for the selected scope.
///
/// Family-level rather than per-category, so it loads exactly once per mode
/// instead of once per category. The payload carries its own mode and category and
/// the screen discards a response that does not match the selection, so the cache
/// can never leak one scope's balances under another scope's label.
class PortfolioHoldingsController extends AsyncNotifier<PortfolioHoldings> {
  @override
  Future<PortfolioHoldings> build() async {
    final mode = ref.watch(
      portfolioSelectionProvider.select((scope) => scope.mode),
    );
    final repository = ref.watch(portfolioRepositoryProvider);
    return repository.getHoldings(mode, PortfolioCategory.spot);
  }
}

final portfolioHoldingsProvider =
    AsyncNotifierProvider<PortfolioHoldingsController, PortfolioHoldings>(
  PortfolioHoldingsController.new,
);

/// One history query as issued by the UI.
class PortfolioHistoryQuery {
  const PortfolioHistoryQuery({
    required this.type,
    this.symbol,
    this.limit,
  });

  final PortfolioHistoryType type;

  /// Required by the exchange for spot orders and fills, so the UI asks for it
  /// rather than silently receiving an error.
  final String? symbol;
  final int? limit;

  PortfolioHistoryQuery copyWith({
    PortfolioHistoryType? type,
    String? symbol,
    int? limit,
  }) {
    return PortfolioHistoryQuery(
      type: type ?? this.type,
      symbol: symbol ?? this.symbol,
      limit: limit ?? this.limit,
    );
  }

  @override
  bool operator ==(Object other) =>
      other is PortfolioHistoryQuery &&
      other.type == type &&
      other.symbol == symbol &&
      other.limit == limit;

  @override
  int get hashCode => Object.hash(type, symbol, limit);
}

/// The history window currently requested in the UI.
class PortfolioHistorySelection extends Notifier<PortfolioHistoryQuery> {
  @override
  PortfolioHistoryQuery build() =>
      const PortfolioHistoryQuery(type: PortfolioHistoryType.order);

  void selectType(PortfolioHistoryType type) {
    state = state.copyWith(type: type);
  }

  void setSymbol(String? symbol) {
    state = state.copyWith(symbol: symbol);
  }
}

final portfolioHistorySelectionProvider =
    NotifierProvider<PortfolioHistorySelection, PortfolioHistoryQuery>(
  PortfolioHistorySelection.new,
);

/// History for the selected scope and window.
///
/// Separate from [portfolioControllerProvider] on purpose: changing the window
/// or the record type must not re-request the account balance, and a history
/// failure must not blank out the balances the user can already see.
class PortfolioHistoryController
    extends AsyncNotifier<PortfolioHistory> {
  @override
  Future<PortfolioHistory> build() async {
    final scope = ref.watch(portfolioSelectionProvider);
    final query = ref.watch(portfolioHistorySelectionProvider);
    final repository = ref.watch(portfolioRepositoryProvider);

    return repository.getHistory(
      mode: scope.mode,
      category: scope.category,
      type: query.type,
      symbol: query.symbol,
      limit: query.limit,
    );
  }
}

final portfolioHistoryControllerProvider =
    AsyncNotifierProvider<PortfolioHistoryController, PortfolioHistory>(
  PortfolioHistoryController.new,
);

/// Connection state and freshness for the selected scope.
class PortfolioSyncStatusController
    extends AsyncNotifier<PortfolioSyncStatus> {
  @override
  Future<PortfolioSyncStatus> build() async {
    final scope = ref.watch(portfolioSelectionProvider);
    final repository = ref.watch(portfolioRepositoryProvider);
    return repository.getSyncStatus(scope.mode, scope.category);
  }
}

final portfolioSyncStatusControllerProvider =
    AsyncNotifierProvider<PortfolioSyncStatusController, PortfolioSyncStatus>(
  PortfolioSyncStatusController.new,
);
