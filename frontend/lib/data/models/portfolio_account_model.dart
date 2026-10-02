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
      availability: PortfolioAvailability.parse(json['availability'] as String?),
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
      availability: PortfolioAvailability.parse(json['availability'] as String?),
      positions: positions,
      statusMessage: json['statusMessage'] as String?,
    );
  }

  static PortfolioPosition positionFromJson(Map<String, dynamic> json) {
    return PortfolioPosition(
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
      availability: PortfolioAvailability.parse(json['availability'] as String?),
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
      availability: PortfolioAvailability.parse(json['availability'] as String?),
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

  static PortfolioSyncStatus syncStatusFromJson(Map<String, dynamic> json) {
    return PortfolioSyncStatus(
      accountMode: parseMode(json['accountMode']),
      accountCategory: parseCategory(json['accountCategory']),
      availability: PortfolioAvailability.parse(json['availability'] as String?),
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
