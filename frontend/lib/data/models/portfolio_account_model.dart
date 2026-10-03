import '../../domain/entities/portfolio_account.dart';

/// Parses the unified Portfolio API payloads.
///
/// The only rule that matters here: a missing numeric field becomes null, never
/// zero. A zero is only ever produced when the backend actually sent a zero.
class PortfolioAccountModel {
  const PortfolioAccountModel._();

  static PortfolioOverview overviewFromJson(Map<String, dynamic> json) {
    final rawAccounts = json['accounts'];
    final accounts = <PortfolioAccount>[];
    if (rawAccounts is List) {
      for (final entry in rawAccounts) {
        if (entry is Map<String, dynamic>) {
          accounts.add(accountFromJson(entry));
        }
      }
    }
    return PortfolioOverview(
      accountMode: parseMode(json['accountMode']),
      accounts: accounts,
    );
  }

  static PortfolioAccount accountFromJson(Map<String, dynamic> json) {
    return PortfolioAccount(
      accountMode: parseMode(json['accountMode']),
      accountCategory: parseCategory(json['accountCategory']),
      availability: PortfolioAvailability.parse(
        json['availability'] as String?,
      ),
      exchange: json['exchange'] as String?,
      connectionStatus: json['connectionStatus'] as String?,
      quoteCurrency: json['quoteCurrency'] as String?,
      equity: _num(json['equity']),
      availableBalance: _num(json['availableBalance']),
      invested: _num(json['invested']),
      realizedPnl: _num(json['realizedPnl']),
      unrealizedPnl: _num(json['unrealizedPnl']),
      totalPositionCount: _int(json['totalPositionCount']),
      openPositionCount: _int(json['openPositionCount']),
      orderCount: _int(json['orderCount']),
      lastSyncedAt: _date(json['lastSyncedAt']),
      statusMessage: json['statusMessage'] as String?,
    );
  }

  static PortfolioPositions positionsFromJson(Map<String, dynamic> json) {
    final raw = json['positions'];
    final positions = <PortfolioPosition>[];
    if (raw is List) {
      for (final entry in raw) {
        if (entry is Map<String, dynamic>) {
          positions.add(positionFromJson(entry));
        }
      }
    }
    return PortfolioPositions(
      accountMode: parseMode(json['accountMode']),
      accountCategory: parseCategory(json['accountCategory']),
      availability: PortfolioAvailability.parse(
        json['availability'] as String?,
      ),
      positions: positions,
      statusMessage: json['statusMessage'] as String?,
    );
  }

  static PortfolioPosition positionFromJson(Map<String, dynamic> json) {
    return PortfolioPosition(
      positionId: json['positionId'] as String?,
      accountMode: parseMode(json['accountMode']),
      accountCategory: _optionalCategory(json['accountCategory']),
      symbol: json['symbol'] as String? ?? '',
      side: json['side'] as String?,
      quantity: _num(json['quantity']),
      entryPrice: _num(json['entryPrice']),
      currentPrice: _num(json['currentPrice']),
      stopLoss: _num(json['stopLoss']),
      takeProfit1: _num(json['takeProfit1']),
      takeProfit2: _num(json['takeProfit2']),
      takeProfit3: _num(json['takeProfit3']),
      liquidationPrice: _num(json['liquidationPrice']),
      leverage: _int(json['leverage']),
      notional: _num(json['notional']),
      unrealizedPnl: _num(json['unrealizedPnl']),
      realizedPnl: _num(json['realizedPnl']),
      status: json['status'] as String?,
    );
  }

  static PortfolioHoldings holdingsFromJson(Map<String, dynamic> json) {
    final raw = json['holdings'];
    final holdings = <PortfolioHolding>[];
    if (raw is List) {
      for (final entry in raw) {
        if (entry is Map<String, dynamic>) {
          holdings.add(
            PortfolioHolding(
              asset: entry['asset'] as String? ?? '',
              free: _num(entry['free']),
              locked: _num(entry['locked']),
              total: _num(entry['total']),
            ),
          );
        }
      }
    }
    return PortfolioHoldings(
      accountMode: parseMode(json['accountMode']),
      accountCategory: parseCategory(json['accountCategory']),
      availability: PortfolioAvailability.parse(
        json['availability'] as String?,
      ),
      source: json['source'] as String?,
      holdings: holdings,
      statusMessage: json['statusMessage'] as String?,
    );
  }

  static PortfolioHistory historyFromJson(Map<String, dynamic> json) {
    final raw = json['entries'];
    final entries = <PortfolioHistoryEntry>[];
    if (raw is List) {
      for (final entry in raw) {
        if (entry is Map<String, dynamic>) {
          entries.add(historyEntryFromJson(entry));
        }
      }
    }
    return PortfolioHistory(
      accountMode: parseMode(json['accountMode']),
      accountCategory: parseCategory(json['accountCategory']),
      availability: PortfolioAvailability.parse(
        json['availability'] as String?,
      ),
      source: json['source'] as String?,
      entryType: json['entryType'] as String?,
      windowFrom: _date(json['windowFrom']),
      windowTo: _date(json['windowTo']),
      // Absent means the backend considered the window complete; only an
      // explicit false marks a truncated result as partial.
      complete: json['complete'] != false,
      entries: entries,
      statusMessage: json['statusMessage'] as String?,
    );
  }

  static PortfolioHistoryEntry historyEntryFromJson(Map<String, dynamic> json) {
    return PortfolioHistoryEntry(
      entryType: json['entryType'] as String? ?? '',
      accountMode: parseMode(json['accountMode']),
      accountCategory: parseCategory(json['accountCategory']),
      positionSide: json['positionSide'] as String?,
      orderType: json['orderType'] as String?,
      symbol: json['symbol'] as String?,
      orderId: _int(json['orderId']),
      tradeId: _int(json['tradeId']),
      side: json['side'] as String?,
      status: json['status'] as String?,
      price: _num(json['price']),
      quantity: _num(json['quantity']),
      quoteQuantity: _num(json['quoteQuantity']),
      fee: _num(json['fee']),
      feeAsset: json['feeAsset'] as String?,
      realizedPnl: _num(json['realizedPnl']),
      occurredAt: _date(json['occurredAt']),
    );
  }

  static PortfolioOrders ordersFromJson(Map<String, dynamic> json) {
    final raw = json['orders'];
    final orders = <PortfolioOrder>[];
    if (raw is List) {
      for (final entry in raw) {
        if (entry is Map<String, dynamic>) {
          orders.add(orderFromJson(entry));
        }
      }
    }
    return PortfolioOrders(
      accountMode: parseMode(json['accountMode']),
      accountCategory: parseCategory(json['accountCategory']),
      availability: PortfolioAvailability.parse(
        json['availability'] as String?,
      ),
      source: json['source'] as String?,
      orders: orders,
      statusMessage: json['statusMessage'] as String?,
    );
  }

  static PortfolioOrder orderFromJson(Map<String, dynamic> json) {
    return PortfolioOrder(
      cancelId: json['cancelId'] as String?,
      accountMode: parseMode(json['accountMode']),
      accountCategory: parseCategory(json['accountCategory']),
      symbol: json['symbol'] as String? ?? '',
      side: json['side'] as String?,
      positionSide: json['positionSide'] as String?,
      orderType: json['orderType'] as String? ?? 'UNKNOWN',
      // An absent or unrecognised status stays UNKNOWN. It is never widened to
      // FILLED, which is what keeps "submitted" and "filled" impossible to
      // confuse on screen.
      status: PortfolioOrderStatus.parse(json['status']),
      price: _num(json['price']),
      stopPrice: _num(json['stopPrice']),
      averageFillPrice: _num(json['averageFillPrice']),
      originalQuantity: _num(json['originalQuantity']),
      executedQuantity: _num(json['executedQuantity']),
      remainingQuantity: _num(json['remainingQuantity']),
      reduceOnly: json['reduceOnly'] as bool?,
      orderId: _int(json['orderId']),
      clientOrderId: json['clientOrderId'] as String?,
      createdAt: _date(json['createdAt']),
      updatedAt: _date(json['updatedAt']),
    );
  }

  static PortfolioClosedPositions closedPositionsFromJson(
    Map<String, dynamic> json,
  ) {
    final raw = json['positions'];
    final positions = <PortfolioClosedPosition>[];
    if (raw is List) {
      for (final entry in raw) {
        if (entry is Map<String, dynamic>) {
          positions.add(closedPositionFromJson(entry));
        }
      }
    }
    return PortfolioClosedPositions(
      accountMode: parseMode(json['accountMode']),
      accountCategory: parseCategory(json['accountCategory']),
      availability: PortfolioAvailability.parse(
        json['availability'] as String?,
      ),
      source: json['source'] as String?,
      partial: json['partial'] == true,
      positions: positions,
      statusMessage: json['statusMessage'] as String?,
    );
  }

  static PortfolioClosedPosition closedPositionFromJson(
    Map<String, dynamic> json,
  ) {
    return PortfolioClosedPosition(
      accountMode: parseMode(json['accountMode']),
      accountCategory: parseCategory(json['accountCategory']),
      symbol: json['symbol'] as String? ?? '',
      side: json['side'] as String?,
      entryPrice: _num(json['entryPrice']),
      exitPrice: _num(json['exitPrice']),
      quantity: _num(json['quantity']),
      realizedPnl: _num(json['realizedPnl']),
      fees: _num(json['fees']),
      funding: _num(json['funding']),
      feesAsset: json['feesAsset'] as String?,
      marginType: json['marginType'] as String?,
      leverage: _int(json['leverage']),
      openedAt: _date(json['openedAt']),
      closedAt: _date(json['closedAt']),
      duration: _duration(json['duration']),
      orderIds: _intList(json['orderIds']),
      tradeIds: _intList(json['tradeIds']),
    );
  }

  /// Parses a duration as the backend serialises it.
  ///
  /// Spring Boot writes a `java.time.Duration` as an ISO-8601 string (`PT2H15M30S`) rather than a
  /// number of seconds, but a numeric value is also accepted so a configuration change cannot turn a
  /// real duration into "unavailable". Anything unrecognised returns null and renders as
  /// unavailable; it never becomes a zero-length duration.
  static Duration? _duration(Object? value) {
    if (value == null) return null;
    if (value is num) return Duration(microseconds: (value * 1000000).round());

    final text = value.toString().trim().toUpperCase();
    if (text.isEmpty) return null;

    // Plain seconds, in case the value is numeric-as-string.
    final asSeconds = num.tryParse(text);
    if (asSeconds != null) {
      return Duration(microseconds: (asSeconds * 1000000).round());
    }

    // ISO-8601: PnDTnHnMnS, where every component is optional but P is not.
    if (!text.startsWith('P')) return null;
    final match = RegExp(
      r'^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?)?$',
    ).firstMatch(text);
    if (match == null) return null;

    final days = int.tryParse(match.group(1) ?? '0') ?? 0;
    final hours = int.tryParse(match.group(2) ?? '0') ?? 0;
    final minutes = int.tryParse(match.group(3) ?? '0') ?? 0;
    final seconds = double.tryParse(match.group(4) ?? '0') ?? 0;

    return Duration(
      days: days,
      hours: hours,
      minutes: minutes,
      microseconds: (seconds * 1000000).round(),
    );
  }

  static List<int> _intList(Object? value) {
    if (value is! List) return const [];
    final ids = <int>[];
    for (final entry in value) {
      final id = _int(entry);
      if (id != null) ids.add(id);
    }
    return List.unmodifiable(ids);
  }

  static PortfolioSyncStatus syncStatusFromJson(Map<String, dynamic> json) {
    return PortfolioSyncStatus(
      accountMode: parseMode(json['accountMode']),
      accountCategory: parseCategory(json['accountCategory']),
      availability: PortfolioAvailability.parse(
        json['availability'] as String?,
      ),
      connectionStatus: json['connectionStatus'] as String?,
      lastRestSync: _date(json['lastRestSync']),
      lastEvent: _date(json['lastEvent']),
      stale: json['stale'] == true,
      message: json['message'] as String?,
    );
  }

  static PortfolioMode parseMode(Object? raw) => PortfolioMode.parse(raw);

  static PortfolioCategory parseCategory(Object? raw) =>
      PortfolioCategory.parse(raw);

  /// Null is meaningful here: a position with no originating signal has no
  /// market category and must not be assigned one.
  static PortfolioCategory? _optionalCategory(Object? raw) =>
      PortfolioCategory.parseOptional(raw);

  /// A missing value stays null. It is never coerced to zero.
  static double? _num(Object? value) {
    if (value == null) return null;
    if (value is num) return value.toDouble();
    return double.tryParse(value.toString());
  }

  static int? _int(Object? value) {
    if (value == null) return null;
    if (value is num) return value.toInt();
    return int.tryParse(value.toString());
  }

  static DateTime? _date(Object? value) {
    if (value == null) return null;
    return DateTime.tryParse(value.toString());
  }
}
