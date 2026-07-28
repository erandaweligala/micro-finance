import 'package:flutter/material.dart';

import '../../../core/utils/formatters.dart';
import '../../../core/widgets/common_widgets.dart';
import '../domain/loan_schedule.dart';

/// The amortisation table.
///
/// On a phone each installment is a card that expands to show its breakdown -
/// a twelve-column table squeezed onto a 360px screen is unreadable. On a tablet
/// the same data renders as a real table, which is what a branch manager
/// reviewing a schedule actually wants.
class ScheduleTable extends StatelessWidget {
  const ScheduleTable({
    required this.installments,
    this.currency,
    this.showStatus = false,
    super.key,
  });

  final List<ScheduledInstallment> installments;
  final String? currency;

  /// Live loans carry a per-installment status; a quotation does not.
  final bool showStatus;

  @override
  Widget build(BuildContext context) {
    if (installments.isEmpty) {
      return const EmptyState(
        title: 'No installments',
        message: 'The schedule has not been generated yet.',
        icon: Icons.event_busy_outlined,
      );
    }

    return Responsive.isPhone(context)
        ? _buildCompactList(context)
        : _buildWideTable(context);
  }

  Widget _buildCompactList(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Column(
      children: installments.map((ScheduledInstallment row) {
        return ExpansionTile(
          tilePadding: EdgeInsets.zero,
          childrenPadding: const EdgeInsets.only(left: 8, right: 8, bottom: 8),
          shape: const Border(),
          collapsedShape: const Border(),
          leading: CircleAvatar(
            radius: 16,
            backgroundColor: theme.colorScheme.surfaceContainerHighest,
            child: Text(
              '${row.installmentNumber}',
              style: theme.textTheme.labelMedium,
            ),
          ),
          title: Text(
            Formatters.date(row.dueDate),
            style: theme.textTheme.bodyMedium,
          ),
          subtitle: row.graceInstallment
              ? Text('Interest only', style: theme.textTheme.bodySmall)
              : null,
          trailing: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.end,
            children: <Widget>[
              MoneyText(row.totalDue, currency: currency),
              if (showStatus && row.status != null)
                Padding(
                  padding: const EdgeInsets.only(top: 2),
                  child: StatusChip(row.status, compact: true),
                ),
            ],
          ),
          children: <Widget>[
            DetailRow('Principal', Formatters.money(row.principal, currency: currency), dense: true),
            DetailRow('Interest', Formatters.money(row.interest, currency: currency), dense: true),
            if (_isPositive(row.fee))
              DetailRow('Fee', Formatters.money(row.fee, currency: currency), dense: true),
            if (_isPositive(row.penalty))
              DetailRow('Penalty', Formatters.money(row.penalty, currency: currency), dense: true),
            DetailRow(
              'Balance after',
              Formatters.money(row.closingBalance, currency: currency),
              dense: true,
            ),
          ],
        );
      }).toList(),
    );
  }

  Widget _buildWideTable(BuildContext context) {
    final ThemeData theme = Theme.of(context);

    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: DataTable(
        headingTextStyle: theme.textTheme.labelMedium?.copyWith(fontWeight: FontWeight.w700),
        columnSpacing: 24,
        columns: <DataColumn>[
          const DataColumn(label: Text('#')),
          const DataColumn(label: Text('Due date')),
          const DataColumn(label: Text('Principal'), numeric: true),
          const DataColumn(label: Text('Interest'), numeric: true),
          const DataColumn(label: Text('Fees'), numeric: true),
          const DataColumn(label: Text('Total due'), numeric: true),
          const DataColumn(label: Text('Balance'), numeric: true),
          if (showStatus) const DataColumn(label: Text('Status')),
        ],
        rows: installments.map((ScheduledInstallment row) {
          return DataRow(
            cells: <DataCell>[
              DataCell(Text('${row.installmentNumber}')),
              DataCell(Text(Formatters.date(row.dueDate))),
              DataCell(Text(Formatters.money(row.principal))),
              DataCell(Text(Formatters.money(row.interest))),
              DataCell(Text(Formatters.money(_sum(row.fee, row.penalty)))),
              DataCell(
                Text(
                  Formatters.money(row.totalDue),
                  style: const TextStyle(fontWeight: FontWeight.w600),
                ),
              ),
              DataCell(Text(Formatters.money(row.closingBalance))),
              if (showStatus) DataCell(StatusChip(row.status, compact: true)),
            ],
          );
        }).toList(),
      ),
    );
  }

  bool _isPositive(String value) {
    final double? parsed = double.tryParse(value);
    return parsed != null && parsed > 0;
  }

  /// Display-only addition of two already-rounded amounts; no balance is derived
  /// from this, so it cannot introduce an accounting discrepancy.
  String _sum(String a, String b) {
    final double left = double.tryParse(a) ?? 0;
    final double right = double.tryParse(b) ?? 0;
    return (left + right).toStringAsFixed(2);
  }
}
