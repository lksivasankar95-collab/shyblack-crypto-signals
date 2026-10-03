import 'package:dio/dio.dart';

import '../../core/constants/api_constants.dart';
import '../../core/network/api_client.dart';

class LiveTradingRemoteDataSource {
  LiveTradingRemoteDataSource(this._api);

  final ApiClient _api;

  Future<Map<String, dynamic>?> getAccount() async {
    try {
      final res = await _api.dio.get<Map<String, dynamic>>(
        ApiConstants.liveAccount,
      );
      return res.data;
    } on DioException catch (e) {
      if (e.response?.statusCode == 404) return null;
      rethrow;
    }
  }

  Future<Map<String, dynamic>> connect(String exchange) async {
    final res = await _api.dio.post<Map<String, dynamic>>(
      ApiConstants.liveConnection,
      data: {'exchange': exchange},
    );
    return res.data ?? const {};
  }

  Future<Map<String, dynamic>> validate() async {
    final res = await _api.dio.post<Map<String, dynamic>>(
      ApiConstants.liveConnectionValidate,
    );
    return res.data ?? const {};
  }

  Future<Map<String, dynamic>> disconnect() async {
    final res = await _api.dio.delete<Map<String, dynamic>>(
      ApiConstants.liveConnection,
    );
    return res.data ?? const {};
  }

  Future<Map<String, dynamic>> activate(bool acknowledged) async {
    final res = await _api.dio.post<Map<String, dynamic>>(
      ApiConstants.liveActivate,
      data: {'acknowledged': acknowledged},
    );
    return res.data ?? const {};
  }

  Future<Map<String, dynamic>> deactivate() async {
    final res = await _api.dio.post<Map<String, dynamic>>(
      ApiConstants.liveDeactivate,
    );
    return res.data ?? const {};
  }

  Future<Map<String, dynamic>> triggerKillSwitch() async {
    final res = await _api.dio.post<Map<String, dynamic>>(
      ApiConstants.liveKillSwitch,
    );
    return res.data ?? const {};
  }

  Future<Map<String, dynamic>> releaseKillSwitch() async {
    final res = await _api.dio.delete<Map<String, dynamic>>(
      ApiConstants.liveKillSwitch,
    );
    return res.data ?? const {};
  }

  Future<List<Map<String, dynamic>>> listOpenOrders() async {
    final res = await _api.dio.get<List<dynamic>>(ApiConstants.liveOrders);
    return (res.data ?? const []).whereType<Map<String, dynamic>>().toList();
  }

  Future<List<Map<String, dynamic>>> listHistory() async {
    final res = await _api.dio.get<List<dynamic>>(ApiConstants.liveHistory);
    return (res.data ?? const []).whereType<Map<String, dynamic>>().toList();
  }

  Future<Map<String, dynamic>> cancelOrder(String id) async {
    final res = await _api.dio.post<Map<String, dynamic>>(
      ApiConstants.liveCancel(id),
    );
    return res.data ?? const {};
  }

  Future<Map<String, dynamic>> closePosition(String entryOrderId) async {
    final res = await _api.dio.post<Map<String, dynamic>>(
      ApiConstants.liveClosePosition(entryOrderId),
    );
    return res.data ?? const {};
  }

  Future<Map<String, dynamic>> getPerformance() async {
    final res = await _api.dio.get<Map<String, dynamic>>(
      ApiConstants.livePerformance,
    );
    return res.data ?? const {};
  }
}
