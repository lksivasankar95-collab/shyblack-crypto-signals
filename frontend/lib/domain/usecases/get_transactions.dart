import '../entities/transaction.dart';
import '../repositories/transaction_repository.dart';

class GetTransactions {
  const GetTransactions(this._repository);
  final TransactionRepository _repository;

  Future<List<Transaction>> call() => _repository.getTransactions();
}
