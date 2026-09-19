import '../../domain/entities/paper_account.dart';
import '../../domain/entities/paper_performance.dart';
import '../../domain/entities/paper_position.dart';
import '../../domain/repositories/paper_trading_repository.dart';
import '../datasources/paper_trading_remote_data_source.dart';
import '../models/paper_account_model.dart';
import '../models/paper_performance_model.dart';
import '../models/paper_position_model.dart';

class PaperTradingRepositoryImpl implements PaperTradingRepository {
  PaperTradingRepositoryImpl(this._remote);

  final PaperTradingRemoteDataSource _remote;

  @override
  Future<PaperAccount> getAccount() async {
    final json = await _remote.getAccount();
    return PaperAccountModel.fromJson(json).account;
  }

  @override
  Future<List<PaperPosition>> listOpenPositions() async {
    final rows = await _remote.listOpenPositions();
    return rows.map((r) => PaperPositionModel.fromJson(r).position).toList();
  }

  @override
  Future<List<PaperPosition>> listHistory() async {
    final rows = await _remote.listHistory();
    return rows.map((r) => PaperPositionModel.fromJson(r).position).toList();
  }

  @override
  Future<PaperPerformance> getPerformance() async {
    final json = await _remote.getPerformance();
    return PaperPerformanceModel.fromJson(json).performance;
  }

  @override
  Future<PaperPosition> closePosition(String id) async {
    final json = await _remote.closePosition(id);
    return PaperPositionModel.fromJson(json).position;
  }

  @override
  Future<PaperAccount> resetAccount() async {
    final json = await _remote.resetAccount();
    return PaperAccountModel.fromJson(json).account;
  }
}
