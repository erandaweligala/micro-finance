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
import '../data/customer_repository.dart';
import '../domain/customer.dart';

/// Customer list and search.
///
/// A full phone or identification number matches exactly, resolved server-side
/// through a blind index - the app cannot search encrypted fields itself, and
/// should not be able to.
class CustomerListScreen extends ConsumerStatefulWidget {
  const CustomerListScreen({super.key});

  @override
  ConsumerState<CustomerListScreen> createState() => _CustomerListScreenState();
}

class _CustomerListScreenState extends ConsumerState<CustomerListScreen> {
  final TextEditingController _searchController = TextEditingController();

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  void _applySearch(String value) {
    final String trimmed = value.trim();
    ref.read(customerQueryProvider.notifier).update(
          (CustomerQuery previous) =>
              CustomerQuery(search: trimmed.isEmpty ? null : trimmed, status: previous.status, kycStatus: previous.kycStatus),
        );
  }

  @override
  Widget build(BuildContext context) {
    final AsyncValue<Paged<Customer>> customers = ref.watch(customerListProvider);
    final CustomerQuery query = ref.watch(customerQueryProvider);
    final Session? session = ref.watch(currentSessionProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Customers')),
      floatingActionButton: (session?.canManageCustomers ?? false)
          ? FloatingActionButton.extended(
              onPressed: () => context.go(Routes.customerNew),
              icon: const Icon(Icons.person_add_outlined),
              label: const Text('Register'),
            )
          : null,
      body: Column(
        children: <Widget>[
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
            child: TextField(
              controller: _searchController,
              textInputAction: TextInputAction.search,
              decoration: InputDecoration(
                hintText: 'Name, customer number, phone or ID',
                prefixIcon: const Icon(Icons.search),
                suffixIcon: _searchController.text.isEmpty
                    ? null
                    : IconButton(
                        icon: const Icon(Icons.clear),
                        onPressed: () {
                          _searchController.clear();
                          _applySearch('');
                        },
                      ),
              ),
              onSubmitted: _applySearch,
              onChanged: (_) => setState(() {}),
            ),
          ),
          SizedBox(
            height: 56,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              children: <Widget>[
                _FilterChip(
                  label: 'Awaiting KYC',
                  selected: query.kycStatus == 'PENDING',
                  onSelected: (bool selected) => ref
                      .read(customerQueryProvider.notifier)
                      .update((CustomerQuery previous) => CustomerQuery(
                            search: previous.search,
                            status: previous.status,
                            kycStatus: selected ? 'PENDING' : null,
                          )),
                ),
                const SizedBox(width: 8),
                _FilterChip(
                  label: 'Verified',
                  selected: query.kycStatus == 'VERIFIED',
                  onSelected: (bool selected) => ref
                      .read(customerQueryProvider.notifier)
                      .update((CustomerQuery previous) => CustomerQuery(
                            search: previous.search,
                            status: previous.status,
                            kycStatus: selected ? 'VERIFIED' : null,
                          )),
                ),
                const SizedBox(width: 8),
                _FilterChip(
                  label: 'Deactivated',
                  selected: query.status == 'DEACTIVATED',
                  onSelected: (bool selected) => ref
                      .read(customerQueryProvider.notifier)
                      .update((CustomerQuery previous) => CustomerQuery(
                            search: previous.search,
                            status: selected ? 'DEACTIVATED' : null,
                            kycStatus: previous.kycStatus,
                          )),
                ),
              ],
            ),
          ),
          Expanded(
            child: AsyncView<Paged<Customer>>(
              value: customers,
              onRetry: () => ref.invalidate(customerListProvider),
              isEmpty: (Paged<Customer> page) => page.isEmpty,
              emptyTitle: 'No customers found',
              emptyMessage: query.search != null
                  ? 'Nothing matched "${query.search}". Try a different search.'
                  : 'Register your first customer to get started.',
              emptyIcon: Icons.people_outline,
              emptyAction: (session?.canManageCustomers ?? false)
                  ? FilledButton.icon(
                      onPressed: () => context.go(Routes.customerNew),
                      icon: const Icon(Icons.person_add_outlined),
                      label: const Text('Register a customer'),
                    )
                  : null,
              data: (Paged<Customer> page) => RefreshIndicator(
                onRefresh: () async => ref.invalidate(customerListProvider),
                child: ListView.separated(
                  padding: const EdgeInsets.fromLTRB(8, 0, 8, 96),
                  itemCount: page.content.length + 1,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (BuildContext context, int index) {
                    if (index == page.content.length) {
                      return Padding(
                        padding: const EdgeInsets.all(16),
                        child: Text(
                          '${page.totalElements} customer(s)',
                          textAlign: TextAlign.center,
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      );
                    }
                    return _CustomerTile(customer: page.content[index]);
                  },
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _FilterChip extends StatelessWidget {
  const _FilterChip({
    required this.label,
    required this.selected,
    required this.onSelected,
  });

  final String label;
  final bool selected;
  final ValueChanged<bool> onSelected;

  @override
  Widget build(BuildContext context) =>
      FilterChip(label: Text(label), selected: selected, onSelected: onSelected);
}

class _CustomerTile extends StatelessWidget {
  const _CustomerTile({required this.customer});

  final Customer customer;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return ListTile(
      onTap: () => context.go(Routes.customerProfile(customer.id)),
      leading: CircleAvatar(
        backgroundColor: theme.colorScheme.primaryContainer,
        child: Text(
          Formatters.initials(customer.fullName),
          style: TextStyle(color: theme.colorScheme.onPrimaryContainer),
        ),
      ),
      title: Text(customer.fullName, maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: Text(
        '${customer.customerNumber} · ${customer.phoneNumberMasked ?? '-'}',
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: <Widget>[
          StatusChip(customer.kycStatus, compact: true),
          if (customer.status != 'ACTIVE')
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: StatusChip(customer.status, compact: true),
            ),
        ],
      ),
    );
  }
}
