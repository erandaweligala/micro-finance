import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/providers.dart';
import '../../../core/router/app_router.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/async_view.dart';
import '../../../core/widgets/common_widgets.dart';
import '../../auth/domain/session.dart';
import '../../loan_calculator/domain/loan_schedule.dart';
import '../../loan_calculator/presentation/schedule_table.dart';
import '../data/loan_account_repository.dart';
import '../domain/loan_account.dart';

/// A live loan: balances, schedule and the actions available on it.
class LoanDetailScreen extends ConsumerWidget {
  const LoanDetailScreen({required this.loanAccountId, super.key});

  final String loanAccountId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final AsyncValue<LoanAccount> account = ref.watch(loanAccountProvider(loanAccountId));
    final Session? session = ref.watch(currentSessionProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Loan'),
        actions: <Widget>[
          IconButton(
            icon: const Icon(Icons.receipt_long_outlined),
            tooltip: 'Ledger and statement',
            onPressed: () => context.go(Routes.loanLedger(loanAccountId)),
          ),
        ],
      ),
      body: AsyncView<LoanAccount>(
        value: account,
        onRetry: () => ref.invalidate(loanAccountProvider(loanAccountId)),
        data: (LoanAccount data) => RefreshIndicator(
          onRefresh: () async {
            ref.invalidate(loanAccountProvider(loanAccountId));
            ref.invalidate(loanScheduleProvider(loanAccountId));
          },
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: <Widget>[
              _BalanceCard(account: data),
              if (data.isInArrears) ...<Widget>[
                const SizedBox(height: 16),
                _ArrearsBanner(account: data),
              ],
              const SizedBox(height: 16),
              SectionCard(
                title: 'Loan terms',
                child: Column(
                  children: <Widget>[
                    DetailRow('Account number', data.accountNumber),
                    DetailRow(
                      'Principal',
                      Formatters.money(data.principal, currency: data.currency),
                    ),
                    DetailRow('Interest rate', Formatters.percent(data.annualInterestRate)),
                    DetailRow('Method', Formatters.humanise(data.interestMethod)),
                    DetailRow('Frequency', Formatters.humanise(data.repaymentFrequency)),
                    DetailRow('Installments', '${data.numberOfInstallments}'),
                    DetailRow(
                      'Installment amount',
                      Formatters.money(data.installmentAmount, currency: data.currency),
                    ),
                    DetailRow('Disbursed', Formatters.date(data.disbursementDate)),
                    DetailRow('Matures', Formatters.date(data.maturityDate)),
                    DetailRow('Last payment', Formatters.date(data.lastPaymentDate)),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              _PayoffCard(loanAccountId: loanAccountId),
              const SizedBox(height: 16),
              _ScheduleSection(loanAccountId: loanAccountId, currency: data.currency),
              const SizedBox(height: 24),
              if ((session?.canCapturePayments ?? false) && data.acceptsPayments)
                FilledButton.icon(
                  onPressed: () => context.go(Routes.loanPayment(loanAccountId)),
                  icon: const Icon(Icons.payments_outlined),
                  label: const Text('Record a payment'),
                ),
              const SizedBox(height: 8),
              OutlinedButton.icon(
                onPressed: () => context.go(Routes.loanLedger(loanAccountId)),
                icon: const Icon(Icons.receipt_long_outlined),
                label: const Text('View ledger'),
              ),
              const SizedBox(height: 32),
            ],
          ),
        ),
      ),
    );
  }
}

class _BalanceCard extends StatelessWidget {
  const _BalanceCard({required this.account});

  final LoanAccount account;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          Row(
            children: <Widget>[
              Expanded(
                child: Text(
                  'Total outstanding',
                  style: theme.textTheme.labelMedium
                      ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                ),
              ),
              StatusChip(account.status, compact: true),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            Formatters.money(account.totalOutstanding, currency: account.currency),
            style: theme.textTheme.displaySmall?.copyWith(
              fontWeight: FontWeight.w700,
              color: account.isInArrears ? theme.colorScheme.error : theme.colorScheme.primary,
              fontFeatures: const <FontFeature>[FontFeature.tabularFigures()],
            ),
          ),
          const SizedBox(height: 16),
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: account.repaymentProgress,
              minHeight: 8,
              backgroundColor: theme.colorScheme.surfaceContainerHighest,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            '${(account.repaymentProgress * 100).toStringAsFixed(0)}% repaid of '
            '${Formatters.money(account.totalRepayable, currency: account.currency)}',
            style: theme.textTheme.bodySmall,
          ),
          const Divider(height: 32),
          DetailRow(
            'Principal outstanding',
            Formatters.money(account.outstandingPrincipal, currency: account.currency),
          ),
          DetailRow(
            'Interest outstanding',
            Formatters.money(account.outstandingInterest, currency: account.currency),
          ),
          if ((double.tryParse(account.outstandingPenalty) ?? 0) > 0)
            DetailRow(
              'Penalties',
              Formatters.money(account.outstandingPenalty, currency: account.currency),
            ),
          if ((double.tryParse(account.advanceBalance) ?? 0) > 0)
            DetailRow(
              'Credit balance',
              Formatters.money(account.advanceBalance, currency: account.currency),
            ),
        ],
      ),
    );
  }
}

class _ArrearsBanner extends StatelessWidget {
  const _ArrearsBanner({required this.account});

  final LoanAccount account;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: theme.colorScheme.errorContainer,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: <Widget>[
          Icon(Icons.warning_amber_outlined, color: theme.colorScheme.onErrorContainer),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(
                  '${account.daysPastDue} days past due',
                  style: theme.textTheme.titleSmall
                      ?.copyWith(color: theme.colorScheme.onErrorContainer),
                ),
                Text(
                  '${Formatters.money(account.overdueAmount, currency: account.currency)} overdue',
                  style: theme.textTheme.bodySmall
                      ?.copyWith(color: theme.colorScheme.onErrorContainer),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Early settlement quote. Shown collapsed because it is a question customers
/// ask often but not on every visit.
class _PayoffCard extends ConsumerWidget {
  const _PayoffCard({required this.loanAccountId});

  final String loanAccountId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Card(
      child: ExpansionTile(
        title: const Text('Settle early'),
        subtitle: const Text('What it costs to clear this loan today'),
        childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        shape: const Border(),
        collapsedShape: const Border(),
        onExpansionChanged: (bool expanded) {
          if (expanded) {
            ref.invalidate(payoffQuoteProvider(loanAccountId));
          }
        },
        children: <Widget>[
          Consumer(
            builder: (BuildContext context, WidgetRef ref, Widget? child) {
              final AsyncValue<PayoffQuote> quote =
                  ref.watch(payoffQuoteProvider(loanAccountId));
              return AsyncView<PayoffQuote>(
                value: quote,
                onRetry: () => ref.invalidate(payoffQuoteProvider(loanAccountId)),
                data: (PayoffQuote data) => Column(
                  children: <Widget>[
                    DetailRow(
                      'Principal',
                      Formatters.money(data.outstandingPrincipal, currency: data.currency),
                    ),
                    DetailRow(
                      'Interest to date',
                      Formatters.money(data.interestDueToDate, currency: data.currency),
                    ),
                    if ((double.tryParse(data.penaltyDue) ?? 0) > 0)
                      DetailRow(
                        'Penalties',
                        Formatters.money(data.penaltyDue, currency: data.currency),
                      ),
                    const Divider(),
                    DetailRow(
                      'Settlement amount',
                      null,
                      valueWidget: MoneyText(
                        data.settlementAmount,
                        currency: data.currency,
                        emphasise: true,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Future interest that has not yet been incurred is not charged.',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              );
            },
          ),
        ],
      ),
    );
  }
}

class _ScheduleSection extends ConsumerWidget {
  const _ScheduleSection({required this.loanAccountId, this.currency});

  final String loanAccountId;
  final String? currency;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final AsyncValue<List<ScheduledInstallment>> schedule =
        ref.watch(loanScheduleProvider(loanAccountId));

    return SectionCard(
      title: 'Repayment schedule',
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      child: AsyncView<List<ScheduledInstallment>>(
        value: schedule,
        onRetry: () => ref.invalidate(loanScheduleProvider(loanAccountId)),
        isEmpty: (List<ScheduledInstallment> rows) => rows.isEmpty,
        emptyTitle: 'Schedule not available',
        emptyMessage: 'The repayment schedule is generated when the loan is disbursed.',
        data: (List<ScheduledInstallment> rows) => ScheduleTable(
          installments: rows,
          currency: currency,
          showStatus: true,
        ),
      ),
    );
  }
}
