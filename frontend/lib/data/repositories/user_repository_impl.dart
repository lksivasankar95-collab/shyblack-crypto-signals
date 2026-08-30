import '../../domain/entities/user.dart';
import '../../domain/repositories/user_repository.dart';
import '../datasources/user_remote_data_source.dart';

class UserRepositoryImpl implements UserRepository {
  UserRepositoryImpl(this._remote);
  final UserRemoteDataSource _remote;

  @override
  Future<List<User>> getUsers() async {
    final models = await _remote.getUsers();
    return models.map((model) => model.toEntity()).toList();
  }

  @override
  Future<User> getCurrentUser() async {
    final model = await _remote.getCurrentUser();
    return model.toEntity();
  }
}
