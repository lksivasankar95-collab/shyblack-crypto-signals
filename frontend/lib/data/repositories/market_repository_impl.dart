import '../../domain/entities/app_settings.dart';
import '../../domain/entities/kline_candle.dart';
import '../../domain/entities/market_ticker.dart';
import '../../domain/repositories/market_repository.dart';
import '../datasources/market_remote_data_source.dart';

class MarketRepositoryImpl implements MarketRepository {
  MarketRepositoryImpl(this._remote);
  final MarketRemoteDataSource _remote;

  @override
  Future<MarketSnapshot> getMarkets(TradingMode mode) async {
    final model = await _remote.getMarkets(mode);
    return model.toEntity();
  }

  @override
  Future<MarketSnapshot> getGainers(TradingMode mode) async {
    final model = await _remote.getGainers(mode);
    return model.toEntity();
  }

  @override
  Future<MarketSnapshot> getLosers(TradingMode mode) async {
    final model = await _remote.getLosers(mode);
    return model.toEntity();
  }

  @override
  Future<MarketTicker> getTicker(String symbol, TradingMode mode) async {
    final model = await _remote.getTicker(symbol, mode);
    return model.toEntity();
  }

  @override
  Future<List<KlineCandle>> getKlines({
    required String symbol,
    required String interval,
    required int limit,
    required TradingMode mode,
  }) async {
    final models = await _remote.getKlines(
      symbol: symbol,
      interval: interval,
      limit: limit,
      mode: mode,
    );
    return models.map((model) => model.toEntity()).toList();
  }
}
