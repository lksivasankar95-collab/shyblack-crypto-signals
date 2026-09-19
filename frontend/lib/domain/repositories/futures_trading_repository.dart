import '../entities/futures_account.dart';
import '../entities/futures_order.dart';
import '../entities/futures_position.dart';

abstract class FuturesTradingRepository {
  Future<FuturesAccount?> getAccount();
  Future<FuturesAccount> connect(String exchange);
  Future<FuturesAccount> validate();
  Future<FuturesAccount> disconnect();
  Future<FuturesAccount> acknowledge(bool flag);
  Future<FuturesAccount> activate({required bool acknowledged});
  Future<FuturesAccount> deactivate();
  Future<FuturesAccount> triggerKillSwitch();
  Future<FuturesAccount> releaseKillSwitch();

  Future<List<FuturesOrder>> listOpenOrders();
  Future<List<FuturesOrder>> listHistory();
  Future<FuturesOrder> cancelOrder(String id);

  Future<List<FuturesPosition>> listOpenPositions();
  Future<List<FuturesPosition>> listClosedPositions();
  Future<FuturesOrder> closePosition(String positionId);
}
