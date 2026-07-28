import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/auth/domain/session.dart';
import '../providers.dart';
import '../router/app_router.dart';
import '../utils/formatters.dart';
import 'common_widgets.dart';

/// Navigation frame shared by every signed-in screen.
///
/// Destinations are filtered by the user's role, so a cashier never sees an
/// approvals tab they cannot use. This is presentation only - the backend
/// refuses the underlying calls regardless of what the app chooses to show.
class AppShell extends ConsumerWidget {
  const AppShell({required this.child, super.key});

  final Widget child;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final Session? session = ref.watch(currentSessionProvider);
    if (session == null) {
      return child;
    }

    final List<_Destination> destinations = _destinationsFor(session);
    final String location = GoRouterState.of(context).matchedLocation;
    final int index = _selectedIndex(destinations, location);

    // Tablets and larger get a persistent rail; phones get a bottom bar.
    if (!Responsive.isPhone(context)) {
      return Scaffold(
        body: Row(
          children: <Widget>[
            NavigationRail(
              selectedIndex: index,
              onDestinationSelected: (int selected) =>
                  context.go(destinations[selected].route),
              labelType: NavigationRailLabelType.all,
              leading: Padding(
                padding: const EdgeInsets.symmetric(vertical: 16),
                child: CircleAvatar(child: Text(Formatters.initials(session.fullName))),
              ),
              destinations: destinations
                  .map(
                    (_Destination destination) => NavigationRailDestination(
                      icon: Icon(destination.icon),
                      selectedIcon: Icon(destination.selectedIcon),
                      label: Text(destination.label),
                    ),
                  )
                  .toList(),
            ),
            const VerticalDivider(width: 1),
            Expanded(child: child),
          ],
        ),
      );
    }

    return Scaffold(
      body: child,
      bottomNavigationBar: NavigationBar(
        selectedIndex: index,
        onDestinationSelected: (int selected) => context.go(destinations[selected].route),
        destinations: destinations
            .map(
              (_Destination destination) => NavigationDestination(
                icon: Icon(destination.icon),
                selectedIcon: Icon(destination.selectedIcon),
                label: destination.label,
              ),
            )
            .toList(),
      ),
    );
  }

  /// Highest-index match wins so nested routes keep their parent tab selected.
  int _selectedIndex(List<_Destination> destinations, String location) {
    int selected = 0;
    for (int index = 0; index < destinations.length; index++) {
      if (location.startsWith(destinations[index].route)) {
        selected = index;
      }
    }
    return selected;
  }

  List<_Destination> _destinationsFor(Session session) {
    final List<_Destination> destinations = <_Destination>[
      const _Destination(Routes.dashboard, 'Home', Icons.dashboard_outlined, Icons.dashboard),
    ];

    if (session.canManageCustomers) {
      destinations.add(
        const _Destination(Routes.customers, 'Customers', Icons.people_outline, Icons.people),
      );
    }
    if (session.canCreateApplications || session.canApproveLoans) {
      destinations.add(
        const _Destination(
          Routes.applications,
          'Loans',
          Icons.assignment_outlined,
          Icons.assignment,
        ),
      );
    }
    destinations.add(
      const _Destination(Routes.calculator, 'Calculator', Icons.calculate_outlined, Icons.calculate),
    );
    if (session.canViewReports) {
      destinations.add(
        const _Destination(Routes.reports, 'Reports', Icons.insights_outlined, Icons.insights),
      );
    }
    destinations.add(
      const _Destination(Routes.settings, 'Settings', Icons.settings_outlined, Icons.settings),
    );
    return destinations;
  }
}

class _Destination {
  const _Destination(this.route, this.label, this.icon, this.selectedIcon);

  final String route;
  final String label;
  final IconData icon;
  final IconData selectedIcon;
}

