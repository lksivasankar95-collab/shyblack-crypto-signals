import 'package:dio/dio.dart';

import '../../core/constants/api_constants.dart';
import '../../core/error/api_exception_mapper.dart';
import '../../core/error/auth_exception.dart';
import '../../core/network/api_client.dart';
import '../models/auth_tokens_model.dart';

class AuthRemoteDataSource {
  AuthRemoteDataSource(this._apiClient);

  final ApiClient _apiClient;

  Future<AuthTokensModel> login({
    required String email,
    required String password,
  }) async {
    try {
      final response = await _apiClient.dio.post<Map<String, dynamic>>(
        ApiConstants.authLogin,
        data: {'email': email, 'password': password},
      );
      return AuthTokensModel.fromJson(response.data!);
    } on DioException catch (error) {
      throw ApiExceptionMapper.fromDio(error);
    }
  }

  Future<AuthTokensModel> loginWithGoogle({required String idToken}) async {
    try {
      final response = await _apiClient.dio.post<Map<String, dynamic>>(
        ApiConstants.authGoogle,
        data: {'idToken': idToken},
      );
      return AuthTokensModel.fromJson(response.data!);
    } on DioException catch (error) {
      throw ApiExceptionMapper.fromDio(error);
    }
  }

  Future<void> signup({
    required String fullName,
    required String email,
    required String password,
  }) async {
    try {
      await _apiClient.dio.post<Map<String, dynamic>>(
        ApiConstants.authSignup,
        data: {
          'fullName': fullName,
          'email': email,
          'password': password,
        },
      );
    } on DioException catch (error) {
      throw ApiExceptionMapper.fromDio(error);
    } on AuthException {
      rethrow;
    }
  }

  Future<AccessTokenModel> refresh({required String refreshToken}) async {
    try {
      final response = await _apiClient.dio.post<Map<String, dynamic>>(
        ApiConstants.authRefresh,
        data: {'refreshToken': refreshToken},
      );
      return AccessTokenModel.fromJson(response.data!);
    } on DioException catch (error) {
      throw ApiExceptionMapper.fromDio(error);
    }
  }

  Future<void> getCurrentUser() async {
    try {
      await _apiClient.dio.get<Map<String, dynamic>>(ApiConstants.usersMe);
    } on DioException catch (error) {
      throw ApiExceptionMapper.fromDio(error);
    }
  }
}
