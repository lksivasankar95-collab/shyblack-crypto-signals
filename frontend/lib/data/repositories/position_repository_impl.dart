import '../../domain/entities/position.dart';
import '../../domain/repositories/position_repository.dart';
import '../datasources/position_remote_data_source.dart';

class PositionRepositoryImpl implements PositionRepository {
  PositionRepositoryImpl(this._remote);
  final PositionRemoteDataSource _remote;

  @override
  Future<List<Position>> getPositions() async {
    final models = await _remote.getPositions();
    return models.map((model) => model.toEntity()).toList();
  }
}
