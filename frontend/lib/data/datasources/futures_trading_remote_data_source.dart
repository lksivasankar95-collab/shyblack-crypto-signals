import 'package:dio/dio.dart';

import '../../core/constants/api_constants.dart';
import '../../core/network/api_client.dart';

class FuturesTradingRemoteDataSource {
  FuturesTradingRemoteDataSource(this._api);

  final ApiClient _api;

  Future<Map<String, dynamic>?> getAccount() async {
    try {
      final r = await _api.dio.get<Map<String, dynamic>>('/api${ApiConstants.futuresAccount}');
      return r.data;
    } on DioException catch (e) {
      if (e.response?.statusCode == 404) return null;
      rethrow;
    }
  }

  Future<Map<String, dynamic>> connect(String exchange) async {
    final r = await _api.dio.post<Map<String, dynamic>>(
      '/api${ApiConstants.futuresConnection}',
      data: {'exchange': exchange},
    );
    return r.data ?? const {};
  }

  Future<Map<String, dynamic>> validate() async {
    final r = await _api.dio.post<Map<String, dynamic>>('/api${ApiConstants.futuresConnectionValidate}');
    return r.data ?? const {};
  }

  Future<Map<String, dynamic>> disconnect() async {
    final r = await _api.dio.delete<Map<String, dynamic>>('/api${ApiConstants.futuresConnection}');
    return r.data ?? const {};
  }

  Future<Map<String, dynamic>> acknowledge(bool flag) async {
    final r = await _api.dio.post<Map<String, dynamic>>(
      '/api${ApiConstants.futuresAcknowledge}', data: {'acknowledged': flag});
    return r.data ?? const {};
  }

  Future<Map<String, dynamic>> activate(bool ack) async {
    final r = await _api.dio.post<Map<String, dynamic>>(
      '/api${ApiConstants.futuresActivate}', data: {'acknowledged': ack});
    return r.data ?? const {};
  }

  Future<Map<String, dynamic>> deactivate() async {
    final r = await _api.dio.post<Map<String, dynamic>>('/api${ApiConstants.futuresDeactivate}');
    return r.data ?? const {};
  }

  Future<Map<String, dynamic>> triggerKillSwitch() async {
    final r = await _api.dio.post<Map<String, dynamic>>('/api${ApiConstants.futuresKillSwitch}');
    return r.data ?? const {};
  }

  Future<Map<String, dynamic>> releaseKillSwitch() async {
    final r = await _api.dio.delete<Map<String, dynamic>>('/api${ApiConstants.futuresKillSwitch}');
    return r.data ?? const {};
  }

  Future<List<Map<String, dynamic>>> listOpenOrders() async {
    final r = await _api.dio.get<List<dynamic>>('/api${ApiConstants.futuresOrders}');
    return (r.data ?? const []).whereType<Map<String, dynamic>>().toList();
  }

  Future<List<Map<String, dynamic>>> listHistory() async {
    final r = await _api.dio.get<List<dynamic>>('/api${ApiConstants.futuresHistory}');
    return (r.data ?? const []).whereType<Map<String, dynamic>>().toList();
  }

  Future<Map<String, dynamic>> cancelOrder(String id) async {
    final r = await _api.dio.post<Map<String, dynamic>>('/api${ApiConstants.futuresCancel(id)}');
    return r.data ?? const {};
  }

  Future<List<Map<String, dynamic>>> listOpenPositions() async {
    final r = await _api.dio.get<List<dynamic>>('/api${ApiConstants.futuresPositions}');
    return (r.data ?? const []).whereType<Map<String, dynamic>>().toList();
  }

  Future<List<Map<String, dynamic>>> listClosedPositions() async {
    final r = await _api.dio.get<List<dynamic>>('/api${ApiConstants.futuresPositionsHistory}');
    return (r.data ?? const []).whereType<Map<String, dynamic>>().toList();
  }

  Future<Map<String, dynamic>> closePosition(String id) async {
    final r = await _api.dio.post<Map<String, dynamic>>('/api${ApiConstants.futuresClosePosition(id)}');
    return r.data ?? const {};
  }
}
