import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/providers.dart';
import '../../../core/router/app_router.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/async_view.dart';
import '../../../core/widgets/common_widgets.dart';
import '../../auth/domain/session.dart';
import '../data/customer_repository.dart';
import '../domain/customer.dart';

/// A customer's profile, and the launch point for lending to them.
class CustomerProfileScreen extends ConsumerWidget {
  const CustomerProfileScreen({required this.customerId, super.key});

  final String customerId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final AsyncValue<Customer> customer = ref.watch(customerProvider(customerId));
    final Session? session = ref.watch(currentSessionProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Customer'),
        actions: <Widget>[
          if (session?.canManageCustomers ?? false)
            IconButton(
              icon: const Icon(Icons.edit_outlined),
              tooltip: 'Edit',
              onPressed: () => context.go(Routes.customerEdit(customerId)),
            ),
        ],
      ),
      body: AsyncView<Customer>(
        value: customer,
        onRetry: () => ref.invalidate(customerProvider(customerId)),
        data: (Customer data) => RefreshIndicator(
          onRefresh: () async => ref.invalidate(customerProvider(customerId)),
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: <Widget>[
              _Header(customer: data),
              const SizedBox(height: 16),
              if (!data.eligibleForLending) _EligibilityNotice(customer: data),
              if (!data.eligibleForLending) const SizedBox(height: 16),
              SectionCard(
                title: 'Personal details',
                child: Column(
                  children: <Widget>[
                    DetailRow('Customer number', data.customerNumber),
                    DetailRow('Date of birth', Formatters.date(data.dateOfBirth)),
                    DetailRow('Gender', Formatters.humanise(data.gender)),
                    DetailRow('Marital status', Formatters.humanise(data.maritalStatus)),
                    DetailRow('ID type', Formatters.humanise(data.idType)),
                    DetailRow('ID number', data.nationalIdMasked),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              SectionCard(
                title: 'Contact',
                child: Column(
                  children: <Widget>[
                    DetailRow('Phone', data.phoneNumberMasked),
                    DetailRow('Email', data.email),
                    DetailRow('Address', data.address?.singleLine),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              SectionCard(
                title: 'Employment',
                child: Column(
                  children: <Widget>[
                    DetailRow('Occupation', data.occupation),
                    DetailRow('Employer', data.employer),
                    DetailRow(
                      'Monthly income',
                      data.monthlyIncome == null
                          ? '-'
                          : Formatters.money(data.monthlyIncome),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              SectionCard(
                title: 'Know your customer',
                trailing: StatusChip(data.kycStatus, compact: true),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: <Widget>[
                    if (data.kycRejectionReason != null)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: Text(
                          data.kycRejectionReason!,
                          style: TextStyle(color: Theme.of(context).colorScheme.error),
                        ),
                      ),
                    OutlinedButton.icon(
                      onPressed: () => context.go(Routes.customerKyc(customerId)),
                      icon: const Icon(Icons.verified_user_outlined),
                      label: const Text('Manage KYC documents'),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),
              if ((session?.canCreateApplications ?? false) && data.eligibleForLending)
                FilledButton.icon(
                  onPressed: () =>
                      context.go('${Routes.applicationNew}?customerId=$customerId'),
                  icon: const Icon(Icons.note_add_outlined),
                  label: const Text('New loan application'),
                ),
              const SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: () => context.go('${Routes.applications}?customerId=$customerId'),
                icon: const Icon(Icons.history),
                label: const Text('Loan history'),
              ),
              const SizedBox(height: 32),
            ],
          ),
        ),
      ),
    );
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.customer});

  final Customer customer;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Row(
      children: <Widget>[
        CircleAvatar(
          radius: 32,
          backgroundColor: theme.colorScheme.primaryContainer,
          child: Text(
            Formatters.initials(customer.fullName),
            style: theme.textTheme.titleLarge
                ?.copyWith(color: theme.colorScheme.onPrimaryContainer),
          ),
        ),
        const SizedBox(width: 16),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Text(customer.fullName, style: theme.textTheme.titleLarge),
              const SizedBox(height: 4),
              Text(
                customer.customerNumber,
                style: theme.textTheme.bodySmall
                    ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
              ),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                children: <Widget>[
                  StatusChip(customer.status, compact: true),
                  StatusChip(customer.kycStatus, compact: true),
                ],
              ),
            ],
          ),
        ),
      ],
    );
  }
}

/// Explains, in plain terms, why this customer cannot be lent to yet.
class _EligibilityNotice extends StatelessWidget {
  const _EligibilityNotice({required this.customer});

  final Customer customer;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final String reason = customer.status != 'ACTIVE'
        ? 'This customer is ${Formatters.humanise(customer.status).toLowerCase()}.'
        : 'KYC is ${Formatters.humanise(customer.kycStatus).toLowerCase()}. '
            'Verify their documents before applying for a loan.';

    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: theme.colorScheme.tertiaryContainer,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: <Widget>[
          Icon(Icons.info_outline, color: theme.colorScheme.onTertiaryContainer),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              'Not eligible to borrow. $reason',
              style: theme.textTheme.bodySmall
                  ?.copyWith(color: theme.colorScheme.onTertiaryContainer),
            ),
          ),
        ],
      ),
    );
  }
}
