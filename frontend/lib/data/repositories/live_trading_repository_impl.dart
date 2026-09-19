import '../../domain/entities/live_account.dart';
import '../../domain/entities/live_order.dart';
import '../../domain/entities/live_performance.dart';
import '../../domain/repositories/live_trading_repository.dart';
import '../datasources/live_trading_remote_data_source.dart';
import '../models/live_account_model.dart';
import '../models/live_order_model.dart';
import '../models/live_performance_model.dart';

class LiveTradingRepositoryImpl implements LiveTradingRepository {
  LiveTradingRepositoryImpl(this._remote);

  final LiveTradingRemoteDataSource _remote;

  @override
  Future<LiveAccount?> getAccount() async {
    final json = await _remote.getAccount();
    if (json == null || json.isEmpty) return null;
    return LiveAccountModel.fromJson(json).account;
  }

  @override
  Future<LiveAccount> connect(LiveExchange exchange) async =>
      LiveAccountModel.fromJson(await _remote.connect(_exchangeName(exchange))).account;

  @override
  Future<LiveAccount> validate() async =>
      LiveAccountModel.fromJson(await _remote.validate()).account;

  @override
  Future<LiveAccount> disconnect() async =>
      LiveAccountModel.fromJson(await _remote.disconnect()).account;

  @override
  Future<LiveAccount> activate({required bool acknowledged}) async =>
      LiveAccountModel.fromJson(await _remote.activate(acknowledged)).account;

  @override
  Future<LiveAccount> deactivate() async =>
      LiveAccountModel.fromJson(await _remote.deactivate()).account;

  @override
  Future<LiveAccount> triggerKillSwitch() async =>
      LiveAccountModel.fromJson(await _remote.triggerKillSwitch()).account;

  @override
  Future<LiveAccount> releaseKillSwitch() async =>
      LiveAccountModel.fromJson(await _remote.releaseKillSwitch()).account;

  @override
  Future<List<LiveOrder>> listOpenOrders() async {
    final rows = await _remote.listOpenOrders();
    return rows.map((r) => LiveOrderModel.fromJson(r).order).toList();
  }

  @override
  Future<List<LiveOrder>> listHistory() async {
    final rows = await _remote.listHistory();
    return rows.map((r) => LiveOrderModel.fromJson(r).order).toList();
  }

  @override
  Future<LiveOrder> cancelOrder(String id) async =>
      LiveOrderModel.fromJson(await _remote.cancelOrder(id)).order;

  @override
  Future<LivePerformance> getPerformance() async =>
      LivePerformanceModel.fromJson(await _remote.getPerformance()).performance;

  static String _exchangeName(LiveExchange exchange) => switch (exchange) {
        LiveExchange.binance => 'BINANCE',
        LiveExchange.bybit => 'BYBIT',
        LiveExchange.okx => 'OKX',
        LiveExchange.coinbase => 'COINBASE',
      };
}
