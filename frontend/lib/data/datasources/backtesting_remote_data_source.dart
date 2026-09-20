import '../../core/constants/api_constants.dart';
import '../../core/network/api_client.dart';

class BacktestingRemoteDataSource {
  BacktestingRemoteDataSource(this._api);

  final ApiClient _api;

  Future<List<Map<String, dynamic>>> listRuns() async {
    final r = await _api.dio.get<List<dynamic>>('/api${ApiConstants.backtests}');
    return (r.data ?? const []).whereType<Map<String, dynamic>>().toList();
  }

  Future<Map<String, dynamic>> getRun(String id) async {
    final r = await _api.dio.get<Map<String, dynamic>>('/api${ApiConstants.backtest(id)}');
    return r.data ?? const {};
  }

  Future<Map<String, dynamic>> startRun(Map<String, dynamic> body) async {
    final r = await _api.dio.post<Map<String, dynamic>>(
      '/api${ApiConstants.backtests}',
      data: body,
    );
    return r.data ?? const {};
  }

  Future<List<Map<String, dynamic>>> getTrades(String id) async {
    final r = await _api.dio.get<List<dynamic>>('/api${ApiConstants.backtestTrades(id)}');
    return (r.data ?? const []).whereType<Map<String, dynamic>>().toList();
  }

  Future<List<Map<String, dynamic>>> getEquity(String id) async {
    final r = await _api.dio.get<List<dynamic>>('/api${ApiConstants.backtestEquity(id)}');
    return (r.data ?? const []).whereType<Map<String, dynamic>>().toList();
  }

  Future<Map<String, dynamic>> cancelRun(String id) async {
    final r = await _api.dio.post<Map<String, dynamic>>('/api${ApiConstants.backtestCancel(id)}');
    return r.data ?? const {};
  }

  Future<void> deleteRun(String id) async {
    await _api.dio.delete<Map<String, dynamic>>('/api${ApiConstants.backtest(id)}');
  }

  Future<List<Map<String, dynamic>>> listStrategies() async {
    final r = await _api.dio.get<List<dynamic>>('/api${ApiConstants.backtestStrategies}');
    return (r.data ?? const []).whereType<Map<String, dynamic>>().toList();
  }
}
