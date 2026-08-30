import '../entities/app_settings.dart';
import '../entities/market_ticker.dart';

abstract class MarketRepository {
  Future<MarketSnapshot> getMarkets(TradingMode mode);
  Future<MarketSnapshot> getGainers(TradingMode mode);
  Future<MarketSnapshot> getLosers(TradingMode mode);
}
