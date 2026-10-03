import '../../domain/entities/futures_account.dart';
import '../../domain/entities/futures_order.dart';
import '../../domain/entities/futures_position.dart';
import '../../domain/repositories/futures_trading_repository.dart';
import '../datasources/futures_trading_remote_data_source.dart';
import '../models/futures_account_model.dart';
import '../models/futures_order_model.dart';
import '../models/futures_position_model.dart';

class FuturesTradingRepositoryImpl implements FuturesTradingRepository {
  FuturesTradingRepositoryImpl(this._remote);

  final FuturesTradingRemoteDataSource _remote;

  @override
  Future<FuturesAccount?> getAccount() async {
    final json = await _remote.getAccount();
    if (json == null || json.isEmpty) return null;
    return FuturesAccountModel.fromJson(json).account;
  }

  @override
  Future<FuturesAccount> connect(String exchange) async =>
      FuturesAccountModel.fromJson(await _remote.connect(exchange)).account;

  @override
  Future<FuturesAccount> validate() async =>
      FuturesAccountModel.fromJson(await _remote.validate()).account;

  @override
  Future<FuturesAccount> disconnect() async =>
      FuturesAccountModel.fromJson(await _remote.disconnect()).account;

  @override
  Future<FuturesAccount> acknowledge(bool flag) async =>
      FuturesAccountModel.fromJson(await _remote.acknowledge(flag)).account;

  @override
  Future<FuturesAccount> activate({required bool acknowledged}) async =>
      FuturesAccountModel.fromJson(await _remote.activate(acknowledged)).account;

  @override
  Future<FuturesAccount> deactivate() async =>
      FuturesAccountModel.fromJson(await _remote.deactivate()).account;

  @override
  Future<FuturesAccount> triggerKillSwitch() async =>
      FuturesAccountModel.fromJson(await _remote.triggerKillSwitch()).account;

  @override
  Future<FuturesAccount> releaseKillSwitch() async =>
      FuturesAccountModel.fromJson(await _remote.releaseKillSwitch()).account;

  @override
  Future<List<FuturesOrder>> listOpenOrders() async =>
      (await _remote.listOpenOrders()).map((r) => FuturesOrderModel.fromJson(r).order).toList();

  @override
  Future<List<FuturesOrder>> listHistory() async =>
      (await _remote.listHistory()).map((r) => FuturesOrderModel.fromJson(r).order).toList();

  @override
  Future<FuturesOrder> cancelOrder(String id) async =>
      FuturesOrderModel.fromJson(await _remote.cancelOrder(id)).order;

  @override
  Future<List<FuturesPosition>> listOpenPositions() async =>
      (await _remote.listOpenPositions()).map((r) => FuturesPositionModel.fromJson(r).position).toList();

  @override
  Future<List<FuturesPosition>> listClosedPositions() async =>
      (await _remote.listClosedPositions()).map((r) => FuturesPositionModel.fromJson(r).position).toList();

  @override
  Future<FuturesOrder> closePosition(String id) async =>
      FuturesOrderModel.fromJson(await _remote.closePosition(id)).order;
}
