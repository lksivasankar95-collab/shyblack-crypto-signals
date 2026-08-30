import '../../domain/entities/app_settings.dart';
import '../../domain/entities/market_ticker.dart';

class MarketTickerModel {
  const MarketTickerModel({
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

  factory MarketTickerModel.fromJson(Map<String, dynamic> json) {
    return MarketTickerModel(
      symbol: json['symbol'] as String? ?? '',
      name: json['name'] as String? ?? '',
      price: _num(json['price']),
      change24h: _num(json['change24h']),
      changePercent24h: _num(json['changePercent24h']),
      volume24h: _num(json['volume24h']),
      high24h: _num(json['high24h']),
      low24h: _num(json['low24h']),
    );
  }

  MarketTicker toEntity() => MarketTicker(
        symbol: symbol,
        name: name,
        price: price,
        change24h: change24h,
        changePercent24h: changePercent24h,
        volume24h: volume24h,
        high24h: high24h,
        low24h: low24h,
      );

  static double _num(dynamic value) {
    if (value == null) {
      return 0;
    }
    if (value is num) {
      return value.toDouble();
    }
    return double.tryParse(value.toString()) ?? 0;
  }
}

class MarketSnapshotModel {
  const MarketSnapshotModel({
    required this.mode,
    required this.tickers,
    this.message,
  });

  final String mode;
  final String? message;
  final List<MarketTickerModel> tickers;

  factory MarketSnapshotModel.fromJson(dynamic data, {required TradingMode fallbackMode}) {
    if (data is List) {
      return MarketSnapshotModel(
        mode: fallbackMode.apiParam,
        tickers: data
            .whereType<Map>()
            .map((item) => MarketTickerModel.fromJson(Map<String, dynamic>.from(item)))
            .toList(),
      );
    }
    final json = data is Map<String, dynamic> ? data : <String, dynamic>{};
    final rawTickers = json['tickers'];
    return MarketSnapshotModel(
      mode: json['mode']?.toString() ?? fallbackMode.apiParam,
      message: json['message']?.toString(),
      tickers: rawTickers is List
          ? rawTickers
              .whereType<Map>()
              .map((item) => MarketTickerModel.fromJson(Map<String, dynamic>.from(item)))
              .toList()
          : const [],
    );
  }

  MarketSnapshot toEntity() => MarketSnapshot(
        mode: mode,
        message: message,
        tickers: tickers.map((model) => model.toEntity()).toList(),
      );
}
