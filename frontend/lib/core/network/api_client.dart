import 'package:dio/dio.dart';

import '../constants/api_constants.dart';

class ApiClient {
  ApiClient({Dio? dio})
      : _dio = dio ??
            Dio(
              BaseOptions(
                baseUrl: ApiConstants.baseUrl,
                connectTimeout: ApiConstants.connectTimeout,
                receiveTimeout: ApiConstants.receiveTimeout,
                headers: const {
                  'Accept': 'application/json',
                  'Content-Type': 'application/json',
                },
              ),
            );

  final Dio _dio;

  Dio get dio => _dio;
}
