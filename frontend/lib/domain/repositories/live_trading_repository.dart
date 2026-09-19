import '../entities/live_account.dart';
import '../entities/live_order.dart';
import '../entities/live_performance.dart';

abstract class LiveTradingRepository {
  Future<LiveAccount?> getAccount();
  Future<LiveAccount> connect(LiveExchange exchange);
  Future<LiveAccount> validate();
  Future<LiveAccount> disconnect();
  Future<LiveAccount> activate({required bool acknowledged});
  Future<LiveAccount> deactivate();
  Future<LiveAccount> triggerKillSwitch();
  Future<LiveAccount> releaseKillSwitch();

  Future<List<LiveOrder>> listOpenOrders();
  Future<List<LiveOrder>> listHistory();
  Future<LiveOrder> cancelOrder(String id);
  Future<LiveOrder> closePosition(String entryOrderId);
  Future<LivePerformance> getPerformance();
}
