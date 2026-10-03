import 'package:dio/dio.dart';

import '../../core/constants/api_constants.dart';
import '../../core/error/api_exception_mapper.dart';
import '../../core/error/auth_exception.dart';
import '../../core/network/api_client.dart';
import '../../domain/entities/app_settings.dart';
import '../models/market_ticker_model.dart';

class MarketRemoteDataSource {
  MarketRemoteDataSource(this._apiClient);
  final ApiClient _apiClient;

  Future<MarketSnapshotModel> getMarkets(TradingMode mode) =>
      _getList(ApiConstants.markets, mode);

  Future<MarketSnapshotModel> getGainers(TradingMode mode) =>
      _getList(ApiConstants.marketsGainers, mode);

  Future<MarketSnapshotModel> getLosers(TradingMode mode) =>
      _getList(ApiConstants.marketsLosers, mode);

  Future<MarketTickerModel> getTicker(String symbol, TradingMode mode) async {
    try {
      final response = await _apiClient.dio.get<Map<String, dynamic>>(
        ApiConstants.marketSymbol(symbol),
        queryParameters: {'mode': mode.apiParam},
      );
      final data = response.data;
      if (data == null) {
        throw const AuthException('Empty ticker response');
      }
      return MarketTickerModel.fromJson(data);
    } on DioException catch (error) {
      throw ApiExceptionMapper.fromDio(error);
    }
  }

  Future<List<KlineCandleModel>> getKlines({
    required String symbol,
    required String interval,
    required int limit,
    required TradingMode mode,
  }) async {
    try {
      final response = await _apiClient.dio.get<List<dynamic>>(
        ApiConstants.marketKlines(symbol),
        queryParameters: {
          'interval': interval,
          'limit': limit,
          'mode': mode.apiParam,
        },
      );
      return (response.data ?? [])
          .whereType<Map>()
          .map((item) => KlineCandleModel.fromJson(Map<String, dynamic>.from(item)))
          .toList();
    } on DioException catch (error) {
      throw ApiExceptionMapper.fromDio(error);
    }
  }

  Future<MarketSnapshotModel> _getList(String path, TradingMode mode) async {
    try {
      final response = await _apiClient.dio.get<dynamic>(
        path,
        queryParameters: {'mode': mode.apiParam},
      );
      return MarketSnapshotModel.fromJson(response.data, fallbackMode: mode);
    } on DioException catch (error) {
      throw ApiExceptionMapper.fromDio(error);
    }
  }
}
