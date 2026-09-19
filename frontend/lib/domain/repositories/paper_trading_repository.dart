import '../entities/paper_account.dart';
import '../entities/paper_performance.dart';
import '../entities/paper_position.dart';

abstract class PaperTradingRepository {
  Future<PaperAccount> getAccount();
  Future<List<PaperPosition>> listOpenPositions();
  Future<List<PaperPosition>> listHistory();
  Future<PaperPerformance> getPerformance();
  Future<PaperPosition> closePosition(String id);
  Future<PaperAccount> resetAccount();
}
