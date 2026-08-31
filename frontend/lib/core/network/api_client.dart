import 'package:dio/dio.dart';

import '../constants/api_constants.dart';
import '../../data/datasources/token_local_data_source.dart';

class ApiClient {
  ApiClient({
    required TokenLocalDataSource this._tokens,
    Dio? dio,
  }) : _dio = dio ??
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
           ) {
    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) async {
          if (!_isPublicAuth(options.path)) {
            final token = await _tokens.readAccessToken();
            if (token != null) {
              options.headers['Authorization'] = 'Bearer $token';
            }
          }
          handler.next(options);
        },
      ),
    );
  }

  final TokenLocalDataSource _tokens;
  final Dio _dio;

  Dio get dio => _dio;

  static bool _isPublicAuth(String path) {
    return path.contains('/auth/login') ||
        path.contains('/auth/signup') ||
        path.contains('/auth/refresh') ||
        path.contains('/auth/google');
  }
}
