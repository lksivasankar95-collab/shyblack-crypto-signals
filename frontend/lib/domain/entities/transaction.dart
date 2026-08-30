class Transaction {
  const Transaction({
    required this.id,
    required this.symbol,
    required this.type,
  });

  final String id;
  final String symbol;
  final String type;
}
