/// Account mode of the unified Portfolio.
///
/// This mirrors the account mode the user selects in **Settings**, and is read from
/// `AppSettings.tradingAccount`. Portfolio has no mode selector of its own: the
/// mode here is a projection of Settings state, never independent UI state, so the
/// two cannot drift apart.
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
///
/// [main] is an internal read-model concept: the aggregate across every category of
/// one account mode. It is deliberately excluded from [portfolioTabs] so it never
/// appears as user-facing Portfolio navigation, where it would be confused with a
/// wallet or with a real market account.
enum PortfolioCategory {
  main('MAIN'),
  spot('SPOT'),
  futures('FUTURES'),
  options('OPTIONS');

  const PortfolioCategory(this.apiValue);

  /// Exact value expected by the backend `{category}` path segment.
  final String apiValue;

  /// The categories a user can actually browse.
  ///
  /// SPOT, FUTURES and OPTIONS, in that order. MAIN is absent on purpose: it is the
  /// backend's aggregate scope, not an account a user owns.
  static const List<PortfolioCategory> portfolioTabs = [spot, futures, options];

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

/// Exchange-confirmed order state, mirroring the backend contract.
///
/// Deliberately carries only states an exchange order-read can report. The local
/// submission states (SUBMITTED, SUBMITTING) live in the execution module and are
/// never surfaced here, because a submitted-but-unconfirmed order is not exchange
/// state and showing it here would let "submitted" be read as "filled".
///
/// [unknown] is the only safe fallback: a read that cannot determine the state stays
/// unknown and is never widened to [filled], closed or successful.
enum PortfolioOrderStatus {
  /// Accepted by the exchange and resting on the book, nothing filled yet.
  ///
  /// Named `open` rather than `new` because `new` is a reserved word in Dart.
  open('NEW'),
  partiallyFilled('PARTIALLY_FILLED'),
  filled('FILLED'),
  canceled('CANCELED'),
  rejected('REJECTED'),
  expired('EXPIRED'),
  unknown('UNKNOWN');

  const PortfolioOrderStatus(this.apiValue);

  final String apiValue;

  /// True only when the exchange said the order is fully filled.
  bool get isFilled => this == PortfolioOrderStatus.filled;

  /// True while the order is still live on the exchange book.
  bool get isOpen =>
      this == PortfolioOrderStatus.open || this == PortfolioOrderStatus.partiallyFilled;

  /// Maps a backend status name. Anything absent or unrecognised is [unknown].
  static PortfolioOrderStatus parse(Object? raw) {
    if (raw == null) return PortfolioOrderStatus.unknown;
    final value = raw.toString().trim().toUpperCase();
    for (final status in PortfolioOrderStatus.values) {
      if (status.apiValue == value) return status;
    }
    return PortfolioOrderStatus.unknown;
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
    this.positionSide,
    this.orderType,
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

  /// BOTH, LONG or SHORT for futures records. Null for spot, which has no position
  /// side, and for income records. Never derived from [side].
  final String? positionSide;

  /// LIMIT, MARKET, STOP_LOSS_LIMIT, ... as reported. Null for fills and income records.
  final String? orderType;
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

/// One order as the exchange currently reports it.
///
/// Every figure comes from an order-read endpoint. An order that has not filled has
/// a null [averageFillPrice] and a null [stopPrice] unless the exchange actually
/// reported one; nothing here defaults a missing value to zero.
class PortfolioOrder {
  const PortfolioOrder({
    required this.accountMode,
    required this.accountCategory,
    required this.symbol,
    required this.orderType,
    required this.status,
    this.side,
    this.positionSide,
    this.price,
    this.stopPrice,
    this.averageFillPrice,
    this.originalQuantity,
    this.executedQuantity,
    this.remainingQuantity,
    this.reduceOnly,
    this.orderId,
    this.clientOrderId,
    this.createdAt,
    this.updatedAt,
  });

  final PortfolioMode accountMode;
  final PortfolioCategory accountCategory;
  final String symbol;

  /// BUY or SELL as reported.
  final String? side;

  /// BOTH, LONG or SHORT for futures. Null for spot, which cannot be shorted.
  final String? positionSide;

  /// LIMIT, MARKET, STOP_LOSS_LIMIT, ... as reported.
  final String orderType;

  /// Normalised exchange state. Never widened to [PortfolioOrderStatus.filled].
  final PortfolioOrderStatus status;

  final double? price;

  /// Trigger price. Null when the order type has no trigger.
  final double? stopPrice;

  /// The exchange's own average fill price. Null when nothing has filled.
  final double? averageFillPrice;
  final double? originalQuantity;
  final double? executedQuantity;

  /// originalQuantity minus executedQuantity, or null when either is unknown.
  final double? remainingQuantity;

  /// Futures only.
  final bool? reduceOnly;
  final int? orderId;
  final String? clientOrderId;
  final DateTime? createdAt;
  final DateTime? updatedAt;
}

/// Open orders for one account scope, answering "what is resting right now".
///
/// A scope with no such capability reports [PortfolioAvailability].unsupported with
/// an empty list, so an absent capability is never shown as "no open orders".
class PortfolioOrders {
  const PortfolioOrders({
    required this.accountMode,
    required this.accountCategory,
    required this.availability,
    required this.orders,
    this.source,
    this.statusMessage,
  });

  final PortfolioMode accountMode;
  final PortfolioCategory accountCategory;
  final PortfolioAvailability availability;
  final String? source;
  final List<PortfolioOrder> orders;
  final String? statusMessage;

  bool get isEmptyBecauseUnsupported => orders.isEmpty && !availability.isAvailable;
}

/// One position that has been closed.
///
/// Provenance is strict: [realizedPnl] is summed from the values the exchange
/// attributed to the closing fills, never recomputed from prices. [entryPrice] and
/// [exitPrice] are quantity-weighted averages of the real fills on each side, and are
/// null when that side of the round trip was not observable in the requested window.
/// [funding] is always null, because a funding-fee record has no position attribution.
class PortfolioClosedPosition {
  const PortfolioClosedPosition({
    required this.accountMode,
    required this.accountCategory,
    required this.symbol,
    this.side,
    this.entryPrice,
    this.exitPrice,
    this.quantity,
    this.realizedPnl,
    this.fees,
    this.funding,
    this.feesAsset,
    this.marginType,
    this.leverage,
    this.openedAt,
    this.closedAt,
    this.duration,
    this.orderIds = const [],
    this.tradeIds = const [],
  });

  final PortfolioMode accountMode;
  final PortfolioCategory accountCategory;
  final String symbol;

  /// LONG or SHORT. Null only when the source reported no position side at all.
  final String? side;
  final double? entryPrice;
  final double? exitPrice;
  final double? quantity;
  final double? realizedPnl;
  final double? fees;
  final double? funding;
  final String? feesAsset;
  final String? marginType;
  final int? leverage;
  final DateTime? openedAt;
  final DateTime? closedAt;

  /// closedAt minus openedAt, or null when either is unknown.
  final Duration? duration;
  final List<int> orderIds;
  final List<int> tradeIds;
}

/// Closed positions for one account scope, answering "what trades were closed".
///
/// [partial] is true when at least one round trip could only partly be observed, so
/// some entry prices or durations are unavailable. The records are still real; this
/// is an honest completeness signal rather than an error.
class PortfolioClosedPositions {
  const PortfolioClosedPositions({
    required this.accountMode,
    required this.accountCategory,
    required this.availability,
    required this.positions,
    this.source,
    this.partial = false,
    this.statusMessage,
  });

  final PortfolioMode accountMode;
  final PortfolioCategory accountCategory;
  final PortfolioAvailability availability;
  final String? source;
  final bool partial;
  final List<PortfolioClosedPosition> positions;
  final String? statusMessage;

  bool get isEmptyBecauseUnsupported =>
      positions.isEmpty && !availability.isAvailable;
}
