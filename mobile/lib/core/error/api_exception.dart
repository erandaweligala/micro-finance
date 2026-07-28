import 'package:dio/dio.dart';

/// A failure the UI can act on.
///
/// Every network error is normalised into this type so screens never have to
/// interpret a raw [DioException] - they show [message], and branch on [code]
/// when a specific outcome needs specific handling.
class ApiException implements Exception {
  const ApiException({
    required this.message,
    this.code,
    this.statusCode,
    this.fieldErrors = const {},
    this.traceId,
  });

  /// Human-readable, safe to show to the user.
  final String message;

  /// Stable machine-readable code from the backend, e.g. `VALIDATION_FAILED`.
  final String? code;

  final int? statusCode;

  /// Field name to message, used to mark individual form fields as invalid.
  final Map<String, String> fieldErrors;

  /// Correlation id, shown on the error screen so support can find the request.
  final String? traceId;

  bool get isUnauthorised => statusCode == 401;

  bool get isForbidden => statusCode == 403;

  bool get isValidation => statusCode == 400 || statusCode == 422;

  bool get isConflict => statusCode == 409;

  /// True when retrying the same request stands a reasonable chance of working.
  bool get isRetryable =>
      statusCode == null || statusCode == 503 || statusCode == 504 || statusCode == 429;

  /// Builds an [ApiException] from a Dio failure, unwrapping the platform's
  /// standard error envelope when the server produced one.
  factory ApiException.fromDio(DioException error) {
    final Response<dynamic>? response = error.response;

    if (error.type == DioExceptionType.connectionTimeout ||
        error.type == DioExceptionType.receiveTimeout ||
        error.type == DioExceptionType.sendTimeout) {
      return const ApiException(
        message: 'The server took too long to respond. Check your connection and try again.',
      );
    }
    if (error.type == DioExceptionType.connectionError) {
      return const ApiException(
        message: 'No connection to the server. Check your network and try again.',
      );
    }

    final dynamic data = response?.data;
    if (data is Map<String, dynamic>) {
      final Map<String, String> fields = <String, String>{};
      final dynamic errors = data['errors'];
      if (errors is List) {
        for (final dynamic entry in errors) {
          if (entry is Map<String, dynamic>) {
            final String? field = entry['field'] as String?;
            final String? message = entry['message'] as String?;
            if (field != null && message != null) {
              fields[field] = message;
            }
          }
        }
      }
      return ApiException(
        message: (data['message'] as String?) ?? _defaultMessage(response?.statusCode),
        code: data['code'] as String?,
        statusCode: response?.statusCode,
        fieldErrors: fields,
        traceId: data['traceId'] as String?,
      );
    }

    return ApiException(
      message: _defaultMessage(response?.statusCode),
      statusCode: response?.statusCode,
    );
  }

  static String _defaultMessage(int? statusCode) {
    switch (statusCode) {
      case 401:
        return 'Your session has expired. Please sign in again.';
      case 403:
        return 'You do not have permission to do that.';
      case 404:
        return 'That record could not be found.';
      case 409:
        return 'That conflicts with existing data.';
      case 429:
        return 'Too many requests. Please wait a moment and try again.';
      case 503:
        return 'The service is temporarily unavailable. Please try again shortly.';
      default:
        return 'Something went wrong. Please try again.';
    }
  }

  @override
  String toString() => 'ApiException($statusCode, $code): $message';
}
