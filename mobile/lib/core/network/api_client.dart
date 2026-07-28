import 'dart:math';

import 'package:dio/dio.dart';

import '../config/app_config.dart';
import '../error/api_exception.dart';
import '../storage/secure_storage.dart';
import 'auth_interceptor.dart';

/// The app's HTTP client.
///
/// Wraps Dio so that callers work with typed results and [ApiException] rather
/// than with Dio's own error model, and so that cross-cutting concerns -
/// authentication, correlation ids, idempotency keys - are applied in exactly
/// one place.
class ApiClient {
  ApiClient({
    required SecureStorage storage,
    required Future<void> Function() onSessionExpired,
    Dio? dio,
  }) : _dio = dio ?? Dio(_baseOptions()) {
    final Dio refreshClient = Dio(_baseOptions());
    _dio.interceptors.add(
      AuthInterceptor(
        storage: storage,
        refreshClient: refreshClient,
        onSessionExpired: onSessionExpired,
      ),
    );
    if (!AppConfig.isProduction) {
      // Bodies are logged only outside production: requests carry passwords,
      // identity numbers and payment details.
      _dio.interceptors.add(
        LogInterceptor(requestBody: true, responseBody: true, requestHeader: false),
      );
    }
  }

  final Dio _dio;

  static BaseOptions _baseOptions() => BaseOptions(
        baseUrl: AppConfig.apiBaseUrl,
        connectTimeout: AppConfig.connectTimeout,
        receiveTimeout: AppConfig.receiveTimeout,
        headers: <String, String>{'Content-Type': 'application/json'},
        // Non-2xx is handled by our own error mapping, not by Dio throwing on
        // some statuses and not others.
        validateStatus: (int? status) => status != null && status < 400,
      );

  Future<Map<String, dynamic>> get(
    String path, {
    Map<String, dynamic>? queryParameters,
  }) async {
    return _wrap(() async {
      final Response<Map<String, dynamic>> response = await _dio.get<Map<String, dynamic>>(
        path,
        queryParameters: _clean(queryParameters),
      );
      return response.data ?? <String, dynamic>{};
    });
  }

  Future<List<dynamic>> getList(
    String path, {
    Map<String, dynamic>? queryParameters,
  }) async {
    return _wrap(() async {
      final Response<List<dynamic>> response = await _dio.get<List<dynamic>>(
        path,
        queryParameters: _clean(queryParameters),
      );
      return response.data ?? <dynamic>[];
    });
  }

  Future<Map<String, dynamic>> post(
    String path, {
    Object? body,
    Map<String, dynamic>? queryParameters,
    String? idempotencyKey,
  }) async {
    return _wrap(() async {
      final Response<Map<String, dynamic>> response = await _dio.post<Map<String, dynamic>>(
        path,
        data: body,
        queryParameters: _clean(queryParameters),
        options: idempotencyKey == null
            ? null
            : Options(headers: <String, String>{'Idempotency-Key': idempotencyKey}),
      );
      return response.data ?? <String, dynamic>{};
    });
  }

  Future<Map<String, dynamic>> put(String path, {Object? body}) async {
    return _wrap(() async {
      final Response<Map<String, dynamic>> response =
          await _dio.put<Map<String, dynamic>>(path, data: body);
      return response.data ?? <String, dynamic>{};
    });
  }

  Future<void> delete(String path) async {
    await _wrap(() => _dio.delete<dynamic>(path));
  }

  /// Generates an idempotency key for a money-moving request.
  ///
  /// The key must be created *before* the first attempt and reused for every
  /// retry of that same logical payment - that is the whole point. Callers hold
  /// it in their form state, so a retry after a timeout carries the original key.
  static String newIdempotencyKey() {
    final Random random = Random.secure();
    final List<int> bytes = List<int>.generate(16, (_) => random.nextInt(256));
    final String hex =
        bytes.map((int byte) => byte.toRadixString(16).padLeft(2, '0')).join();
    return '${DateTime.now().millisecondsSinceEpoch}-$hex';
  }

  Future<T> _wrap<T>(Future<T> Function() request) async {
    try {
      return await request();
    } on DioException catch (error) {
      throw ApiException.fromDio(error);
    }
  }

  /// Drops null and blank query parameters so the URL stays clean and the
  /// backend applies its own defaults rather than receiving empty filters.
  Map<String, dynamic>? _clean(Map<String, dynamic>? parameters) {
    if (parameters == null) {
      return null;
    }
    final Map<String, dynamic> cleaned = <String, dynamic>{};
    parameters.forEach((String key, dynamic value) {
      if (value == null) {
        return;
      }
      if (value is String && value.trim().isEmpty) {
        return;
      }
      cleaned[key] = value;
    });
    return cleaned.isEmpty ? null : cleaned;
  }
}
