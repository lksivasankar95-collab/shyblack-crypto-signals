import '../entities/app_settings.dart';
import '../entities/kline_candle.dart';
import '../entities/market_ticker.dart';
import '../repositories/market_repository.dart';

class GetMarkets {
  const GetMarkets(this._repository);
  final MarketRepository _repository;

  Future<MarketSnapshot> call(TradingMode mode) => _repository.getMarkets(mode);
}

class GetMarketGainers {
  const GetMarketGainers(this._repository);
  final MarketRepository _repository;

  Future<MarketSnapshot> call(TradingMode mode) => _repository.getGainers(mode);
}

class GetMarketLosers {
  const GetMarketLosers(this._repository);
  final MarketRepository _repository;

  Future<MarketSnapshot> call(TradingMode mode) => _repository.getLosers(mode);
}

class GetMarketTicker {
  const GetMarketTicker(this._repository);
  final MarketRepository _repository;

  Future<MarketTicker> call(String symbol, TradingMode mode) =>
      _repository.getTicker(symbol, mode);
}

class GetKlines {
  const GetKlines(this._repository);
  final MarketRepository _repository;

  Future<List<KlineCandle>> call({
    required String symbol,
    required String interval,
    required TradingMode mode,
    int limit = 100,
  }) =>
      _repository.getKlines(symbol: symbol, interval: interval, limit: limit, mode: mode);
}
