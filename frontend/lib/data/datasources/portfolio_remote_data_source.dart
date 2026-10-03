import '../../core/constants/api_constants.dart';
import '../../core/network/api_client.dart';

/// Read-only data source for the unified Portfolio API.
///
/// Consumes the Phase 5 contract exactly. It never connects to Binance and
/// never receives, stores or forwards any credential: the backend is the only
/// component that talks to an exchange.
class PortfolioRemoteDataSource {
  PortfolioRemoteDataSource(this._api);

  final ApiClient _api;

  Future<Map<String, dynamic>> getOverview(String mode) async {
    final res = await _api.dio.get<Map<String, dynamic>>(
      ApiConstants.portfolioOverview,
      queryParameters: {'mode': mode},
    );
    return res.data ?? const {};
  }

  Future<Map<String, dynamic>> getAccount(String mode, String category) async {
    final res = await _api.dio.get<Map<String, dynamic>>(
      ApiConstants.portfolioAccount(category),
      queryParameters: {'mode': mode},
    );
    return res.data ?? const {};
  }

  Future<Map<String, dynamic>> getPositions(
    String mode,
    String category,
  ) async {
    final res = await _api.dio.get<Map<String, dynamic>>(
      ApiConstants.portfolioPositions(category),
      queryParameters: {'mode': mode},
    );
    return res.data ?? const {};
  }

  /// Per-asset wallet holdings. Only LIVE spot has them; every other scope
  /// reports its own availability instead of an empty wallet.
  Future<Map<String, dynamic>> getHoldings(String mode, String category) async {
    final res = await _api.dio.get<Map<String, dynamic>>(
      ApiConstants.portfolioHoldings(category),
      queryParameters: {'mode': mode},
    );
    return res.data ?? const {};
  }

  /// Orders, fills or income over an explicit bounded window.
  ///
  /// [symbol] is required by the exchange for spot orders and fills, so it is
  /// passed through when known. The window bounds and the limit are sent
  /// explicitly rather than left to a server-side default.
  ///
  /// The narrowing parameters are optional; a null is omitted so the backend keeps
  /// its "no narrowing" meaning rather than receiving an empty value.
  Future<Map<String, dynamic>> getHistory({
    required String mode,
    required String category,
    required String type,
    String? symbol,
    DateTime? from,
    DateTime? to,
    int? limit,
    String? side,
    String? orderType,
    String? status,
    String? positionSide,
  }) async {
    final params = <String, dynamic>{'mode': mode, 'type': type};
    _put(params, 'symbol', symbol);
    _put(params, 'side', side);
    _put(params, 'orderType', orderType);
    _put(params, 'status', status);
    _put(params, 'positionSide', positionSide);
    _putWindow(params, from, to, limit);

    final res = await _api.dio.get<Map<String, dynamic>>(
      ApiConstants.portfolioHistory(category),
      queryParameters: params,
    );
    return res.data ?? const {};
  }

  /// Orders the exchange currently reports as resting. Read-only.
  Future<Map<String, dynamic>> getOpenOrders({
    required String mode,
    required String category,
    String? symbol,
    String? side,
    String? status,
  }) async {
    final params = <String, dynamic>{'mode': mode};
    _put(params, 'symbol', symbol);
    _put(params, 'side', side);
    _put(params, 'status', status);

    final res = await _api.dio.get<Map<String, dynamic>>(
      ApiConstants.portfolioOpenOrders(category),
      queryParameters: params,
    );
    return res.data ?? const {};
  }

  /// Positions closed inside an explicit window. Fields the source cannot prove
  /// arrive as null and are rendered as unavailable, never as zero.
  Future<Map<String, dynamic>> getClosedPositions({
    required String mode,
    required String category,
    String? symbol,
    DateTime? from,
    DateTime? to,
    int? limit,
  }) async {
    final params = <String, dynamic>{'mode': mode};
    _put(params, 'symbol', symbol);
    _putWindow(params, from, to, limit);

    final res = await _api.dio.get<Map<String, dynamic>>(
      ApiConstants.portfolioClosedPositions(category),
      queryParameters: params,
    );
    return res.data ?? const {};
  }

  /// Every account income record the exchange published in the window.
  Future<Map<String, dynamic>> getTransactionHistory({
    required String mode,
    required String category,
    String? symbol,
    DateTime? from,
    DateTime? to,
    int? limit,
  }) async {
    final params = <String, dynamic>{'mode': mode};
    _put(params, 'symbol', symbol);
    _putWindow(params, from, to, limit);

    final res = await _api.dio.get<Map<String, dynamic>>(
      ApiConstants.portfolioTransactionHistory(category),
      queryParameters: params,
    );
    return res.data ?? const {};
  }

  /// Funding fees paid or received in the window.
  Future<Map<String, dynamic>> getFundingFees({
    required String mode,
    required String category,
    String? symbol,
    DateTime? from,
    DateTime? to,
    int? limit,
  }) async {
    final params = <String, dynamic>{'mode': mode};
    _put(params, 'symbol', symbol);
    _putWindow(params, from, to, limit);

    final res = await _api.dio.get<Map<String, dynamic>>(
      ApiConstants.portfolioFundingFees(category),
      queryParameters: params,
    );
    return res.data ?? const {};
  }

  /// Connection state, data freshness and the reason a scope is not current.
  Future<Map<String, dynamic>> getSyncStatus(
    String mode,
    String category,
  ) async {
    final res = await _api.dio.get<Map<String, dynamic>>(
      ApiConstants.portfolioSyncStatus(category),
      queryParameters: {'mode': mode},
    );
    return res.data ?? const {};
  }

  /// An empty or absent filter value means "no narrowing", so it is omitted rather
  /// than sent and interpreted as a filter that matches nothing.
  static void _put(Map<String, dynamic> params, String key, String? value) {
    if (value != null && value.isNotEmpty) params[key] = value;
  }

  static void _putWindow(
    Map<String, dynamic> params,
    DateTime? from,
    DateTime? to,
    int? limit,
  ) {
    if (from != null) params['from'] = from.toUtc().toIso8601String();
    if (to != null) params['to'] = to.toUtc().toIso8601String();
    if (limit != null) params['limit'] = limit;
  }

  // ── Paper capital management (simulated funds only) ──────────────

  Future<Map<String, dynamic>> getPaperAccount() async {
    final res = await _api.dio.get<Map<String, dynamic>>(
      ApiConstants.paperAccount,
    );
    return res.data ?? const {};
  }

  /// Adds simulated capital. The backend validates amount > 0 and records an
  /// audited ADD event; historical trades are never rewritten.
  Future<Map<String, dynamic>> addPaperCapital({
    required double amount,
    String? reason,
  }) async {
    final res = await _api.dio.post<Map<String, dynamic>>(
      ApiConstants.paperAddCapital,
      data: {
        'amount': amount,
        if (reason != null && reason.isNotEmpty) 'reason': reason,
      },
    );
    return res.data ?? const {};
  }

  /// Withdraws simulated capital, bounded by free cash on the server side.
  Future<Map<String, dynamic>> reducePaperCapital({
    required double amount,
    String? reason,
  }) async {
    final res = await _api.dio.post<Map<String, dynamic>>(
      ApiConstants.paperReduceCapital,
      data: {
        'amount': amount,
        if (reason != null && reason.isNotEmpty) 'reason': reason,
      },
    );
    return res.data ?? const {};
  }

  /// The audited capital ledger, newest first.
  Future<List<dynamic>> getPaperCapitalHistory({int limit = 50}) async {
    final res = await _api.dio.get<List<dynamic>>(
      ApiConstants.paperCapitalHistory,
      queryParameters: {'limit': limit},
    );
    return res.data ?? const [];
  }

  /// Resets the simulated account to its starting balance.
  ///
  /// The server closes open positions and zeroes the counters; the capital
  /// ledger is deliberately preserved so the balance stays explainable.
  Future<Map<String, dynamic>> resetPaperAccount() async {
    final res = await _api.dio.delete<Map<String, dynamic>>(
      ApiConstants.paperAccount,
    );
    return res.data ?? const {};
  }

  // ── Paper position actions ───────────────────────────────────────

  /// Repositions the stop-loss / take-profit of an open paper position.
  ///
  /// [stopLoss] and [takeProfit] are both nullable and mean "leave unchanged" —
  /// there is no way to clear protection from the client. Side-aware validation
  /// (a long's stop must sit below entry) is enforced server side.
  Future<Map<String, dynamic>> updatePaperPositionRisk(
    String positionId, {
    double? stopLoss,
    double? takeProfit,
  }) async {
    final res = await _api.dio.patch<Map<String, dynamic>>(
      ApiConstants.paperPositionRisk(positionId),
      data: {'stopLoss': ?stopLoss, 'takeProfit': ?takeProfit},
    );
    return res.data ?? const {};
  }

  /// Closes an open paper position at the current market price.
  Future<Map<String, dynamic>> closePaperPosition(String positionId) async {
    final res = await _api.dio.post<Map<String, dynamic>>(
      ApiConstants.paperClose(positionId),
    );
    return res.data ?? const {};
  }
}
