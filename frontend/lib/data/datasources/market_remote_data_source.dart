import 'package:dio/dio.dart';

import '../../core/constants/api_constants.dart';
import '../../core/error/api_exception_mapper.dart';
import '../../core/network/api_client.dart';
import '../../domain/entities/app_settings.dart';
import '../models/market_ticker_model.dart';

class MarketRemoteDataSource {
  MarketRemoteDataSource(this._apiClient);
  final ApiClient _apiClient;

  Future<MarketSnapshotModel> getMarkets(TradingMode mode) =>
      _get(ApiConstants.markets, mode);

  Future<MarketSnapshotModel> getGainers(TradingMode mode) =>
      _get(ApiConstants.marketsGainers, mode);

  Future<MarketSnapshotModel> getLosers(TradingMode mode) =>
      _get(ApiConstants.marketsLosers, mode);

  Future<MarketSnapshotModel> _get(String path, TradingMode mode) async {
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
