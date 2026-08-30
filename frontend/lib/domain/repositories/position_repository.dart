import '../entities/position.dart';

abstract class PositionRepository {
  Future<List<Position>> getPositions();
}
