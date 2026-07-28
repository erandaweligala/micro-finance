import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/providers.dart';
import '../../../core/router/app_router.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/async_view.dart';
import '../../../core/widgets/common_widgets.dart';
import '../../auth/domain/session.dart';
import '../data/dashboard_repository.dart';
import '../domain/dashboard_summary.dart';

/// The home screen, composed from the signed-in user's role.
///
/// A cashier opens the app to take payments; a branch manager opens it to see
/// arrears. Showing both the same dashboard would serve neither, so the tiles
/// and quick actions are chosen from the user's capabilities.
class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final Session? session = ref.watch(currentSessionProvider);
    final AsyncValue<DashboardSummary> summary = ref.watch(dashboardSummaryProvider);
    final ThemeData theme = Theme.of(context);

    if (session == null) {
      return const Scaffold(body: LoadingState());
    }

    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Text('Hello, ${session.fullName.split(' ').first}',
                style: theme.textTheme.titleMedium),
            Text(
              session.primaryRole.label,
              style: theme.textTheme.bodySmall
                  ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
          ],
        ),
        actions: <Widget>[
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Refresh',
            onPressed: () => ref.invalidate(dashboardSummaryProvider),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(dashboardSummaryProvider),
        child: AsyncView<DashboardSummary>(
          value: summary,
          onRetry: () => ref.invalidate(dashboardSummaryProvider),
          data: (DashboardSummary data) => ListView(
            padding: const EdgeInsets.all(16),
            children: <Widget>[
              if (data.alerts.isNotEmpty) ...<Widget>[
                _AlertsCard(alerts: data.alerts),
                const SizedBox(height: 16),
              ],
              GridView.count(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                crossAxisCount: Responsive.columns(context),
                childAspectRatio: 1.5,
                mainAxisSpacing: 12,
                crossAxisSpacing: 12,
                children: _tilesFor(context, session, data),
              ),
              const SizedBox(height: 24),
              Text('Quick actions', style: theme.textTheme.titleSmall),
              const SizedBox(height: 12),
              _QuickActions(session: session),
              const SizedBox(height: 32),
            ],
          ),
        ),
      ),
    );
  }

  List<Widget> _tilesFor(BuildContext context, Session session, DashboardSummary data) {
    final ThemeData theme = Theme.of(context);
    final List<Widget> tiles = <Widget>[
      StatTile(
        label: 'Active loans',
        value: '${data.activeLoans}',
        icon: Icons.account_balance_wallet_outlined,
      ),
      StatTile(
        label: 'In arrears',
        value: '${data.overdueLoans}',
        icon: Icons.warning_amber_outlined,
        tone: data.overdueLoans > 0 ? theme.colorScheme.error : null,
      ),
    ];

    if (session.canViewReports) {
      tiles.addAll(<Widget>[
        StatTile(
          label: 'Outstanding',
          value: Formatters.moneyCompact(data.outstandingPrincipal),
          caption: 'Principal on the book',
          icon: Icons.savings_outlined,
        ),
        StatTile(
          label: 'At risk (30d)',
          value: Formatters.moneyCompact(data.principalAtRisk30),
          caption: 'Principal over 30 days late',
          icon: Icons.trending_down,
          tone: theme.colorScheme.error,
        ),
      ]);
    }

    tiles.addAll(<Widget>[
      StatTile(
        label: 'Collected',
        value: Formatters.moneyCompact(data.collectedThisMonth),
        caption: 'This month',
        icon: Icons.south_west,
      ),
      StatTile(
        label: 'Disbursed',
        value: Formatters.moneyCompact(data.disbursedThisMonth),
        caption: 'This month',
        icon: Icons.north_east,
      ),
    ]);
    return tiles;
  }
}

class _AlertsCard extends StatelessWidget {
  const _AlertsCard({required this.alerts});

  final List<String> alerts;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Card(
      color: theme.colorScheme.errorContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Row(
              children: <Widget>[
                Icon(Icons.priority_high, color: theme.colorScheme.onErrorContainer),
                const SizedBox(width: 8),
                Text(
                  'Needs attention',
                  style: theme.textTheme.titleSmall
                      ?.copyWith(color: theme.colorScheme.onErrorContainer),
                ),
              ],
            ),
            const SizedBox(height: 8),
            ...alerts.map(
              (String alert) => Padding(
                padding: const EdgeInsets.symmetric(vertical: 2),
                child: Text(
                  '• $alert',
                  style: theme.textTheme.bodyMedium
                      ?.copyWith(color: theme.colorScheme.onErrorContainer),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _QuickActions extends StatelessWidget {
  const _QuickActions({required this.session});

  final Session session;

  @override
  Widget build(BuildContext context) {
    final List<_Action> actions = <_Action>[
      if (session.canManageCustomers)
        const _Action('Register customer', Icons.person_add_outlined, Routes.customerNew),
      if (session.canCreateApplications)
        const _Action('New application', Icons.note_add_outlined, Routes.applicationNew),
      const _Action('Calculator', Icons.calculate_outlined, Routes.calculator),
      if (session.canCapturePayments)
        const _Action('Find a loan to pay', Icons.payments_outlined, Routes.applications),
      if (session.canViewReports)
        const _Action('Reports', Icons.insights_outlined, Routes.reports),
    ];

    return Wrap(
      spacing: 12,
      runSpacing: 12,
      children: actions
          .map(
            (_Action action) => ActionChip(
              avatar: Icon(action.icon, size: 18),
              label: Text(action.label),
              onPressed: () => context.go(action.route),
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 10),
            ),
          )
          .toList(),
    );
  }
}

class _Action {
  const _Action(this.label, this.icon, this.route);

  final String label;
  final IconData icon;
  final String route;
}
