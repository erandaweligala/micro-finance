import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/providers.dart';
import '../../../core/router/app_router.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/async_view.dart';
import '../../../core/widgets/common_widgets.dart';
import '../../auth/domain/session.dart';
import '../../shared/domain/paged.dart';
import '../data/application_repository.dart';
import '../domain/loan_application.dart';

/// The loan work queue.
///
/// An approver's first question is "what is waiting for me?", so the status
/// filter defaults are the ones that answer it.
class ApplicationListScreen extends ConsumerWidget {
  const ApplicationListScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final AsyncValue<Paged<LoanApplication>> applications = ref.watch(applicationListProvider);
    final String? status = ref.watch(applicationStatusFilterProvider);
    final Session? session = ref.watch(currentSessionProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Loan applications')),
      floatingActionButton: (session?.canCreateApplications ?? false)
          ? FloatingActionButton.extended(
              onPressed: () => context.go(Routes.applicationNew),
              icon: const Icon(Icons.note_add_outlined),
              label: const Text('New'),
            )
          : null,
      body: Column(
        children: <Widget>[
          SizedBox(
            height: 60,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              children: <Widget>[
                for (final MapEntry<String?, String> entry in <String?, String>{
                  null: 'All',
                  'SUBMITTED': 'Awaiting review',
                  'UNDER_REVIEW': 'In review',
                  'APPROVED': 'Ready to disburse',
                  'DISBURSED': 'Disbursed',
                  'REJECTED': 'Rejected',
                }.entries)
                  Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: FilterChip(
                      label: Text(entry.value),
                      selected: status == entry.key,
                      onSelected: (bool selected) => ref
                          .read(applicationStatusFilterProvider.notifier)
                          .state = selected ? entry.key : null,
                    ),
                  ),
              ],
            ),
          ),
          Expanded(
            child: AsyncView<Paged<LoanApplication>>(
              value: applications,
              onRetry: () => ref.invalidate(applicationListProvider),
              isEmpty: (Paged<LoanApplication> page) => page.isEmpty,
              emptyTitle: 'Nothing here',
              emptyMessage: status == null
                  ? 'No loan applications have been created yet.'
                  : 'No applications in this state.',
              emptyIcon: Icons.assignment_outlined,
              data: (Paged<LoanApplication> page) => RefreshIndicator(
                onRefresh: () async => ref.invalidate(applicationListProvider),
                child: ListView.separated(
                  padding: const EdgeInsets.fromLTRB(8, 0, 8, 96),
                  itemCount: page.content.length,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (BuildContext context, int index) =>
                      _ApplicationTile(application: page.content[index]),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ApplicationTile extends StatelessWidget {
  const _ApplicationTile({required this.application});

  final LoanApplication application;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return ListTile(
      onTap: () => context.go(Routes.applicationDetail(application.id)),
      title: Text(
        application.customerName ?? application.applicationNumber,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      subtitle: Text(
        '${application.applicationNumber} · ${application.productName ?? '-'}',
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: theme.textTheme.bodySmall,
      ),
      trailing: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: <Widget>[
          MoneyText(application.effectiveAmount, currency: application.currency),
          const SizedBox(height: 4),
          StatusChip(application.status, compact: true),
          if (application.isAwaitingDecision && application.requiredApprovalLevels > 1)
            Padding(
              padding: const EdgeInsets.only(top: 2),
              child: Text(
                'Approval ${application.approvalProgress}',
                style: theme.textTheme.labelSmall,
              ),
            ),
        ],
      ),
    );
  }
}
