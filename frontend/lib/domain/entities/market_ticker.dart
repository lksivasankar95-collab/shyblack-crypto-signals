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
  });

  final String symbol;
  final String name;
  final double price;
  final double change24h;
  final double changePercent24h;
  final double volume24h;
  final double high24h;
  final double low24h;

  bool get isPositive => changePercent24h >= 0;

  String get baseSymbol {
    final upper = symbol.toUpperCase();
    if (upper.endsWith('USDT') && upper.length > 4) {
      return upper.substring(0, upper.length - 4);
    }
    return upper;
  }
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
