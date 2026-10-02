import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/di/providers.dart';
import '../../domain/entities/app_settings.dart';
import '../../domain/entities/portfolio_account.dart';
import 'settings_controller.dart';

/// Thrown when Settings has not yet resolved which account mode is selected.
///
/// The Portfolio issues **no** request in that state. That is deliberate: defaulting to
/// PAPER while the user has actually chosen LIVE would render a simulated account as if
/// it were their exchange account, which is precisely the confusion this redesign removes.
/// The screen shows an explicit "account mode unavailable" state instead.
class PortfolioAccountModeUnresolved implements Exception {
  const PortfolioAccountModeUnresolved();

  @override
  String toString() => 'PortfolioAccountModeUnresolved';
}

/// The account mode, read from Settings.
///
/// This is a pure projection of `AppSettings.tradingAccount`. There is no Portfolio
/// control that writes it, so the Portfolio cannot offer a second, divergent mode of its
/// own: changing the account mode in Settings changes the Portfolio, and nothing else can.
///
/// Null while Settings is still loading or has failed. It is never defaulted.
final portfolioAccountModeProvider = Provider<PortfolioMode?>((ref) {
  final settings = ref.watch(settingsControllerProvider).asData?.value;
  if (settings == null) return null;
  return settings.tradingAccount == TradingAccount.live
      ? PortfolioMode.live
      : PortfolioMode.paper;
});

/// Which market account the user is looking at: SPOT, FUTURES or OPTIONS.
///
/// This is the only Portfolio selection state that exists. MAIN is intentionally not
/// offered: it is the backend's aggregate scope, not an account a user owns.
class PortfolioCategorySelection extends Notifier<PortfolioCategory> {
  @override
  PortfolioCategory build() => PortfolioCategory.spot;

  void select(PortfolioCategory category) {
    state = category;
  }
}

final portfolioCategoryProvider =
    NotifierProvider<PortfolioCategorySelection, PortfolioCategory>(
      PortfolioCategorySelection.new,
    );

/// The account mode and market account currently on screen.
///
/// Null exactly when the account mode is unresolved, which is what stops the screen from
/// rendering any figure against an unknown mode.
class PortfolioScope {
  const PortfolioScope({required this.mode, required this.category});

  final PortfolioMode mode;
  final PortfolioCategory category;

  @override
  bool operator ==(Object other) =>
      other is PortfolioScope &&
      other.mode == mode &&
      other.category == category;

  @override
  int get hashCode => Object.hash(mode, category);
}

final portfolioScopeProvider = Provider<PortfolioScope?>((ref) {
  final mode = ref.watch(portfolioAccountModeProvider);
  final category = ref.watch(portfolioCategoryProvider);
  if (mode == null) return null;
  return PortfolioScope(mode: mode, category: category);
});

/// Resolves the scope for a data request, or refuses to make one.
///
/// Every portfolio read goes through here, so no provider can accidentally issue a
/// request against a guessed mode.
PortfolioScope requireScope(Ref ref) {
  final scope = ref.watch(portfolioScopeProvider);
  if (scope == null) {
    throw const PortfolioAccountModeUnresolved();
  }
  return scope;
}

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
/// into separate caches. It watches [portfolioScopeProvider], so changing the account
/// mode in Settings or the category tab rebuilds the request for that exact scope and
/// the previous scope's response is never shown underneath the new label.
///
/// There is no polling loop here. Refresh is explicit or pull-to-refresh, and
/// the provider is disposed when nothing watches it.
class PortfolioController extends AsyncNotifier<PortfolioViewData> {
  @override
  Future<PortfolioViewData> build() async {
    // Refuses to request anything while the account mode is unknown.
    final scope = requireScope(ref);
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

/// Wallet holdings for the selected mode's spot wallet.
///
/// Holdings are a property of the spot wallet, not of the selected category, so a
/// FUTURES selection still shows the spot wallet's assets rather than a response that
/// silently disappears. The mode is still matched strictly, so one account mode's
/// balances can never appear under the other's label.
class PortfolioHoldingsController extends AsyncNotifier<PortfolioHoldings> {
  @override
  Future<PortfolioHoldings> build() async {
    final scope = requireScope(ref);
    final repository = ref.watch(portfolioRepositoryProvider);
    return repository.getHoldings(scope.mode, PortfolioCategory.spot);
  }
}

final portfolioHoldingsProvider =
    AsyncNotifierProvider<PortfolioHoldingsController, PortfolioHoldings>(
  PortfolioHoldingsController.new,
    );

/// Orders the exchange currently reports as resting for the selected scope.
///
/// Read-only by construction: this provider has no place to place, modify or cancel an
/// order.
class PortfolioOpenOrdersController extends AsyncNotifier<PortfolioOrders> {
  @override
  Future<PortfolioOrders> build() async {
    final scope = requireScope(ref);
    final repository = ref.watch(portfolioRepositoryProvider);
    return repository.getOpenOrders(mode: scope.mode, category: scope.category);
  }
}

final portfolioOpenOrdersProvider =
    AsyncNotifierProvider<PortfolioOpenOrdersController, PortfolioOrders>(
      PortfolioOpenOrdersController.new,
    );

/// Positions closed inside the selected window, for the selected scope.
class PortfolioClosedPositionsController
    extends AsyncNotifier<PortfolioClosedPositions> {
  @override
  Future<PortfolioClosedPositions> build() async {
    final scope = requireScope(ref);
    final window = ref.watch(portfolioHistorySelectionProvider);
    final repository = ref.watch(portfolioRepositoryProvider);
    return repository.getClosedPositions(
      mode: scope.mode,
      category: scope.category,
      symbol: window.symbol,
      from: window.from,
      to: window.to,
      limit: window.limit,
    );
  }
}

final portfolioClosedPositionsProvider =
    AsyncNotifierProvider<PortfolioClosedPositionsController,
        PortfolioClosedPositions>(PortfolioClosedPositionsController.new);

/// One history query as issued by the UI.
class PortfolioHistoryQuery {
  const PortfolioHistoryQuery({
    required this.type,
    this.symbol,
    this.limit,
    this.from,
    this.to,
    this.side,
    this.orderType,
    this.status,
    this.positionSide,
  });

  final PortfolioHistoryType type;

  /// Required by the exchange for spot orders and fills, so the UI asks for it
  /// rather than silently receiving an error.
  final String? symbol;

  final int? limit;

  /// Inclusive window start. Null lets the backend apply its own bounded default,
  /// which is always explicit in the response.
  final DateTime? from;
  final DateTime? to;

  /// Optional narrowing. Null means no narrowing at all.
  final String? side;
  final String? orderType;
  final String? status;
  final String? positionSide;

  PortfolioHistoryQuery copyWith({
    PortfolioHistoryType? type,
    String? symbol,
    int? limit,
    DateTime? from,
    DateTime? to,
    String? side,
    String? orderType,
    String? status,
    String? positionSide,
    bool clearSymbol = false,
    bool clearFrom = false,
    bool clearTo = false,
  }) {
    return PortfolioHistoryQuery(
      type: type ?? this.type,
      symbol: clearSymbol ? null : (symbol ?? this.symbol),
      limit: limit ?? this.limit,
      from: clearFrom ? null : (from ?? this.from),
      to: clearTo ? null : (to ?? this.to),
      side: side ?? this.side,
      orderType: orderType ?? this.orderType,
      status: status ?? this.status,
      positionSide: positionSide ?? this.positionSide,
    );
  }

  @override
  bool operator ==(Object other) =>
      other is PortfolioHistoryQuery &&
      other.type == type &&
      other.symbol == symbol &&
      other.limit == limit &&
      other.from == from &&
      other.to == to &&
      other.side == side &&
      other.orderType == orderType &&
      other.status == status &&
      other.positionSide == positionSide;

  @override
  int get hashCode => Object.hash(
        type,
        symbol,
        limit,
        from,
        to,
        side,
        orderType,
        status,
        positionSide,
      );
}

/// The history window and narrowing currently requested in the UI.
class PortfolioHistorySelection extends Notifier<PortfolioHistoryQuery> {
  @override
  PortfolioHistoryQuery build() =>
      const PortfolioHistoryQuery(type: PortfolioHistoryType.order);

  void selectType(PortfolioHistoryType type) {
    state = state.copyWith(type: type);
  }

  void setSymbol(String? symbol) {
    state = symbol == null
        ? state.copyWith(clearSymbol: true)
        : state.copyWith(symbol: symbol);
  }

  void setSide(String? side) => state = state.copyWith(side: side);

  void setOrderType(String? orderType) =>
      state = state.copyWith(orderType: orderType);

  void setStatus(String? status) => state = state.copyWith(status: status);

  void setPositionSide(String? positionSide) =>
      state = state.copyWith(positionSide: positionSide);

  /// Applies a relative window. Only bounded windows are ever requested; an
  /// unbounded "everything" request is not issued.
  void setRange(PortfolioHistoryRange range) {
    final now = DateTime.now().toUtc();
    switch (range) {
      case PortfolioHistoryRange.today:
        state = state.copyWith(
          from: DateTime.utc(now.year, now.month, now.day),
          to: now,
          clearTo: false,
        );
      case PortfolioHistoryRange.sevenDays:
        state = state.copyWith(
          from: now.subtract(const Duration(days: 7)),
          to: now,
        );
      case PortfolioHistoryRange.thirtyDays:
        state = state.copyWith(
          from: now.subtract(const Duration(days: 30)),
          to: now,
        );
    }
  }
}

/// The bounded windows offered for history, matching the backend's own bounds.
enum PortfolioHistoryRange {
  today('Today'),
  sevenDays('7 Days'),
  thirtyDays('30 Days');

  const PortfolioHistoryRange(this.label);

  final String label;
}

final portfolioHistorySelectionProvider =
    NotifierProvider<PortfolioHistorySelection, PortfolioHistoryQuery>(
  PortfolioHistorySelection.new,
);

/// History for the selected scope and window.
class PortfolioHistoryController
    extends AsyncNotifier<PortfolioHistory> {
  @override
  Future<PortfolioHistory> build() async {
    final scope = requireScope(ref);
    final query = ref.watch(portfolioHistorySelectionProvider);
    final repository = ref.watch(portfolioRepositoryProvider);

    return repository.getHistory(
      mode: scope.mode,
      category: scope.category,
      type: query.type,
      symbol: query.symbol,
      from: query.from,
      to: query.to,
      limit: query.limit,
      side: query.side,
      orderType: query.orderType,
      status: query.status,
      positionSide: query.positionSide,
    );
  }
}

final portfolioHistoryControllerProvider =
    AsyncNotifierProvider<PortfolioHistoryController, PortfolioHistory>(
  PortfolioHistoryController.new,
);

/// Account income records for the selected scope and window.
class PortfolioTransactionController
    extends AsyncNotifier<PortfolioHistory> {
  @override
  Future<PortfolioHistory> build() async {
    final scope = requireScope(ref);
    final query = ref.watch(portfolioHistorySelectionProvider);
    final repository = ref.watch(portfolioRepositoryProvider);

    return repository.getTransactionHistory(
      mode: scope.mode,
      category: scope.category,
      symbol: query.symbol,
      from: query.from,
      to: query.to,
      limit: query.limit,
    );
  }
}

final portfolioTransactionControllerProvider =
    AsyncNotifierProvider<PortfolioTransactionController, PortfolioHistory>(
  PortfolioTransactionController.new,
);

/// Funding fees for the selected scope and window.
class PortfolioFundingController extends AsyncNotifier<PortfolioHistory> {
  @override
  Future<PortfolioHistory> build() async {
    final scope = requireScope(ref);
    final query = ref.watch(portfolioHistorySelectionProvider);
    final repository = ref.watch(portfolioRepositoryProvider);

    return repository.getFundingFees(
      mode: scope.mode,
      category: scope.category,
      symbol: query.symbol,
      from: query.from,
      to: query.to,
      limit: query.limit,
    );
  }
}

final portfolioFundingControllerProvider =
    AsyncNotifierProvider<PortfolioFundingController, PortfolioHistory>(
  PortfolioFundingController.new,
);

/// Connection state and freshness for the selected scope.
class PortfolioSyncStatusController
    extends AsyncNotifier<PortfolioSyncStatus> {
  @override
  Future<PortfolioSyncStatus> build() async {
    final scope = requireScope(ref);
    final repository = ref.watch(portfolioRepositoryProvider);
    return repository.getSyncStatus(scope.mode, scope.category);
  }
}

final portfolioSyncStatusControllerProvider =
    AsyncNotifierProvider<PortfolioSyncStatusController, PortfolioSyncStatus>(
  PortfolioSyncStatusController.new,
);
