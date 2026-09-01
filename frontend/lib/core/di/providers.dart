import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../network/api_client.dart';
import '../../data/datasources/auth_remote_data_source.dart';
import '../../data/datasources/market_remote_data_source.dart';
import '../../data/datasources/markets_websocket_client.dart';
import '../../data/datasources/notification_remote_data_source.dart';
import '../../data/datasources/portfolio_remote_data_source.dart';
import '../../data/datasources/position_remote_data_source.dart';
import '../../data/datasources/signal_remote_data_source.dart';
import '../../data/datasources/transaction_remote_data_source.dart';
import '../../data/datasources/user_remote_data_source.dart';
import '../../data/datasources/watchlist_remote_data_source.dart';
import '../../data/datasources/token_local_data_source.dart';
import '../../data/repositories/auth_repository_impl.dart';
import '../../data/repositories/market_repository_impl.dart';
import '../../data/repositories/notification_repository_impl.dart';
import '../../data/repositories/portfolio_repository_impl.dart';
import '../../data/repositories/position_repository_impl.dart';
import '../../data/repositories/signal_repository_impl.dart';
import '../../data/repositories/transaction_repository_impl.dart';
import '../../data/repositories/user_repository_impl.dart';
import '../../data/repositories/watchlist_repository_impl.dart';
import '../../domain/repositories/auth_repository.dart';
import '../../domain/repositories/market_repository.dart';
import '../../domain/repositories/notification_repository.dart';
import '../../domain/repositories/portfolio_repository.dart';
import '../../domain/repositories/position_repository.dart';
import '../../domain/repositories/signal_repository.dart';
import '../../domain/repositories/transaction_repository.dart';
import '../../domain/repositories/user_repository.dart';
import '../../domain/repositories/watchlist_repository.dart';
import '../../domain/usecases/get_current_user.dart';
import '../../domain/usecases/login_user.dart';
import '../../domain/usecases/signup_user.dart';
import '../../domain/usecases/get_markets.dart';
import '../../domain/usecases/get_notifications.dart';
import '../../domain/usecases/get_portfolios.dart';
import '../../domain/usecases/get_positions.dart';
import '../../domain/usecases/get_signals.dart';
import '../../domain/usecases/get_transactions.dart';
import '../../domain/usecases/get_watchlist.dart';
import '../../domain/usecases/get_settings.dart';
import '../../domain/usecases/logout_user.dart';
import '../../domain/usecases/restore_session.dart';
import '../../domain/usecases/save_settings.dart';
import '../../data/datasources/settings_local_data_source.dart';
import '../../data/repositories/settings_repository_impl.dart';
import '../../domain/repositories/settings_repository.dart';

final apiClientProvider = Provider<ApiClient>(
  (ref) => ApiClient(tokens: ref.watch(tokenLocalDataSourceProvider)),
);

final tokenLocalDataSourceProvider = Provider<TokenLocalDataSource>(
  (ref) => TokenLocalDataSource(),
);

final authRemoteDataSourceProvider = Provider<AuthRemoteDataSource>(
  (ref) => AuthRemoteDataSource(ref.watch(apiClientProvider)),
);

final authRepositoryProvider = Provider<AuthRepository>(
  (ref) => AuthRepositoryImpl(
    ref.watch(authRemoteDataSourceProvider),
    ref.watch(tokenLocalDataSourceProvider),
  ),
);

final loginUserProvider = Provider<LoginUser>(
  (ref) => LoginUser(ref.watch(authRepositoryProvider)),
);

final signupUserProvider = Provider<SignupUser>(
  (ref) => SignupUser(ref.watch(authRepositoryProvider)),
);

final logoutUserProvider = Provider<LogoutUser>(
  (ref) => LogoutUser(ref.watch(authRepositoryProvider)),
);

final restoreSessionProvider = Provider<RestoreSession>(
  (ref) => RestoreSession(ref.watch(authRepositoryProvider)),
);

final settingsLocalDataSourceProvider = Provider<SettingsLocalDataSource>(
  (ref) => SettingsLocalDataSource(),
);

final settingsRepositoryProvider = Provider<SettingsRepository>(
  (ref) => SettingsRepositoryImpl(ref.watch(settingsLocalDataSourceProvider)),
);

final getSettingsProvider = Provider<GetSettings>(
  (ref) => GetSettings(ref.watch(settingsRepositoryProvider)),
);

final saveSettingsProvider = Provider<SaveSettings>(
  (ref) => SaveSettings(ref.watch(settingsRepositoryProvider)),
);

final userRemoteDataSourceProvider = Provider<UserRemoteDataSource>(
  (ref) => UserRemoteDataSource(ref.watch(apiClientProvider)),
);

final portfolioRemoteDataSourceProvider = Provider<PortfolioRemoteDataSource>(
  (ref) => PortfolioRemoteDataSource(ref.watch(apiClientProvider)),
);

final positionRemoteDataSourceProvider = Provider<PositionRemoteDataSource>(
  (ref) => PositionRemoteDataSource(ref.watch(apiClientProvider)),
);

final transactionRemoteDataSourceProvider = Provider<TransactionRemoteDataSource>(
  (ref) => TransactionRemoteDataSource(ref.watch(apiClientProvider)),
);

final signalRemoteDataSourceProvider = Provider<SignalRemoteDataSource>(
  (ref) => SignalRemoteDataSource(ref.watch(apiClientProvider)),
);

final marketRemoteDataSourceProvider = Provider<MarketRemoteDataSource>(
  (ref) => MarketRemoteDataSource(ref.watch(apiClientProvider)),
);

final marketsSocketConnectorProvider = Provider<MarketsSocketConnector>(
  (ref) => const WebSocketChannelConnector(),
);

final watchlistRemoteDataSourceProvider = Provider<WatchlistRemoteDataSource>(
  (ref) => WatchlistRemoteDataSource(ref.watch(apiClientProvider)),
);

final notificationRemoteDataSourceProvider = Provider<NotificationRemoteDataSource>(
  (ref) => NotificationRemoteDataSource(ref.watch(apiClientProvider)),
);

final userRepositoryProvider = Provider<UserRepository>(
  (ref) => UserRepositoryImpl(ref.watch(userRemoteDataSourceProvider)),
);

final portfolioRepositoryProvider = Provider<PortfolioRepository>(
  (ref) => PortfolioRepositoryImpl(ref.watch(portfolioRemoteDataSourceProvider)),
);

final positionRepositoryProvider = Provider<PositionRepository>(
  (ref) => PositionRepositoryImpl(ref.watch(positionRemoteDataSourceProvider)),
);

final transactionRepositoryProvider = Provider<TransactionRepository>(
  (ref) => TransactionRepositoryImpl(ref.watch(transactionRemoteDataSourceProvider)),
);

final signalRepositoryProvider = Provider<SignalRepository>(
  (ref) => SignalRepositoryImpl(ref.watch(signalRemoteDataSourceProvider)),
);

final marketRepositoryProvider = Provider<MarketRepository>(
  (ref) => MarketRepositoryImpl(ref.watch(marketRemoteDataSourceProvider)),
);

final watchlistRepositoryProvider = Provider<WatchlistRepository>(
  (ref) => WatchlistRepositoryImpl(ref.watch(watchlistRemoteDataSourceProvider)),
);

final notificationRepositoryProvider = Provider<NotificationRepository>(
  (ref) => NotificationRepositoryImpl(ref.watch(notificationRemoteDataSourceProvider)),
);

final getCurrentUserProvider = Provider<GetCurrentUser>(
  (ref) => GetCurrentUser(ref.watch(userRepositoryProvider)),
);

final getPortfoliosProvider = Provider<GetPortfolios>(
  (ref) => GetPortfolios(ref.watch(portfolioRepositoryProvider)),
);

final getPositionsProvider = Provider<GetPositions>(
  (ref) => GetPositions(ref.watch(positionRepositoryProvider)),
);

final getTransactionsProvider = Provider<GetTransactions>(
  (ref) => GetTransactions(ref.watch(transactionRepositoryProvider)),
);

final getSignalsProvider = Provider<GetSignals>(
  (ref) => GetSignals(ref.watch(signalRepositoryProvider)),
);

final getMarketsProvider = Provider<GetMarkets>(
  (ref) => GetMarkets(ref.watch(marketRepositoryProvider)),
);

final getMarketGainersProvider = Provider<GetMarketGainers>(
  (ref) => GetMarketGainers(ref.watch(marketRepositoryProvider)),
);

final getMarketLosersProvider = Provider<GetMarketLosers>(
  (ref) => GetMarketLosers(ref.watch(marketRepositoryProvider)),
);

final getMarketTickerProvider = Provider<GetMarketTicker>(
  (ref) => GetMarketTicker(ref.watch(marketRepositoryProvider)),
);

final getKlinesProvider = Provider<GetKlines>(
  (ref) => GetKlines(ref.watch(marketRepositoryProvider)),
);

final getWatchlistProvider = Provider<GetWatchlist>(
  (ref) => GetWatchlist(ref.watch(watchlistRepositoryProvider)),
);

final getNotificationsProvider = Provider<GetNotifications>(
  (ref) => GetNotifications(ref.watch(notificationRepositoryProvider)),
);
