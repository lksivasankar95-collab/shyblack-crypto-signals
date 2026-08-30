import '../entities/user.dart';
import '../repositories/user_repository.dart';

class GetCurrentUser {
  const GetCurrentUser(this._repository);
  final UserRepository _repository;

  Future<User> call() => _repository.getCurrentUser();
}
