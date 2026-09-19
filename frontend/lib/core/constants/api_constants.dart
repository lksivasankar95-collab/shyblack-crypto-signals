abstract final class ApiConstants {
  static const String baseUrl = 'http://localhost:8080/api';
  static const String marketsWsUrl = 'ws://localhost:8080/ws/markets';
  static const Duration connectTimeout = Duration(seconds: 15);
  static const Duration receiveTimeout = Duration(seconds: 15);

  static const String users = '/v1/users';
  static const String portfolios = '/v1/portfolios';
  static const String positions = '/v1/positions';
  static const String transactions = '/v1/transactions';
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

  // Paper Trading
  static const String paperAccount = '/v1/paper-trading/account';
  static const String paperPositions = '/v1/paper-trading/positions';
  static const String paperHistory = '/v1/paper-trading/history';
  static const String paperPerformance = '/v1/paper-trading/performance';
  static String paperClose(String id) => '/v1/paper-trading/positions/$id/close';

  // Settings
  static const String settings = '/v1/settings';
  static const String settingsExchanges = '/v1/settings/exchanges';
  static const String settingsNotifications = '/v1/settings/notifications';
  static String settingsExchangeTest(String id) => '/v1/settings/exchanges/$id/test-connection';
  static String settingsExchangeDelete(String id) => '/v1/settings/exchanges/$id';

  static const String news = '/v1/news';
  static const String newsMeta = '/v1/news/meta';
  static const String newsSources = '/v1/news/sources';

  static String newsDetail(String id) => '/v1/news/$id';

  static String newsAsset(String symbol) => '/v1/news/asset/$symbol';

  static String newsAssetContext(String symbol) =>
      '/v1/news/asset/$symbol/context';
}
