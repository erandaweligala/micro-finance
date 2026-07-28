import 'dart:async';

import 'package:dio/dio.dart';

import '../config/api_endpoints.dart';
import '../config/app_config.dart';
import '../storage/secure_storage.dart';

/// Attaches the access token to every request and refreshes it transparently.
///
/// Two problems this solves that a naive interceptor does not:
///
/// * **Refresh stampede.** When several requests fire at once with an expired
///   token, only the first performs the refresh; the rest await the same future.
///   Without this, each would spend its own refresh token, and the backend's
///   rotation detection would treat the reuse as a stolen token and revoke the
///   whole session.
/// * **Retry loops.** A 401 on the refresh call itself never triggers another
///   refresh; it signs the user out once.
class AuthInterceptor extends Interceptor {
  AuthInterceptor({
    required SecureStorage storage,
    required Dio refreshClient,
    required Future<void> Function() onSessionExpired,
  })  : _storage = storage,
        _refreshClient = refreshClient,
        _onSessionExpired = onSessionExpired;

  final SecureStorage _storage;

  /// A separate Dio instance with no interceptors, so refreshing cannot recurse.
  final Dio _refreshClient;

  final Future<void> Function() _onSessionExpired;

  /// In-flight refresh, shared by every request that arrives while it runs.
  Future<String?>? _pendingRefresh;

  /// Paths that must never carry a token or trigger a refresh.
  static const Set<String> _anonymousPaths = <String>{
    ApiEndpoints.login,
    ApiEndpoints.platformLogin,
    ApiEndpoints.refresh,
    ApiEndpoints.forgotPassword,
    ApiEndpoints.resetPassword,
  };

  @override
  Future<void> onRequest(RequestOptions options, RequestInterceptorHandler handler) async {
    if (_isAnonymous(options.path)) {
      return handler.next(options);
    }

    String? token = await _storage.readAccessToken();
    final DateTime? expiresAt = await _storage.readAccessTokenExpiry();

    // Refresh proactively: a token that expires mid-flight would fail a request
    // the user has already committed to, which for a payment is worse than a
    // slightly early refresh.
    final bool expiringSoon = expiresAt != null &&
        DateTime.now().toUtc().isAfter(expiresAt.subtract(AppConfig.tokenRefreshLeeway));
    if (token != null && expiringSoon) {
      token = await _refreshAccessToken();
    }

    if (token != null) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    return handler.next(options);
  }

  @override
  Future<void> onError(DioException err, ErrorInterceptorHandler handler) async {
    final RequestOptions request = err.requestOptions;

    final bool shouldAttemptRefresh = err.response?.statusCode == 401 &&
        !_isAnonymous(request.path) &&
        request.extra['retried'] != true;

    if (!shouldAttemptRefresh) {
      return handler.next(err);
    }

    final String? token = await _refreshAccessToken();
    if (token == null) {
      await _onSessionExpired();
      return handler.next(err);
    }

    try {
      // Replay the original request exactly once with the new token.
      request.headers['Authorization'] = 'Bearer $token';
      request.extra['retried'] = true;
      final Response<dynamic> response = await _refreshClient.fetch<dynamic>(request);
      return handler.resolve(response);
    } on DioException catch (retryError) {
      return handler.next(retryError);
    }
  }

  /// Refreshes the access token, collapsing concurrent callers onto one request.
  Future<String?> _refreshAccessToken() {
    return _pendingRefresh ??= _performRefresh().whenComplete(() {
      _pendingRefresh = null;
    });
  }

  Future<String?> _performRefresh() async {
    final String? refreshToken = await _storage.readRefreshToken();
    if (refreshToken == null) {
      return null;
    }
    try {
      final Response<Map<String, dynamic>> response =
          await _refreshClient.post<Map<String, dynamic>>(
        ApiEndpoints.refresh,
        data: <String, dynamic>{'refreshToken': refreshToken},
      );
      final Map<String, dynamic>? body = response.data;
      if (body == null) {
        return null;
      }
      final String accessToken = body['accessToken'] as String;
      await _storage.saveTokens(
        accessToken: accessToken,
        refreshToken: body['refreshToken'] as String,
        expiresIn: Duration(seconds: (body['expiresInSeconds'] as num).toInt()),
      );
      return accessToken;
    } on DioException {
      // The refresh token is spent, revoked or rejected: the session is over.
      await _storage.clearTokens();
      return null;
    }
  }

  bool _isAnonymous(String path) =>
      _anonymousPaths.any((String anonymous) => path.endsWith(anonymous));
}
