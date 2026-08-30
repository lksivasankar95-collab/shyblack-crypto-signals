abstract final class ApiConstants {
  static const String baseUrl = 'http://localhost:8080/api';
  static const Duration connectTimeout = Duration(seconds: 15);
  static const Duration receiveTimeout = Duration(seconds: 15);

  static const String users = '/v1/users';
  static const String portfolios = '/v1/portfolios';
  static const String positions = '/v1/positions';
  static const String transactions = '/v1/transactions';
  static const String signals = '/v1/signals';
  static const String watchlist = '/v1/watchlist';
  static const String notifications = '/v1/notifications';
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
}
