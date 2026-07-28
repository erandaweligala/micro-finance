import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../features/auth/data/auth_repository.dart';
import '../features/auth/domain/session.dart';
import 'network/api_client.dart';
import 'storage/secure_storage.dart';

/// Application-wide dependency wiring.
///
/// Everything is a provider so that a widget test can override any layer -
/// swap the [ApiClient] for a fake and a whole screen becomes testable without
/// a running backend.
final Provider<SecureStorage> secureStorageProvider =
    Provider<SecureStorage>((Ref ref) => SecureStorage());

/// The HTTP client. It calls back into [sessionProvider] when a refresh fails,
/// which is what turns an expired session into a redirect to the sign-in screen.
final Provider<ApiClient> apiClientProvider = Provider<ApiClient>((Ref ref) {
  final SecureStorage storage = ref.watch(secureStorageProvider);
  return ApiClient(
    storage: storage,
    onSessionExpired: () async {
      await ref.read(sessionProvider.notifier).handleSessionExpired();
    },
  );
});

final Provider<AuthRepository> authRepositoryProvider = Provider<AuthRepository>((Ref ref) {
  return AuthRepository(
    client: ref.watch(apiClientProvider),
    storage: ref.watch(secureStorageProvider),
  );
});

/// The authentication state machine that the router listens to.
sealed class SessionState {
  const SessionState();
}

/// Before the stored token has been checked. The router shows a splash rather
/// than briefly flashing the sign-in screen at an already-authenticated user.
class SessionUnknown extends SessionState {
  const SessionUnknown();
}

class SessionUnauthenticated extends SessionState {
  const SessionUnauthenticated({this.message});

  /// Explains an involuntary sign-out, e.g. "your session has expired".
  final String? message;
}

class SessionAuthenticated extends SessionState {
  const SessionAuthenticated(this.session);

  final Session session;
}

/// Owns the signed-in user for the whole app.
class SessionController extends Notifier<SessionState> {
  @override
  SessionState build() {
    // Resolve the stored session immediately, without blocking the first frame.
    Future<void>.microtask(restore);
    return const SessionUnknown();
  }

  AuthRepository get _repository => ref.read(authRepositoryProvider);

  /// Restores a session from the securely stored refresh token on cold start.
  Future<void> restore() async {
    try {
      if (!await _repository.hasStoredSession()) {
        state = const SessionUnauthenticated();
        return;
      }
      // Fetching the profile also proves the token is still valid; if it has been
      // revoked server-side, this fails and the user is signed out cleanly.
      final Session session = await _repository.currentUser();
      state = SessionAuthenticated(session);
    } catch (error) {
      debugPrint('Session restore failed: $error');
      state = const SessionUnauthenticated();
    }
  }

  Future<void> signIn({
    required String tenantSlug,
    required String username,
    required String password,
  }) async {
    final Session session = await _repository.login(
      tenantSlug: tenantSlug,
      username: username,
      password: password,
    );
    state = SessionAuthenticated(session);
  }

  Future<void> signInAsPlatformOperator({
    required String username,
    required String password,
  }) async {
    final Session session = await _repository.loginPlatformOperator(
      username: username,
      password: password,
    );
    state = SessionAuthenticated(session);
  }

  Future<void> signOut() async {
    await _repository.logout();
    state = const SessionUnauthenticated();
  }

  /// Called by the HTTP layer when the refresh token is spent or rejected.
  Future<void> handleSessionExpired() async {
    state = const SessionUnauthenticated(
      message: 'Your session has expired. Please sign in again.',
    );
  }

  /// After a password change every session is revoked, so the user signs in again.
  Future<void> onPasswordChanged() async {
    state = const SessionUnauthenticated(
      message: 'Your password was changed. Please sign in with your new password.',
    );
  }
}

final NotifierProvider<SessionController, SessionState> sessionProvider =
    NotifierProvider<SessionController, SessionState>(SessionController.new);

/// The signed-in user, or null. Most screens want this rather than the state machine.
final Provider<Session?> currentSessionProvider = Provider<Session?>((Ref ref) {
  final SessionState state = ref.watch(sessionProvider);
  return state is SessionAuthenticated ? state.session : null;
});

/// The institution's brand colour, applied to the whole app once signed in.
final StateProvider<TenantBranding?> tenantBrandingProvider =
    StateProvider<TenantBranding?>((Ref ref) => null);

/// User-selected theme mode; defaults to following the device.
final StateProvider<ThemeModeSetting> themeModeProvider =
    StateProvider<ThemeModeSetting>((Ref ref) => ThemeModeSetting.system);

enum ThemeModeSetting { system, light, dark }
