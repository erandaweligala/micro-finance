/// Build-time configuration.
///
/// Values come from `--dart-define`, so the same source tree produces a staging
/// build and a production build without a code change and without secrets ever
/// being compiled into the repository.
class AppConfig {
  const AppConfig._();

  /// Base URL of the API gateway. Every request goes through the gateway; the app
  /// never addresses an individual microservice.
  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080',
  );

  static const String environment = String.fromEnvironment(
    'ENVIRONMENT',
    defaultValue: 'local',
  );

  static const Duration connectTimeout = Duration(seconds: 15);
  static const Duration receiveTimeout = Duration(seconds: 30);

  /// Refresh the access token this long before it actually expires, so a request
  /// is never sent with a token that dies in flight.
  static const Duration tokenRefreshLeeway = Duration(minutes: 1);

  static const int defaultPageSize = 20;

  static bool get isProduction => environment == 'production';
}
