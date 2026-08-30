import '../../domain/entities/transaction.dart';
import '../../domain/repositories/transaction_repository.dart';
import '../datasources/transaction_remote_data_source.dart';

class TransactionRepositoryImpl implements TransactionRepository {
  TransactionRepositoryImpl(this._remote);
  final TransactionRemoteDataSource _remote;

  @override
  Future<List<Transaction>> getTransactions() async {
    final models = await _remote.getTransactions();
    return models.map((model) => model.toEntity()).toList();
  }
}
