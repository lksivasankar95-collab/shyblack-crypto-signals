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

  MarketTicker copyWith({
    String? symbol,
    String? name,
    double? price,
    double? change24h,
    double? changePercent24h,
    double? volume24h,
    double? high24h,
    double? low24h,
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
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is MarketTicker &&
          symbol == other.symbol &&
          name == other.name &&
          price == other.price &&
          change24h == other.change24h &&
          changePercent24h == other.changePercent24h &&
          volume24h == other.volume24h &&
          high24h == other.high24h &&
          low24h == other.low24h;

  @override
  int get hashCode => Object.hash(
        symbol,
        name,
        price,
        change24h,
        changePercent24h,
        volume24h,
        high24h,
        low24h,
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
