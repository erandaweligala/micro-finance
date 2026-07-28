import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/error/api_exception.dart';
import '../../../core/providers.dart';
import '../../../core/router/app_router.dart';
import '../../../core/utils/validators.dart';
import '../../../core/widgets/common_widgets.dart';
import '../domain/session.dart';

/// Sign-in.
///
/// When no organisation was selected the screen switches to platform-operator
/// mode, which is the only path that is not tenant-scoped.
class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({this.tenantSlug, super.key});

  final String? tenantSlug;

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final GlobalKey<FormState> _formKey = GlobalKey<FormState>();
  final TextEditingController _usernameController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();

  bool _obscurePassword = true;
  bool _isSubmitting = false;
  String? _error;

  bool get _isPlatformLogin => widget.tenantSlug == null || widget.tenantSlug!.isEmpty;

  @override
  void dispose() {
    _usernameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    FocusScope.of(context).unfocus();
    setState(() {
      _isSubmitting = true;
      _error = null;
    });

    try {
      final SessionController controller = ref.read(sessionProvider.notifier);
      if (_isPlatformLogin) {
        await controller.signInAsPlatformOperator(
          username: _usernameController.text,
          password: _passwordController.text,
        );
      } else {
        await controller.signIn(
          tenantSlug: widget.tenantSlug!,
          username: _usernameController.text,
          password: _passwordController.text,
        );
      }
      // Navigation is handled by the router's redirect once the session changes.
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _error = error.message);
      }
    } finally {
      if (mounted) {
        setState(() => _isSubmitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final TenantBranding? branding = ref.watch(tenantBrandingProvider);

    // An involuntary sign-out (expired session) surfaces its reason here.
    final SessionState sessionState = ref.watch(sessionProvider);
    final String? sessionMessage =
        sessionState is SessionUnauthenticated ? sessionState.message : null;

    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.go(Routes.tenantSelection),
        ),
      ),
      body: SafeArea(
        child: Responsive.constrain(
          SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Form(
              key: _formKey,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: <Widget>[
                  const SizedBox(height: 16),
                  if (branding?.logoUrl != null)
                    SizedBox(
                      height: 64,
                      child: Image.network(
                        branding!.logoUrl!,
                        // A broken logo must never block sign-in.
                        errorBuilder: (_, __, ___) =>
                            Icon(Icons.account_balance, size: 56, color: theme.colorScheme.primary),
                      ),
                    )
                  else
                    Icon(Icons.account_balance, size: 56, color: theme.colorScheme.primary),
                  const SizedBox(height: 24),
                  Text(
                    _isPlatformLogin ? 'Platform sign-in' : branding?.name ?? 'Sign in',
                    textAlign: TextAlign.center,
                    style: theme.textTheme.headlineSmall,
                  ),
                  if (!_isPlatformLogin) ...<Widget>[
                    const SizedBox(height: 4),
                    Text(
                      widget.tenantSlug!,
                      textAlign: TextAlign.center,
                      style: theme.textTheme.bodySmall
                          ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                    ),
                  ],
                  const SizedBox(height: 32),
                  if (sessionMessage != null) ...<Widget>[
                    _Banner(message: sessionMessage, isError: false),
                    const SizedBox(height: 16),
                  ],
                  TextFormField(
                    controller: _usernameController,
                    autofillHints: const <String>[AutofillHints.username],
                    textInputAction: TextInputAction.next,
                    autocorrect: false,
                    decoration: const InputDecoration(
                      labelText: 'Username',
                      prefixIcon: Icon(Icons.person_outline),
                    ),
                    validator: (String? value) => Validators.required(value, field: 'Username'),
                  ),
                  const SizedBox(height: 16),
                  TextFormField(
                    controller: _passwordController,
                    obscureText: _obscurePassword,
                    autofillHints: const <String>[AutofillHints.password],
                    textInputAction: TextInputAction.done,
                    decoration: InputDecoration(
                      labelText: 'Password',
                      prefixIcon: const Icon(Icons.lock_outline),
                      suffixIcon: IconButton(
                        icon: Icon(
                          _obscurePassword ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                        ),
                        onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                        tooltip: _obscurePassword ? 'Show password' : 'Hide password',
                      ),
                    ),
                    // Only presence is checked here: telling an attacker that a
                    // password fails the policy would confirm it is not the real one.
                    validator: (String? value) => Validators.required(value, field: 'Password'),
                    onFieldSubmitted: (_) => _submit(),
                  ),
                  if (_error != null) ...<Widget>[
                    const SizedBox(height: 16),
                    _Banner(message: _error!, isError: true),
                  ],
                  const SizedBox(height: 24),
                  FilledButton(
                    onPressed: _isSubmitting ? null : _submit,
                    child: _isSubmitting
                        ? const SizedBox(
                            height: 20,
                            width: 20,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Text('Sign in'),
                  ),
                  if (!_isPlatformLogin) ...<Widget>[
                    const SizedBox(height: 8),
                    TextButton(
                      onPressed: () => context.push(
                        '${Routes.forgotPassword}?organisation=${widget.tenantSlug}',
                      ),
                      child: const Text('Forgot your password?'),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _Banner extends StatelessWidget {
  const _Banner({required this.message, required this.isError});

  final String message;
  final bool isError;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final Color background =
        isError ? theme.colorScheme.errorContainer : theme.colorScheme.secondaryContainer;
    final Color foreground =
        isError ? theme.colorScheme.onErrorContainer : theme.colorScheme.onSecondaryContainer;

    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: background, borderRadius: BorderRadius.circular(12)),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Icon(isError ? Icons.error_outline : Icons.info_outline, size: 20, color: foreground),
          const SizedBox(width: 8),
          Expanded(
            child: Text(message, style: theme.textTheme.bodySmall?.copyWith(color: foreground)),
          ),
        ],
      ),
    );
  }
}
