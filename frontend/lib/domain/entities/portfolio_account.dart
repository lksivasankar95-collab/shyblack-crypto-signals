/// Account mode of the unified Portfolio.
///
/// This is a Portfolio UI concern only. It is deliberately independent of the
/// legacy `User.accountType` and `User.tradingMode`, which are not portfolio
/// selector state.
enum PortfolioMode {
  paper('PAPER'),
  live('LIVE');

  const PortfolioMode(this.apiValue);

  /// Exact value expected by the backend `mode` query parameter.
  final String apiValue;

  /// Parses a backend value. An unrecognised value falls back to [paper] rather
  /// than throwing, so a malformed response can never crash the screen.
  static PortfolioMode parse(Object? raw) =>
      raw == live.apiValue ? live : paper;
}

/// Account category within an [PortfolioMode].
enum PortfolioCategory {
  main('MAIN'),
  spot('SPOT'),
  futures('FUTURES'),
  options('OPTIONS');

  const PortfolioCategory(this.apiValue);

  /// Exact value expected by the backend `{category}` path segment.
  final String apiValue;

  /// Parses a backend value, falling back to [main] for an unrecognised value.
  static PortfolioCategory parse(Object? raw) {
    for (final category in PortfolioCategory.values) {
      if (category.apiValue == raw) return category;
    }
    return PortfolioCategory.main;
  }

  /// Parses a backend value that may legitimately be absent. A position with no
  /// originating signal has no category and must not be assigned one.
  static PortfolioCategory? parseOptional(Object? raw) {
    for (final category in PortfolioCategory.values) {
      if (category.apiValue == raw) return category;
    }
    return null;
  }
}

/// Data availability for one account scope, mirroring the backend contract.
///
/// Every non-[available] value means the data must NOT be rendered as a number.
/// A null balance and an unavailable balance are different facts and are shown
/// differently.
enum PortfolioAvailability {
  available('AVAILABLE'),
  syncing('SYNCING'),
  stale('STALE'),
  notConnected('NOT_CONNECTED'),
  disconnected('DISCONNECTED'),
  unavailable('UNAVAILABLE'),

  /// The backend enum value is UNSUPPORTED; NOT_SUPPORTED is accepted as an
  /// alias so either spelling parses, but UNSUPPORTED is canonical.
  unsupported('UNSUPPORTED'),
  error('ERROR');

  const PortfolioAvailability(this.apiValue);

  final String apiValue;

  /// True when the scope reports real data. Anything else must be presented as
  /// an explicit state rather than as figures.
  bool get isAvailable => this == PortfolioAvailability.available;

  /// True when the platform has no such capability at all, which is different
  /// from an account that happens to be empty.
  bool get isUnsupported => this == PortfolioAvailability.unsupported;

  /// True when the underlying data could not be determined. Distinct from
  /// unsupported and from a genuinely empty account.
  bool get isUnavailable => this == PortfolioAvailability.unavailable;

  /// True when the values are known but not current, so they must be labelled.
  bool get isStale => this == PortfolioAvailability.stale;

  /// Short human label for the UI.
  String get label {
    switch (this) {
      case PortfolioAvailability.available:
        return 'LIVE';
      case PortfolioAvailability.syncing:
        return 'SYNCING';
      case PortfolioAvailability.stale:
        return 'STALE';
      case PortfolioAvailability.notConnected:
        return 'NOT CONNECTED';
      case PortfolioAvailability.disconnected:
        return 'DISCONNECTED';
      case PortfolioAvailability.unavailable:
        return 'UNAVAILABLE';
      case PortfolioAvailability.unsupported:
        return 'NOT SUPPORTED';
      case PortfolioAvailability.error:
        return 'ERROR';
    }
  }

  static PortfolioAvailability parse(String? raw) {
    if (raw == null) return PortfolioAvailability.unavailable;
    if (raw == 'NOT_SUPPORTED') return PortfolioAvailability.unsupported;
    for (final value in PortfolioAvailability.values) {
      if (value.apiValue == raw) return value;
    }
    return PortfolioAvailability.unavailable;
  }
}

/// One account scope as reported by the unified Portfolio API.
///
/// Every money and count field is nullable and MUST stay nullable all the way
/// to the widget layer. A null means "this source does not provide the value",
/// which is never the same as zero, so nothing here substitutes a default.
class PortfolioAccount {
  const PortfolioAccount({
    required this.accountMode,
    required this.accountCategory,
    required this.availability,
    this.exchange,
    this.connectionStatus,
    this.quoteCurrency,
    this.equity,
    this.availableBalance,
    this.invested,
    this.realizedPnl,
    this.unrealizedPnl,
    this.totalPositionCount,
    this.openPositionCount,
    this.orderCount,
    this.lastSyncedAt,
    this.statusMessage,
  });

  final PortfolioMode accountMode;
  final PortfolioCategory accountCategory;
  final PortfolioAvailability availability;
  final String? exchange;
  final String? connectionStatus;
  final String? quoteCurrency;
  final double? equity;
  final double? availableBalance;
  final double? invested;
  final double? realizedPnl;
  final double? unrealizedPnl;
  final int? totalPositionCount;
  final int? openPositionCount;
  final int? orderCount;
  final DateTime? lastSyncedAt;
  final String? statusMessage;

  /// True when the scope must be shown as an explicit state instead of figures.
  bool get isNotAvailable => !availability.isAvailable;
}

/// Overview of one [PortfolioMode] across all four categories.
class PortfolioOverview {
  const PortfolioOverview({
    required this.accountMode,
    required this.accounts,
  });

  final PortfolioMode accountMode;
  final List<PortfolioAccount> accounts;

  /// The account for one category, or null when the API omitted it.
  PortfolioAccount? accountFor(PortfolioCategory category) {
    for (final account in accounts) {
      if (account.accountCategory == category) return account;
    }
    return null;
  }
}

/// One position inside a single account scope.
class PortfolioPosition {
  const PortfolioPosition({
    required this.accountMode,
    this.accountCategory,
    required this.symbol,
    this.side,
    this.quantity,
    this.entryPrice,
    this.currentPrice,
    this.stopLoss,
    this.takeProfit1,
    this.takeProfit2,
    this.takeProfit3,
    this.liquidationPrice,
    this.leverage,
    this.notional,
    this.unrealizedPnl,
    this.realizedPnl,
    this.status,
  });

  final PortfolioMode accountMode;

  /// Null only for a simulated position with no originating signal, which
  /// cannot be attributed to a market category.
  final PortfolioCategory? accountCategory;
  final String symbol;
  final String? side;
  final double? quantity;
  final double? entryPrice;
  final double? currentPrice;
  final double? stopLoss;
  final double? takeProfit1;
  final double? takeProfit2;
  final double? takeProfit3;
  final double? liquidationPrice;
  final int? leverage;
  final double? notional;
  final double? unrealizedPnl;
  final double? realizedPnl;
  final String? status;
}

/// Positions for one account scope, with the availability that explains an
/// empty list.
class PortfolioPositions {
  const PortfolioPositions({
    required this.accountMode,
    required this.accountCategory,
    required this.availability,
    required this.positions,
    this.statusMessage,
  });

  final PortfolioMode accountMode;
  final PortfolioCategory accountCategory;
  final PortfolioAvailability availability;
  final List<PortfolioPosition> positions;
  final String? statusMessage;

  /// True when the list is empty because the scope is unsupported or
  /// unavailable, rather than because the account genuinely holds nothing.
  bool get isEmptyBecauseUnsupported => positions.isEmpty && !availability.isAvailable;
}

/// One asset held in an exchange wallet.
///
/// This is a wallet HOLDING, not a trading position. The spot API has no
/// open-position concept, so the distinction is carried in the type rather than
/// left to the widget layer.
///
/// There is deliberately no valuation field. Converting a balance into a quote
/// currency needs a trusted price feed, and an unpriced holding is far more
/// honest than a fabricated one.
class PortfolioHolding {
  const PortfolioHolding({
    required this.asset,
    this.free,
    this.locked,
    this.total,
  });

  final String asset;
  final double? free;
  final double? locked;
  final double? total;
}

/// Per-asset wallet holdings for one account scope.
///
/// Only LIVE spot has them. Every other scope reports [PortfolioAvailability]
/// .unsupported with an empty list, so an absent capability can never be
/// mistaken for an empty wallet.
class PortfolioHoldings {
  const PortfolioHoldings({
    required this.accountMode,
    required this.accountCategory,
    required this.availability,
    required this.holdings,
    this.source,
    this.statusMessage,
  });

  final PortfolioMode accountMode;
  final PortfolioCategory accountCategory;
  final PortfolioAvailability availability;

  /// EXCHANGE or LOCAL_PAPER, so the two sources are never conflated.
  final String? source;
  final List<PortfolioHolding> holdings;
  final String? statusMessage;

  /// True when there are no holdings because the scope has no such capability,
  /// rather than because the wallet is genuinely empty.
  bool get isEmptyBecauseUnsupported => holdings.isEmpty && !availability.isAvailable;
}

/// What kind of history record is being shown.
///
/// Mirrors the backend `type` parameter. [income] exists only for futures, where
/// the exchange publishes realized P&L as income records.
enum PortfolioHistoryType {
  order('ORDER'),
  trade('TRADE'),
  income('INCOME');

  const PortfolioHistoryType(this.apiValue);

  final String apiValue;

  static PortfolioHistoryType parse(Object? raw) {
    for (final value in PortfolioHistoryType.values) {
      if (value.apiValue == raw) return value;
    }
    return PortfolioHistoryType.order;
  }
}

/// One exchange-reported history record: an order, a fill or an income record.
///
/// Every record originates from an exchange endpoint. Nothing here is
/// reconstructed from a signal or a local order, so a record that the exchange
/// did not report is simply absent rather than approximated.
class PortfolioHistoryEntry {
  const PortfolioHistoryEntry({
    required this.entryType,
    required this.accountMode,
    required this.accountCategory,
    this.symbol,
    this.orderId,
    this.tradeId,
    this.side,
    this.status,
    this.price,
    this.quantity,
    this.quoteQuantity,
    this.fee,
    this.feeAsset,
    this.realizedPnl,
    this.occurredAt,
  });

  final String entryType;
  final PortfolioMode accountMode;
  final PortfolioCategory accountCategory;
  final String? symbol;
  final int? orderId;

  /// Natural identity of a fill. Null for an order or income record.
  final int? tradeId;
  final String? side;

  /// Order status for an order record, exchange income type for an income
  /// record, null for a fill.
  final String? status;
  final double? price;
  final double? quantity;
  final double? quoteQuantity;
  final double? fee;
  final String? feeAsset;

  /// Realized P&L exactly as the exchange reported it. Null when the exchange
  /// reported none; it is never derived from local state.
  final double? realizedPnl;
  final DateTime? occurredAt;
}

/// History for one account scope over an explicit window.
///
/// [windowFrom] and [windowTo] are always present so the user can see exactly
/// which period is covered. [complete] is false when the backend stopped at its
/// record cap, in which case the history is partial and must be labelled as
/// such instead of being presented as the full period.
class PortfolioHistory {
  const PortfolioHistory({
    required this.accountMode,
    required this.accountCategory,
    required this.availability,
    required this.entries,
    this.source,
    this.entryType,
    this.windowFrom,
    this.windowTo,
    this.complete = true,
    this.statusMessage,
  });

  final PortfolioMode accountMode;
  final PortfolioCategory accountCategory;
  final PortfolioAvailability availability;
  final String? source;
  final String? entryType;
  final DateTime? windowFrom;
  final DateTime? windowTo;
  final bool complete;
  final List<PortfolioHistoryEntry> entries;
  final String? statusMessage;

  /// True when the backend truncated the window at its record cap, so what is
  /// shown is not the whole period.
  bool get isPartial => !complete;

  bool get isEmptyBecauseUnsupported => entries.isEmpty && !availability.isAvailable;
}

/// Synchronization and reconciliation status for one account scope.
///
/// Exposes no credential, listen key or internal identifier, and carries the
/// reason a scope is not current so stale data can be labelled rather than
/// shown as fact.
class PortfolioSyncStatus {
  const PortfolioSyncStatus({
    required this.accountMode,
    required this.accountCategory,
    required this.availability,
    required this.stale,
    this.connectionStatus,
    this.lastRestSync,
    this.lastEvent,
    this.message,
  });

  final PortfolioMode accountMode;
  final PortfolioCategory accountCategory;
  final PortfolioAvailability availability;
  final String? connectionStatus;

  /// REST snapshot time, distinct from the user-stream event time.
  final DateTime? lastRestSync;

  /// Last applied user-data stream event time, null when none has arrived.
  final DateTime? lastEvent;

  /// True when the scope holds data that is past the freshness window.
  final bool stale;
  final String? message;

  bool get isStale => stale || availability.isStale;
}
