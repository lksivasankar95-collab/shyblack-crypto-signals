import '../../domain/entities/watchlist_item.dart';

class WatchlistModel {
  const WatchlistModel({
    required this.id,
    required this.symbol,
  });

  final String id;
  final String symbol;

  factory WatchlistModel.fromJson(Map<String, dynamic> json) {
    return WatchlistModel(
      id: json['id'] as String,
      symbol: json['symbol'] as String,
    );
  }

  WatchlistItem toEntity() => WatchlistItem(id: id, symbol: symbol);
}
