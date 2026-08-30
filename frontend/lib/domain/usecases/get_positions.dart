import '../entities/position.dart';
import '../repositories/position_repository.dart';

class GetPositions {
  const GetPositions(this._repository);
  final PositionRepository _repository;

  Future<List<Position>> call() => _repository.getPositions();
}
