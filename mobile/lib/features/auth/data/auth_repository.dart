import '../../../core/config/api_endpoints.dart';
import '../../../core/network/api_client.dart';
import '../../../core/storage/secure_storage.dart';
import '../domain/session.dart';

/// All authentication calls, and the only place tokens are written.
///
/// Screens never touch [SecureStorage] directly: a token that is stored in one
/// place and cleared in another is how sessions leak between users on a shared
/// branch device.
class AuthRepository {
  AuthRepository({required ApiClient client, required SecureStorage storage})
      : _client = client,
        _storage = storage;

  final ApiClient _client;
  final SecureStorage _storage;

  /// Fetches an institution's branding before sign-in. Returns null when the
  /// organisation is unknown, which the UI reports as "we could not find that
  /// organisation" without confirming which slugs exist.
  Future<TenantBranding?> lookupTenant(String slug) async {
    try {
      final Map<String, dynamic> json =
          await _client.get(ApiEndpoints.tenantBranding(slug.trim().toLowerCase()));
      return TenantBranding.fromJson(json);
    } catch (_) {
      return null;
    }
  }

  Future<Session> login({
    required String tenantSlug,
    required String username,
    required String password,
  }) async {
    final Map<String, dynamic> json = await _client.post(
      ApiEndpoints.login,
      body: <String, dynamic>{
        'tenantSlug': tenantSlug.trim().toLowerCase(),
        'username': username.trim(),
        'password': password,
      },
    );
    return _persist(json, tenantSlug: tenantSlug);
  }

  Future<Session> loginPlatformOperator({
    required String username,
    required String password,
  }) async {
    final Map<String, dynamic> json = await _client.post(
      ApiEndpoints.platformLogin,
      body: <String, dynamic>{'username': username.trim(), 'password': password},
    );
    return _persist(json);
  }

  /// Ends the session. The server call is best-effort: if it fails the local
  /// tokens are still cleared, because leaving credentials on the device after
  /// the user asked to sign out is the worse outcome.
  Future<void> logout() async {
    final String? refreshToken = await _storage.readRefreshToken();
    if (refreshToken != null) {
      try {
        await _client.post(
          ApiEndpoints.logout,
          body: <String, dynamic>{'refreshToken': refreshToken},
        );
      } catch (_) {
        // Deliberately ignored; see above.
      }
    }
    await _storage.clearTokens();
  }

  Future<Session> currentUser() async {
    final Map<String, dynamic> json = await _client.get(ApiEndpoints.currentUser);
    return Session.fromJson(json);
  }

  Future<void> changePassword({
    required String currentPassword,
    required String newPassword,
  }) async {
    await _client.post(
      ApiEndpoints.changePassword,
      body: <String, dynamic>{
        'currentPassword': currentPassword,
        'newPassword': newPassword,
      },
    );
    // Changing a password revokes every other session server-side, including
    // this device's refresh token, so the user must sign in again.
    await _storage.clearTokens();
  }

  Future<void> requestPasswordReset({
    required String tenantSlug,
    required String email,
  }) async {
    await _client.post(
      ApiEndpoints.forgotPassword,
      body: <String, dynamic>{
        'tenantSlug': tenantSlug.trim().toLowerCase(),
        'email': email.trim(),
      },
    );
  }

  Future<void> resetPassword({required String token, required String newPassword}) async {
    await _client.post(
      ApiEndpoints.resetPassword,
      body: <String, dynamic>{'token': token, 'newPassword': newPassword},
    );
  }

  Future<bool> hasStoredSession() async => (await _storage.readRefreshToken()) != null;

  Future<String?> lastTenantSlug() => _storage.readTenantSlug();

  Future<Session> _persist(Map<String, dynamic> json, {String? tenantSlug}) async {
    await _storage.saveTokens(
      accessToken: json['accessToken'] as String,
      refreshToken: json['refreshToken'] as String,
      expiresIn: Duration(seconds: (json['expiresInSeconds'] as num).toInt()),
    );
    final String? slug = (json['tenantSlug'] as String?) ?? tenantSlug;
    if (slug != null) {
      await _storage.saveTenantSlug(slug);
    }
    return Session.fromJson(json);
  }
}
