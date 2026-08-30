import '../../../core/constants/api_constants.dart';
import '../../../core/network/api_client.dart';
import '../models/transaction_model.dart';

class TransactionRemoteDataSource {
  TransactionRemoteDataSource(this._apiClient);
  final ApiClient _apiClient;

  Future<List<TransactionModel>> getTransactions() async {
    final response = await _apiClient.dio.get<List<dynamic>>(ApiConstants.transactions);
    return (response.data ?? [])
        .map((item) => TransactionModel.fromJson(item as Map<String, dynamic>))
        .toList();
  }
}
