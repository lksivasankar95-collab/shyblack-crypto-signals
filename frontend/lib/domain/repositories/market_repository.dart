import '../entities/app_settings.dart';
import '../entities/kline_candle.dart';
import '../entities/market_ticker.dart';

abstract class MarketRepository {
  Future<MarketSnapshot> getMarkets(TradingMode mode);
  Future<MarketSnapshot> getGainers(TradingMode mode);
  Future<MarketSnapshot> getLosers(TradingMode mode);
  Future<MarketTicker> getTicker(String symbol, TradingMode mode);
  Future<List<KlineCandle>> getKlines({
    required String symbol,
    required String interval,
    required int limit,
    required TradingMode mode,
  });
}
