import '../../domain/entities/position.dart';

class PositionModel {
  const PositionModel({
    required this.id,
    required this.symbol,
    required this.side,
    required this.status,
  });

  final String id;
  final String symbol;
  final String side;
  final String status;

  factory PositionModel.fromJson(Map<String, dynamic> json) {
    return PositionModel(
      id: json['id'] as String,
      symbol: json['symbol'] as String,
      side: json['side'] as String,
      status: json['status'] as String,
    );
  }

  Position toEntity() => Position(id: id, symbol: symbol, side: side, status: status);
}
