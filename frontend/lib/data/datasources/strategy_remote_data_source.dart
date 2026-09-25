import '../../core/constants/api_constants.dart';
import '../../core/network/api_client.dart';
import '../../domain/entities/trading_strategy.dart';
import '../models/trading_strategy_model.dart';

class StrategyRemoteDataSource {
  final ApiClient _client;
  StrategyRemoteDataSource(this._client);

  Future<List<TradingStrategy>> listStrategies(String mode) async {
    final resp = await _client.dio.get<List<dynamic>>(
      ApiConstants.strategies,
      queryParameters: {'mode': mode},
    );
    return (resp.data ?? [])
        .map((e) => TradingStrategyModel.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<TradingStrategy> createStrategy({
    required String name, String? description,
    required String tradingMode, Map<String, dynamic>? config,
  }) async {
    final body = TradingStrategyModel.createToJson(
        name: name, description: description, tradingMode: tradingMode, config: config);
    final resp = await _client.dio.post<Map<String, dynamic>>(ApiConstants.strategies, data: body);
    return TradingStrategyModel.fromJson(resp.data!);
  }

  Future<void> deleteStrategy(String id) async {
    await _client.dio.delete<void>(ApiConstants.strategyById(id));
  }

  Future<ActiveStrategyInfo> getActiveStrategy(String mode) async {
    final resp = await _client.dio.get<Map<String, dynamic>>(
      ApiConstants.strategiesActive,
      queryParameters: {'mode': mode},
    );
    return TradingStrategyModel.activeFromJson(resp.data!);
  }

  Future<ActiveStrategyInfo> setActiveStrategy(String tradingMode, String strategyId) async {
    final body = TradingStrategyModel.setActiveToJson(tradingMode, strategyId);
    final resp = await _client.dio.post<Map<String, dynamic>>(ApiConstants.strategiesActive, data: body);
    return TradingStrategyModel.activeFromJson(resp.data!);
  }
}
