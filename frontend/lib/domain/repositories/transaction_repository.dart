import '../entities/transaction.dart';

abstract class TransactionRepository {
  Future<List<Transaction>> getTransactions();
}
