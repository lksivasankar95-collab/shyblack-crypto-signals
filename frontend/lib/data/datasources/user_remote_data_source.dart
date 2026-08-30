import '../../../core/constants/api_constants.dart';
import '../../../core/network/api_client.dart';
import '../models/user_model.dart';

class UserRemoteDataSource {
  UserRemoteDataSource(this._apiClient);
  final ApiClient _apiClient;

  Future<List<UserModel>> getUsers() async {
    final response = await _apiClient.dio.get<List<dynamic>>(ApiConstants.users);
    return (response.data ?? [])
        .map((item) => UserModel.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  Future<UserModel> getCurrentUser() async {
    final response = await _apiClient.dio.get<Map<String, dynamic>>(
      '${ApiConstants.users}/me',
    );
    return UserModel.fromJson(response.data!);
  }
}
