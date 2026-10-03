import 'app_settings.dart';

/// One live quote, carrying the identity of the instrument it belongs to.
///
/// `symbol` is what Binance addresses the instrument with and is **not** sufficient to
/// identify it: `BTCUSDT` is a live spot pair and a live USDT-M futures perpetual at the
/// same moment, at different prices. [marketType] is what separates them.
///
/// Identity fields are rendered, not parsed: [displaySymbol] is supplied by the server
/// from Binance metadata so that a perpetual is never collapsed into the same label as
/// the spot pair that shares its symbol.
class MarketTicker {
  const MarketTicker({
    required this.symbol,
    required this.name,
    required this.price,
    required this.change24h,
    required this.changePercent24h,
    required this.volume24h,
    required this.high24h,
    required this.low24h,
    this.marketType = TradingMode.spot,
    this.exchangeSymbol,
    this.displaySymbol,
    this.baseAsset,
    this.quoteAsset,
    this.contractType,
  });

  final String symbol;
  final String name;
  final double price;
  final double change24h;
  final double changePercent24h;
  final double volume24h;
  final double high24h;
  final double low24h;

  /// Which market this quote belongs to. Never derived from [symbol].
  final TradingMode marketType;

  /// The symbol Binance addresses this instrument with. Defaults to [symbol].
  final String? exchangeSymbol;

  /// Server-derived label, e.g. `BTC/USDT` spot or `BTCUSDT Perpetual`.
  final String? displaySymbol;

  final String? baseAsset;
  final String? quoteAsset;

  /// `PERPETUAL`, `CURRENT_QUARTER`, ... for futures; null for spot.
  final String? contractType;

  bool get isPositive => changePercent24h >= 0;

  bool get isFutures => marketType == TradingMode.futures;

  /// Label to render for this instrument.
  ///
  /// Prefers the server-derived value. Falls back to the exchange symbol so a market
  /// label is never silently dropped when metadata is unavailable.
  String get displayLabel => displaySymbol ?? exchangeSymbol ?? symbol;

  /// Underlying asset, from Binance metadata.
  ///
  /// Falls back to the exchange symbol rather than trying to slice the asset out of it.
  /// Splitting `BTCUSDT` into `BTC` would be a guess about the quote asset that is wrong for
  /// any non-USDT market, and this app supports a configurable quote asset.
  String get baseSymbol => baseAsset ?? exchangeSymbol ?? symbol;

  MarketTicker copyWith({
    String? symbol,
    String? name,
    double? price,
    double? change24h,
    double? changePercent24h,
    double? volume24h,
    double? high24h,
    double? low24h,
    TradingMode? marketType,
    String? exchangeSymbol,
    String? displaySymbol,
    String? baseAsset,
    String? quoteAsset,
    String? contractType,
  }) {
    return MarketTicker(
      symbol: symbol ?? this.symbol,
      name: name ?? this.name,
      price: price ?? this.price,
      change24h: change24h ?? this.change24h,
      changePercent24h: changePercent24h ?? this.changePercent24h,
      volume24h: volume24h ?? this.volume24h,
      high24h: high24h ?? this.high24h,
      low24h: low24h ?? this.low24h,
      marketType: marketType ?? this.marketType,
      exchangeSymbol: exchangeSymbol ?? this.exchangeSymbol,
      displaySymbol: displaySymbol ?? this.displaySymbol,
      baseAsset: baseAsset ?? this.baseAsset,
      quoteAsset: quoteAsset ?? this.quoteAsset,
      contractType: contractType ?? this.contractType,
    );
  }

  /// Identity is market **plus** symbol.
  ///
  /// Two tickers that differ only by market are different instruments, so they must not
  /// be treated as the same value; collapsing them is what let one market's price stand
  /// in for the other's.
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is MarketTicker &&
          marketType == other.marketType &&
          symbol == other.symbol &&
          name == other.name &&
          price == other.price &&
          change24h == other.change24h &&
          changePercent24h == other.changePercent24h &&
          volume24h == other.volume24h &&
          high24h == other.high24h &&
          low24h == other.low24h &&
          displaySymbol == other.displaySymbol &&
          contractType == other.contractType;

  @override
  int get hashCode => Object.hash(
    marketType,
    symbol,
    name,
    price,
    change24h,
    changePercent24h,
    volume24h,
    high24h,
    low24h,
    displaySymbol,
    contractType,
  );
}

class MarketSnapshot {
  const MarketSnapshot({
    required this.mode,
    required this.tickers,
    this.message,
  });

  final String mode;
  final String? message;
  final List<MarketTicker> tickers;

  bool get isOptionsUnavailable =>
      mode.toUpperCase() == 'OPTIONS' && tickers.isEmpty;
}
