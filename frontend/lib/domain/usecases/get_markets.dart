import '../entities/app_settings.dart';
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
