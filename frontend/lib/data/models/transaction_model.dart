import '../../domain/entities/transaction.dart';

class TransactionModel {
  const TransactionModel({
    required this.id,
    required this.symbol,
    required this.type,
  });

  final String id;
  final String symbol;
  final String type;

  factory TransactionModel.fromJson(Map<String, dynamic> json) {
    return TransactionModel(
      id: json['id'] as String,
      symbol: json['symbol'] as String,
      type: json['type'] as String,
    );
  }

  Transaction toEntity() => Transaction(id: id, symbol: symbol, type: type);
}
