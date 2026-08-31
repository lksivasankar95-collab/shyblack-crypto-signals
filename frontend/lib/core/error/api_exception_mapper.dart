import 'package:dio/dio.dart';

import 'auth_exception.dart';

abstract final class ApiExceptionMapper {
  static AuthException fromDio(DioException error) {
    if (error.type == DioExceptionType.connectionTimeout ||
        error.type == DioExceptionType.receiveTimeout ||
        error.type == DioExceptionType.connectionError) {
      return const AuthException('Unable to reach the server', unreachable: true);
    }

    final status = error.response?.statusCode;
    if (status == 401 || status == 403) {
      return const AuthException('Invalid credentials', unauthorized: true);
    }
    if (status == 409) {
      return const AuthException('Email already exists');
    }

    final data = error.response?.data;
    if (data is Map<String, dynamic>) {
      final details = data['details'];
      if (status == 400 && details is List && details.isNotEmpty) {
        return AuthException(details.map((item) => item.toString()).join('\n'));
      }
      final message = data['message']?.toString();
      if (message != null && message.isNotEmpty) {
        return AuthException(message, unauthorized: status == 401);
      }
    }

    return const AuthException('Something went wrong. Please try again.');
  }
}
