import '../../domain/entities/signal.dart';

class SignalModel {
  const SignalModel({
    required this.id,
    required this.symbol,
    required this.status,
  });

  final String id;
  final String symbol;
  final String status;

  factory SignalModel.fromJson(Map<String, dynamic> json) {
    return SignalModel(
      id: json['id'] as String,
      symbol: json['symbol'] as String,
      status: json['status'] as String,
    );
  }

  Signal toEntity() => Signal(id: id, symbol: symbol, status: status);
}
