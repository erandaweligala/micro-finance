import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/auth/presentation/change_password_screen.dart';
import '../../features/auth/presentation/forgot_password_screen.dart';
import '../../features/auth/presentation/login_screen.dart';
import '../../features/auth/presentation/tenant_selection_screen.dart';
import '../../features/customers/presentation/customer_form_screen.dart';
import '../../features/customers/presentation/customer_list_screen.dart';
import '../../features/customers/presentation/customer_profile_screen.dart';
import '../../features/customers/presentation/kyc_screen.dart';
import '../../features/dashboard/presentation/dashboard_screen.dart';
import '../../features/ledger/presentation/ledger_screen.dart';
import '../../features/loan_accounts/presentation/loan_detail_screen.dart';
import '../../features/loan_applications/presentation/application_detail_screen.dart';
import '../../features/loan_applications/presentation/application_form_screen.dart';
import '../../features/loan_applications/presentation/application_list_screen.dart';
import '../../features/loan_calculator/presentation/loan_calculator_screen.dart';
import '../../features/payments/presentation/payment_entry_screen.dart';
import '../../features/payments/presentation/receipt_screen.dart';
import '../../features/reports/presentation/reports_screen.dart';
import '../../features/settings/presentation/settings_screen.dart';
import '../providers.dart';
import '../widgets/app_shell.dart';

/// Route names, referenced by screens instead of raw path strings so a path
/// change is a single edit.
class Routes {
  const Routes._();

  static const String splash = '/';
  static const String tenantSelection = '/organisation';
  static const String login = '/sign-in';
  static const String forgotPassword = '/forgot-password';
  static const String changePassword = '/change-password';

  static const String dashboard = '/dashboard';
  static const String customers = '/customers';
  static const String customerNew = '/customers/new';
  static String customerProfile(String id) => '/customers/$id';
  static String customerEdit(String id) => '/customers/$id/edit';
  static String customerKyc(String id) => '/customers/$id/kyc';

  static const String calculator = '/calculator';

  static const String applications = '/applications';
  static const String applicationNew = '/applications/new';
  static String applicationDetail(String id) => '/applications/$id';

  static String loanDetail(String id) => '/loans/$id';
  static String loanLedger(String id) => '/loans/$id/ledger';
  static String loanPayment(String id) => '/loans/$id/payment';
  static String receipt(String id) => '/payments/$id/receipt';

  static const String reports = '/reports';
  static const String settings = '/settings';
}

/// Builds the router and wires the authentication redirect.
///
/// Guarding centrally means no screen has to check "am I signed in?" itself, and
/// a user who is forced to change their password cannot navigate around that
/// requirement by deep link.
GoRouter buildRouter(Ref ref) {
  final ValueNotifier<int> refreshSignal = ValueNotifier<int>(0);
  ref.listen<SessionState>(sessionProvider, (SessionState? previous, SessionState next) {
    refreshSignal.value++;
  });
  ref.onDispose(refreshSignal.dispose);

  return GoRouter(
    initialLocation: Routes.splash,
    refreshListenable: refreshSignal,
    redirect: (BuildContext context, GoRouterState state) {
      final SessionState session = ref.read(sessionProvider);
      final String location = state.matchedLocation;

      final bool onAuthRoute = location == Routes.login ||
          location == Routes.tenantSelection ||
          location == Routes.forgotPassword ||
          location == Routes.splash;

      if (session is SessionUnknown) {
        // Still restoring: hold on the splash rather than flashing sign-in.
        return location == Routes.splash ? null : Routes.splash;
      }

      if (session is SessionUnauthenticated) {
        return onAuthRoute && location != Routes.splash ? null : Routes.tenantSelection;
      }

      if (session is SessionAuthenticated) {
        // A temporary password must be replaced before anything else is reachable.
        if (session.session.mustChangePassword && location != Routes.changePassword) {
          return Routes.changePassword;
        }
        if (onAuthRoute) {
          return Routes.dashboard;
        }
      }
      return null;
    },
    routes: <RouteBase>[
      GoRoute(
        path: Routes.splash,
        builder: (BuildContext context, GoRouterState state) => const _SplashScreen(),
      ),
      GoRoute(
        path: Routes.tenantSelection,
        builder: (BuildContext context, GoRouterState state) => const TenantSelectionScreen(),
      ),
      GoRoute(
        path: Routes.login,
        builder: (BuildContext context, GoRouterState state) =>
            LoginScreen(tenantSlug: state.uri.queryParameters['organisation']),
      ),
      GoRoute(
        path: Routes.forgotPassword,
        builder: (BuildContext context, GoRouterState state) =>
            ForgotPasswordScreen(tenantSlug: state.uri.queryParameters['organisation']),
      ),
      GoRoute(
        path: Routes.changePassword,
        builder: (BuildContext context, GoRouterState state) => const ChangePasswordScreen(),
      ),

      // Everything below sits inside the navigation shell.
      ShellRoute(
        builder: (BuildContext context, GoRouterState state, Widget child) =>
            AppShell(child: child),
        routes: <RouteBase>[
          GoRoute(
            path: Routes.dashboard,
            builder: (BuildContext context, GoRouterState state) => const DashboardScreen(),
          ),
          GoRoute(
            path: Routes.customers,
            builder: (BuildContext context, GoRouterState state) => const CustomerListScreen(),
            routes: <RouteBase>[
              GoRoute(
                path: 'new',
                builder: (BuildContext context, GoRouterState state) =>
                    const CustomerFormScreen(),
              ),
              GoRoute(
                path: ':id',
                builder: (BuildContext context, GoRouterState state) =>
                    CustomerProfileScreen(customerId: state.pathParameters['id']!),
                routes: <RouteBase>[
                  GoRoute(
                    path: 'edit',
                    builder: (BuildContext context, GoRouterState state) =>
                        CustomerFormScreen(customerId: state.pathParameters['id']),
                  ),
                  GoRoute(
                    path: 'kyc',
                    builder: (BuildContext context, GoRouterState state) =>
                        KycScreen(customerId: state.pathParameters['id']!),
                  ),
                ],
              ),
            ],
          ),
          GoRoute(
            path: Routes.calculator,
            builder: (BuildContext context, GoRouterState state) => const LoanCalculatorScreen(),
          ),
          GoRoute(
            path: Routes.applications,
            builder: (BuildContext context, GoRouterState state) =>
                const ApplicationListScreen(),
            routes: <RouteBase>[
              GoRoute(
                path: 'new',
                builder: (BuildContext context, GoRouterState state) => ApplicationFormScreen(
                  customerId: state.uri.queryParameters['customerId'],
                ),
              ),
              GoRoute(
                path: ':id',
                builder: (BuildContext context, GoRouterState state) =>
                    ApplicationDetailScreen(applicationId: state.pathParameters['id']!),
              ),
            ],
          ),
          GoRoute(
            path: '/loans/:id',
            builder: (BuildContext context, GoRouterState state) =>
                LoanDetailScreen(loanAccountId: state.pathParameters['id']!),
            routes: <RouteBase>[
              GoRoute(
                path: 'ledger',
                builder: (BuildContext context, GoRouterState state) =>
                    LedgerScreen(loanAccountId: state.pathParameters['id']!),
              ),
              GoRoute(
                path: 'payment',
                builder: (BuildContext context, GoRouterState state) =>
                    PaymentEntryScreen(loanAccountId: state.pathParameters['id']!),
              ),
            ],
          ),
          GoRoute(
            path: '/payments/:id/receipt',
            builder: (BuildContext context, GoRouterState state) =>
                ReceiptScreen(paymentId: state.pathParameters['id']!),
          ),
          GoRoute(
            path: Routes.reports,
            builder: (BuildContext context, GoRouterState state) => const ReportsScreen(),
          ),
          GoRoute(
            path: Routes.settings,
            builder: (BuildContext context, GoRouterState state) => const SettingsScreen(),
          ),
        ],
      ),
    ],
    errorBuilder: (BuildContext context, GoRouterState state) => Scaffold(
      appBar: AppBar(title: const Text('Not found')),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              const Icon(Icons.explore_off_outlined, size: 48),
              const SizedBox(height: 16),
              const Text('That screen does not exist.'),
              const SizedBox(height: 24),
              FilledButton(
                onPressed: () => context.go(Routes.dashboard),
                child: const Text('Go to dashboard'),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}

final Provider<GoRouter> routerProvider = Provider<GoRouter>(buildRouter);

class _SplashScreen extends StatelessWidget {
  const _SplashScreen();

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Icon(Icons.account_balance, size: 56),
            SizedBox(height: 24),
            CircularProgressIndicator(),
          ],
        ),
      ),
    );
  }
}
