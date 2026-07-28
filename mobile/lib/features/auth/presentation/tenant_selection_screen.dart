import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/providers.dart';
import '../../../core/router/app_router.dart';
import '../../../core/utils/validators.dart';
import '../../../core/widgets/common_widgets.dart';
import '../domain/session.dart';

/// Organisation selection - the first screen an unauthenticated user sees.
///
/// The app is multi-tenant, so a username alone is ambiguous: two institutions
/// may each have a "jane.officer". Resolving the organisation first also lets the
/// app apply that institution's branding before the user types a password, which
/// is a small but real anti-phishing signal.
class TenantSelectionScreen extends ConsumerStatefulWidget {
  const TenantSelectionScreen({super.key});

  @override
  ConsumerState<TenantSelectionScreen> createState() => _TenantSelectionScreenState();
}

class _TenantSelectionScreenState extends ConsumerState<TenantSelectionScreen> {
  final GlobalKey<FormState> _formKey = GlobalKey<FormState>();
  final TextEditingController _slugController = TextEditingController();

  bool _isChecking = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _prefillLastOrganisation();
  }

  /// Most users work for one institution; remembering it saves typing it daily.
  Future<void> _prefillLastOrganisation() async {
    final String? previous = await ref.read(authRepositoryProvider).lastTenantSlug();
    if (previous != null && mounted) {
      _slugController.text = previous;
    }
  }

  @override
  void dispose() {
    _slugController.dispose();
    super.dispose();
  }

  Future<void> _continue() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    setState(() {
      _isChecking = true;
      _error = null;
    });

    final String slug = _slugController.text.trim().toLowerCase();
    final TenantBranding? branding = await ref.read(authRepositoryProvider).lookupTenant(slug);

    if (!mounted) {
      return;
    }
    setState(() => _isChecking = false);

    if (branding == null) {
      setState(() => _error = 'We could not find that organisation. Check the identifier '
          'with your administrator.');
      return;
    }
    if (!branding.loginEnabled) {
      setState(() => _error = 'This organisation is not currently active. '
          'Please contact your administrator.');
      return;
    }

    // Apply the institution's branding immediately, before sign-in.
    ref.read(tenantBrandingProvider.notifier).state = branding;
    if (mounted) {
      context.go('${Routes.login}?organisation=$slug');
    }
  }

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);

    return Scaffold(
      body: SafeArea(
        child: Responsive.constrain(
          SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Form(
              key: _formKey,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: <Widget>[
                  const SizedBox(height: 48),
                  Icon(Icons.account_balance, size: 64, color: theme.colorScheme.primary),
                  const SizedBox(height: 24),
                  Text(
                    'Sign in to your organisation',
                    textAlign: TextAlign.center,
                    style: theme.textTheme.headlineSmall,
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Enter the organisation identifier your administrator gave you.',
                    textAlign: TextAlign.center,
                    style: theme.textTheme.bodyMedium
                        ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                  ),
                  const SizedBox(height: 32),
                  TextFormField(
                    controller: _slugController,
                    autofocus: true,
                    textInputAction: TextInputAction.done,
                    autocorrect: false,
                    decoration: const InputDecoration(
                      labelText: 'Organisation',
                      hintText: 'acme-microfinance',
                      prefixIcon: Icon(Icons.business_outlined),
                    ),
                    validator: Validators.organisation,
                    onFieldSubmitted: (_) => _continue(),
                  ),
                  if (_error != null) ...<Widget>[
                    const SizedBox(height: 16),
                    _ErrorBanner(message: _error!),
                  ],
                  const SizedBox(height: 24),
                  FilledButton(
                    onPressed: _isChecking ? null : _continue,
                    child: _isChecking
                        ? const SizedBox(
                            height: 20,
                            width: 20,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Text('Continue'),
                  ),
                  const SizedBox(height: 16),
                  TextButton(
                    onPressed: () => context.go(Routes.login),
                    child: const Text('I am a platform administrator'),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _ErrorBanner extends StatelessWidget {
  const _ErrorBanner({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: theme.colorScheme.errorContainer,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Icon(Icons.error_outline, size: 20, color: theme.colorScheme.onErrorContainer),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              message,
              style: theme.textTheme.bodySmall
                  ?.copyWith(color: theme.colorScheme.onErrorContainer),
            ),
          ),
        ],
      ),
    );
  }
}
