import 'package:flutter/foundation.dart';

/// Single source of truth for API + WebSocket URLs.
///
/// Override at build/run time (no code change):
///   --dart-define=API_BASE_URL=http://10.0.2.2:8080/api
///   --dart-define=WS_BASE_URL=ws://10.0.2.2:8080
///
/// Defaults:
///   Android emulator        -> 10.0.2.2 (host loopback)
///   desktop / web / iOS sim -> localhost
///
/// Endpoint constants below must NOT repeat the `/api` prefix that already
/// lives in [baseUrl].
abstract final class ApiConstants {
  static const String _apiBaseUrlOverride = String.fromEnvironment(
    'API_BASE_URL',
  );
  static const String _wsBaseUrlOverride = String.fromEnvironment(
    'WS_BASE_URL',
  );

  static String get baseUrl {
    if (_apiBaseUrlOverride.isNotEmpty) return _apiBaseUrlOverride;
    if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) {
      return 'http://10.0.2.2:8080/api';
    }
    return 'http://localhost:8080/api';
  }

  static String get wsBaseUrl {
    if (_wsBaseUrlOverride.isNotEmpty) return _wsBaseUrlOverride;
    if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) {
      return 'ws://10.0.2.2:8080';
    }
    return 'ws://localhost:8080';
  }

  static const Duration connectTimeout = Duration(seconds: 15);
  static const Duration receiveTimeout = Duration(seconds: 15);

  static String get marketsWsUrl => '$wsBaseUrl/ws/markets';
  static String get privateWsUrl => '$wsBaseUrl/ws/private';

  static const String users = '/users';
  // Unified Portfolio (read-only, Phase 5 contract). One flow covers PAPER and
  // LIVE; the mode and category are always sent explicitly so the client never
  // relies on a server-side default.
  static const String portfolioOverview = '/v1/portfolio';
  static String portfolioAccount(String category) => '/v1/portfolio/$category';
  static String portfolioPositions(String category) =>
      '/v1/portfolio/$category/positions';

  // Phase 7 read-only information layer. Holdings are wallet balances, history is
  // read from the exchange over an explicit bounded window, and sync status
  // reports freshness. All three are read-only; none of them can place an order.
  static String portfolioHoldings(String category) =>
      '/v1/portfolio/$category/holdings';

  static String portfolioHistory(String category) =>
      '/v1/portfolio/$category/history';

  // Binance-style account sections. All read-only.
  static String portfolioOpenOrders(String category) =>
      '/v1/portfolio/$category/open-orders';
  static String portfolioClosedPositions(String category) =>
      '/v1/portfolio/$category/closed-positions';
  static String portfolioTransactionHistory(String category) =>
      '/v1/portfolio/$category/transaction-history';
  static String portfolioFundingFees(String category) =>
      '/v1/portfolio/$category/funding-fees';

  static String portfolioSyncStatus(String category) =>
      '/v1/portfolio/$category/sync-status';

  // Paper account capital management. PAPER ONLY — the backend refuses these on a
  // live account, and the UI hides the control entirely outside PAPER mode. These
  // move simulated funds only; they can never touch real balances.
  static const String paperAddCapital = '/v1/paper-trading/account/capital/add';
  static const String paperReduceCapital =
      '/v1/paper-trading/account/capital/reduce';
  static const String paperCapitalHistory =
      '/v1/paper-trading/account/capital/history';

  // Paper position actions. Every one resolves the caller from the security
  // context, so no user id is ever sent from the client.
  static String paperPositionRisk(String id) =>
      '/v1/paper-trading/positions/$id/risk';
  static const String signals = '/v1/signals';
  static const String watchlist = '/v1/watchlist';
  static const String notifications = '/v1/notifications';
  static const String deviceTokens = '/device-tokens';
  static const String markets = '/markets';
  static const String marketsGainers = '/markets/gainers';
  static const String marketsLosers = '/markets/losers';

  static String marketSymbol(String symbol) => '/markets/$symbol';

  static String marketKlines(String symbol) => '/markets/$symbol/klines';
  static const String auth = '/auth';
  static const String authLogin = '/auth/login';
  static const String authSignup = '/auth/signup';
  static const String authRefresh = '/auth/refresh';
  static const String authGoogle = '/auth/google';
  static const String usersMe = '/users/me';

  // Backtesting
  static const String backtests = '/v1/backtests';
  static const String backtestStrategies = '/v1/backtests/strategies';
  static String backtest(String id) => '/v1/backtests/$id';
  static String backtestTrades(String id) => '/v1/backtests/$id/trades';
  static String backtestEquity(String id) => '/v1/backtests/$id/equity';
  static String backtestCancel(String id) => '/v1/backtests/$id/cancel';

  // Futures Trading
  static const String futuresAccount = '/v1/futures-trading/account';
  static const String futuresConnection = '/v1/futures-trading/connection';
  static const String futuresConnectionValidate =
      '/v1/futures-trading/connection/validate';
  static const String futuresAcknowledge = '/v1/futures-trading/acknowledge';
  static const String futuresActivate = '/v1/futures-trading/activate';
  static const String futuresDeactivate = '/v1/futures-trading/deactivate';
  static const String futuresKillSwitch = '/v1/futures-trading/kill-switch';
  static const String futuresOrders = '/v1/futures-trading/orders';
  static const String futuresHistory = '/v1/futures-trading/history';
  static const String futuresPositions = '/v1/futures-trading/positions';
  static const String futuresPositionsHistory =
      '/v1/futures-trading/positions/history';
  static String futuresCancel(String id) =>
      '/v1/futures-trading/orders/$id/cancel';
  static String futuresClosePosition(String id) =>
      '/v1/futures-trading/positions/$id/close';

  // Live Trading
  static const String liveAccount = '/v1/live-trading/account';
  static const String liveConnection = '/v1/live-trading/connection';
  static const String liveConnectionValidate =
      '/v1/live-trading/connection/validate';
  static const String liveActivate = '/v1/live-trading/activate';
  static const String liveDeactivate = '/v1/live-trading/deactivate';
  static const String liveKillSwitch = '/v1/live-trading/kill-switch';
  static const String liveOrders = '/v1/live-trading/orders';
  static const String liveHistory = '/v1/live-trading/history';
  static const String livePerformance = '/v1/live-trading/performance';
  static String liveCancel(String id) => '/v1/live-trading/orders/$id/cancel';
  static String liveClosePosition(String entryOrderId) =>
      '/v1/live-trading/positions/$entryOrderId/close';

  // Paper Trading
  static const String paperAccount = '/v1/paper-trading/account';
  static const String paperAccountCapital = '/v1/paper-trading/account/capital';
  static const String paperPositions = '/v1/paper-trading/positions';
  static const String paperHistory = '/v1/paper-trading/history';
  static const String paperPerformance = '/v1/paper-trading/performance';
  static String paperClose(String id) =>
      '/v1/paper-trading/positions/$id/close';

  // Settings
  static const String settings = '/v1/settings';
  static const String settingsExchanges = '/v1/settings/exchanges';
  static const String settingsNotifications = '/v1/settings/notifications';
  static String settingsExchangeTest(String id) =>
      '/v1/settings/exchanges/$id/test-connection';
  static String settingsExchangeDelete(String id) =>
      '/v1/settings/exchanges/$id';

  static const String news = '/v1/news';
  static const String newsMeta = '/v1/news/meta';
  static const String newsSources = '/v1/news/sources';

  static String newsDetail(String id) => '/v1/news/$id';

  static String newsAsset(String symbol) => '/v1/news/asset/$symbol';

  static String newsAssetContext(String symbol) =>
      '/v1/news/asset/$symbol/context';

  // Strategies
  static const String strategies = '/v1/strategies';
  static const String strategiesActive = '/v1/strategies/active';
  static String strategyById(String id) => '/v1/strategies/$id';
}
