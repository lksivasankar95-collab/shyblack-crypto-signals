import 'package:cryptosignals/data/models/market_ticker_model.dart';
import 'package:cryptosignals/domain/entities/app_settings.dart';
import 'package:cryptosignals/domain/entities/market_ticker.dart';
import 'package:cryptosignals/presentation/providers/coin_detail_providers.dart';
import 'package:cryptosignals/presentation/providers/market_prices_controller.dart';
import 'package:flutter_test/flutter_test.dart';

/// Distinct synthetic prices so a cross-market read is detectable rather than
/// coincidentally equal.
const spotPrice = 100000.0;
const futuresPrice = 100500.0;

MarketTicker ticker(
  String symbol,
  double price, {
  TradingMode market = TradingMode.spot,
  String? contractType,
  String? displaySymbol,
}) => MarketTicker(
  symbol: symbol,
  name: 'Bitcoin',
  price: price,
  change24h: 1,
  changePercent24h: 1,
  volume24h: 10,
  high24h: price,
  low24h: price,
  marketType: market,
  exchangeSymbol: symbol,
  displaySymbol: displaySymbol ?? symbol,
  baseAsset: 'BTC',
  quoteAsset: 'USDT',
  contractType: contractType,
);

void main() {
  group('ticker identity', () {
    test('two markets on one symbol are different values', () {
      final spot = ticker('BTCUSDT', spotPrice);
      final futures = ticker(
        'BTCUSDT',
        futuresPrice,
        market: TradingMode.futures,
      );

      // Same symbol, different instrument. Collapsing these is how one market's price
      // comes to stand in for the other's.
      expect(spot, isNot(futures));
      expect(spot.hashCode, isNot(futures.hashCode));
    });

    test('a set can hold the same symbol from both markets', () {
      final set = {
        ticker('BTCUSDT', spotPrice),
        ticker('BTCUSDT', futuresPrice, market: TradingMode.futures),
      };
      expect(set, hasLength(2));
    });

    test('display labels distinguish a perpetual from the spot pair', () {
      expect(
        ticker('BTCUSDT', spotPrice, displaySymbol: 'BTC/USDT').displayLabel,
        'BTC/USDT',
      );
      expect(
        ticker(
          'BTCUSDT',
          futuresPrice,
          market: TradingMode.futures,
          contractType: 'PERPETUAL',
          displaySymbol: 'BTCUSDT Perpetual',
        ).displayLabel,
        'BTCUSDT Perpetual',
      );
    });
  });

  group('payload parsing', () {
    test('reads marketType, displaySymbol and contractType from the API', () {
      final model = MarketTickerModel.fromJson({
        'symbol': 'BTCUSDT',
        'name': 'BTC',
        'price': 100500,
        'marketType': 'FUTURES',
        'exchangeSymbol': 'BTCUSDT',
        'displaySymbol': 'BTCUSDT Perpetual',
        'baseAsset': 'BTC',
        'quoteAsset': 'USDT',
        'contractType': 'PERPETUAL',
      });

      expect(model.marketType, TradingMode.futures);
      expect(model.contractType, 'PERPETUAL');
      expect(model.toEntity().displayLabel, 'BTCUSDT Perpetual');
      expect(model.toEntity().isFutures, isTrue);
    });

    test(
      'an omitted marketType falls back to the requested market, not a guess',
      () {
        final model = MarketTickerModel.fromJson({
          'symbol': 'BTCUSDT',
          'name': 'BTC',
          'price': 1,
        }, fallbackMode: TradingMode.futures);
        expect(model.marketType, TradingMode.futures);
      },
    );

    test('a spot payload has no contract type', () {
      final model = MarketTickerModel.fromJson({
        'symbol': 'BTCUSDT',
        'name': 'BTC',
        'price': 100000,
        'marketType': 'SPOT',
        'displaySymbol': 'BTC/USDT',
      });
      expect(model.marketType, TradingMode.spot);
      expect(model.contractType, isNull);
      expect(model.toEntity().isFutures, isFalse);
    });
  });

  group('market price book', () {
    test('the same symbol resolves independently per market', () {
      final book = const MarketPriceBook()
          .withSnapshot(TradingMode.spot, [ticker('BTCUSDT', spotPrice)])
          .withSnapshot(TradingMode.futures, [
            ticker('BTCUSDT', futuresPrice, market: TradingMode.futures),
          ]);

      expect(book.priceFor(TradingMode.spot, 'BTCUSDT'), spotPrice);
      expect(book.priceFor(TradingMode.futures, 'BTCUSDT'), futuresPrice);
    });

    test(
      'a missing market returns null rather than another market\'s price',
      () {
        final book = const MarketPriceBook().withSnapshot(TradingMode.spot, [
          ticker('BTCUSDT', spotPrice),
        ]);

        // A fallback would be indistinguishable from a real quote.
        expect(book.priceFor(TradingMode.futures, 'BTCUSDT'), isNull);
      },
    );

    test('a tick on one market does not disturb the other', () {
      final book = const MarketPriceBook()
          .withSnapshot(TradingMode.spot, [ticker('BTCUSDT', spotPrice)])
          .withSnapshot(TradingMode.futures, [
            ticker('BTCUSDT', futuresPrice, market: TradingMode.futures),
          ])
          .withTicks(TradingMode.futures, [
            ticker('BTCUSDT', 100900, market: TradingMode.futures),
          ]);

      expect(book.priceFor(TradingMode.spot, 'BTCUSDT'), spotPrice);
      expect(book.priceFor(TradingMode.futures, 'BTCUSDT'), 100900);
    });

    test('a mislabelled tick is discarded rather than stored', () {
      final book = const MarketPriceBook().withTicks(
        TradingMode.futures,
        // Claims to be futures data but arrives on the spot stream.
        [ticker('BTCUSDT', 1, market: TradingMode.spot)],
      );

      expect(book.priceFor(TradingMode.futures, 'BTCUSDT'), isNull);
      expect(book.priceFor(TradingMode.spot, 'BTCUSDT'), isNull);
    });

    test('lookup is case-insensitive on the exchange symbol', () {
      final book = const MarketPriceBook().withSnapshot(TradingMode.spot, [
        ticker('BTCUSDT', spotPrice),
      ]);
      expect(book.priceFor(TradingMode.spot, 'btcusdt'), spotPrice);
    });

    test('connection state is tracked per market', () {
      final book = const MarketPriceBook().withSnapshot(TradingMode.spot, [
        ticker('BTCUSDT', spotPrice),
      ]);
      expect(book.isConnected(TradingMode.spot), isTrue);
      expect(book.isConnected(TradingMode.futures), isFalse);
    });
  });

  group('market reference', () {
    test('the same symbol on two markets is two different references', () {
      // Guards the provider-family key: these are family keys, so collapsing them would
      // let one market's cached entry answer the other's request.
      const spotRef = MarketRef(symbol: 'BTCUSDT', market: TradingMode.spot);
      const futuresRef = MarketRef(
        symbol: 'BTCUSDT',
        market: TradingMode.futures,
      );

      expect(spotRef, isNot(futuresRef));
      expect(spotRef.hashCode, isNot(futuresRef.hashCode));
      expect({spotRef, futuresRef}, hasLength(2));
    });

    test('a market-qualified reference equals itself', () {
      const a = MarketRef(symbol: 'BTCUSDT', market: TradingMode.futures);
      const b = MarketRef(symbol: 'BTCUSDT', market: TradingMode.futures);
      expect(a, b);
    });

    test('candle queries are keyed by market too', () {
      const spot = KlineQuery(symbol: 'BTCUSDT', interval: '1d');
      const futures = KlineQuery(
        symbol: 'BTCUSDT',
        interval: '1d',
        market: TradingMode.futures,
      );

      // Spot and futures candles for one symbol are different series.
      expect(spot, isNot(futures));
      expect(spot.hashCode, isNot(futures.hashCode));
    });
  });
}
