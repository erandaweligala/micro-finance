import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/utils/formatters.dart';
import '../../../core/widgets/async_view.dart';
import '../../../core/widgets/common_widgets.dart';
import '../data/ledger_repository.dart';
import '../domain/ledger_entry.dart';

/// The customer loan ledger and statement.
///
/// Entries are shown oldest first with the running balance on each line, which
/// is how a statement is read: the closing balance of one row is the opening
/// balance of the next. Reversals appear as their own contra entries rather than
/// removing the original line.
class LedgerScreen extends ConsumerWidget {
  const LedgerScreen({required this.loanAccountId, super.key});

  final String loanAccountId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final AsyncValue<Statement> statement = ref.watch(statementProvider(loanAccountId));

    return Scaffold(
      appBar: AppBar(
        title: const Text('Ledger'),
        actions: <Widget>[
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Refresh',
            onPressed: () => ref.invalidate(statementProvider(loanAccountId)),
          ),
        ],
      ),
      body: AsyncView<Statement>(
        value: statement,
        onRetry: () => ref.invalidate(statementProvider(loanAccountId)),
        isEmpty: (Statement data) => data.entries.isEmpty,
        emptyTitle: 'No transactions yet',
        emptyMessage: 'Disbursements, repayments and charges will appear here.',
        emptyIcon: Icons.receipt_long_outlined,
        data: (Statement data) => RefreshIndicator(
          onRefresh: () async => ref.invalidate(statementProvider(loanAccountId)),
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: <Widget>[
              _StatementSummary(statement: data),
              const SizedBox(height: 16),
              Text('Transactions', style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 8),
              ...data.entries.map(
                (LedgerEntry entry) => Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: _LedgerTile(entry: entry),
                ),
              ),
              const SizedBox(height: 32),
            ],
          ),
        ),
      ),
    );
  }
}

class _StatementSummary extends StatelessWidget {
  const _StatementSummary({required this.statement});

  final Statement statement;

  @override
  Widget build(BuildContext context) {
    return SectionCard(
      title: 'Statement',
      trailing: statement.periodEnd == null
          ? null
          : Text(
              'to ${Formatters.date(statement.periodEnd)}',
              style: Theme.of(context).textTheme.bodySmall,
            ),
      child: Column(
        children: <Widget>[
          DetailRow('Opening balance', Formatters.money(statement.openingBalance)),
          DetailRow('Charges (debits)', Formatters.money(statement.totalDebits)),
          DetailRow('Payments (credits)', Formatters.money(statement.totalCredits)),
          const Divider(),
          DetailRow(
            'Closing balance',
            null,
            valueWidget: MoneyText(statement.closingBalance, emphasise: true),
          ),
        ],
      ),
    );
  }
}

class _LedgerTile extends StatelessWidget {
  const _LedgerTile({required this.entry});

  final LedgerEntry entry;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final bool isDebit = entry.isDebit;
    final Color amountColour = isDebit ? theme.colorScheme.error : theme.colorScheme.primary;
    final String amount = isDebit ? entry.debitAmount : entry.creditAmount;

    return Card(
      child: ExpansionTile(
        shape: const Border(),
        collapsedShape: const Border(),
        tilePadding: const EdgeInsets.symmetric(horizontal: 16),
        childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
        leading: CircleAvatar(
          backgroundColor: amountColour.withValues(alpha: 0.12),
          child: Icon(
            isDebit ? Icons.arrow_upward : Icons.arrow_downward,
            size: 18,
            color: amountColour,
          ),
        ),
        title: Text(Formatters.humanise(entry.transactionType)),
        subtitle: Text(
          '${Formatters.date(entry.transactionDate)} · ${entry.transactionReference}',
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: theme.textTheme.bodySmall,
        ),
        trailing: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.end,
          children: <Widget>[
            Text(
              '${isDebit ? '+' : '-'}${Formatters.money(amount)}',
              style: theme.textTheme.bodyMedium?.copyWith(
                fontWeight: FontWeight.w700,
                color: amountColour,
                fontFeatures: const <FontFeature>[FontFeature.tabularFigures()],
              ),
            ),
            Text(
              Formatters.money(entry.totalOutstanding),
              style: theme.textTheme.labelSmall,
            ),
          ],
        ),
        children: <Widget>[
          if (entry.narrative != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Text(entry.narrative!, style: theme.textTheme.bodySmall),
            ),
          if (!isDebit) ...<Widget>[
            DetailRow('Principal', Formatters.money(entry.principalAllocation), dense: true),
            DetailRow('Interest', Formatters.money(entry.interestAllocation), dense: true),
            if ((double.tryParse(entry.feeAllocation) ?? 0) > 0)
              DetailRow('Fees', Formatters.money(entry.feeAllocation), dense: true),
            if ((double.tryParse(entry.penaltyAllocation) ?? 0) > 0)
              DetailRow('Penalty', Formatters.money(entry.penaltyAllocation), dense: true),
          ],
          DetailRow(
            'Outstanding principal',
            Formatters.money(entry.outstandingPrincipal),
            dense: true,
          ),
          DetailRow(
            'Total outstanding',
            Formatters.money(entry.totalOutstanding),
            dense: true,
          ),
          if (entry.isContraEntry)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(
                'This entry reverses an earlier transaction.',
                style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.error),
              ),
            ),
        ],
      ),
    );
  }
}
