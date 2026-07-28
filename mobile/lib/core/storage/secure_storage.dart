import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Persists authentication material in platform-backed secure storage.
///
/// Access and refresh tokens are bearer credentials: anyone holding them can act
/// as the user. They therefore go to the iOS Keychain and Android
/// EncryptedSharedPreferences, never to SharedPreferences or a file.
class SecureStorage {
  SecureStorage([FlutterSecureStorage? storage])
      : _storage = storage ??
            const FlutterSecureStorage(
              aOptions: AndroidOptions(encryptedSharedPreferences: true),
              iOptions: IOSOptions(
                // Tokens are unavailable until the device has been unlocked once
                // after boot, and are never synced to iCloud or a backup.
                accessibility: KeychainAccessibility.first_unlock_this_device,
              ),
            );

  final FlutterSecureStorage _storage;

  static const String _accessTokenKey = 'access_token';
  static const String _refreshTokenKey = 'refresh_token';
  static const String _tenantSlugKey = 'tenant_slug';
  static const String _expiresAtKey = 'access_token_expires_at';

  Future<void> saveTokens({
    required String accessToken,
    required String refreshToken,
    required Duration expiresIn,
  }) async {
    final DateTime expiresAt = DateTime.now().toUtc().add(expiresIn);
    await Future.wait(<Future<void>>[
      _storage.write(key: _accessTokenKey, value: accessToken),
      _storage.write(key: _refreshTokenKey, value: refreshToken),
      _storage.write(key: _expiresAtKey, value: expiresAt.toIso8601String()),
    ]);
  }

  Future<String?> readAccessToken() => _storage.read(key: _accessTokenKey);

  Future<String?> readRefreshToken() => _storage.read(key: _refreshTokenKey);

  Future<DateTime?> readAccessTokenExpiry() async {
    final String? raw = await _storage.read(key: _expiresAtKey);
    return raw == null ? null : DateTime.tryParse(raw);
  }

  /// The organisation the user last signed in to, so the login screen can
  /// pre-fill it. Not sensitive, but kept alongside the tokens for one clear-all.
  Future<void> saveTenantSlug(String slug) => _storage.write(key: _tenantSlugKey, value: slug);

  Future<String?> readTenantSlug() => _storage.read(key: _tenantSlugKey);

  /// Clears credentials on sign-out. The tenant slug is deliberately kept so the
  /// next sign-in does not make the user retype their organisation.
  Future<void> clearTokens() async {
    await Future.wait(<Future<void>>[
      _storage.delete(key: _accessTokenKey),
      _storage.delete(key: _refreshTokenKey),
      _storage.delete(key: _expiresAtKey),
    ]);
  }

  Future<void> clearAll() => _storage.deleteAll();
}
