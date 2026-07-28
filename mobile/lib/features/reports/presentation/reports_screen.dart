import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/utils/formatters.dart';
import '../../../core/widgets/async_view.dart';
import '../../../core/widgets/common_widgets.dart';
import '../../dashboard/data/dashboard_repository.dart';
import '../../dashboard/domain/dashboard_summary.dart';

/// Portfolio reporting.
///
/// Portfolio at risk is shown as a share of outstanding principal, because that
/// is the figure funders and regulators ask for; presenting a count of late
/// loans under the same label would flatter a book with many small arrears.
class ReportsScreen extends ConsumerWidget {
  const ReportsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final AsyncValue<PortfolioSummary> portfolio = ref.watch(portfolioProvider);
    final AsyncValue<ArrearsAging> arrears = ref.watch(arrearsAgingProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Reports'),
        actions: <Widget>[
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Refresh',
            onPressed: () {
              ref.invalidate(portfolioProvider);
              ref.invalidate(arrearsAgingProvider);
            },
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(portfolioProvider);
          ref.invalidate(arrearsAgingProvider);
        },
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: <Widget>[
            AsyncView<PortfolioSummary>(
              value: portfolio,
              onRetry: () => ref.invalidate(portfolioProvider),
              data: (PortfolioSummary data) => _PortfolioCard(summary: data),
            ),
            const SizedBox(height: 16),
            AsyncView<ArrearsAging>(
              value: arrears,
              onRetry: () => ref.invalidate(arrearsAgingProvider),
              data: (ArrearsAging data) => _ArrearsCard(aging: data),
            ),
            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }
}

class _PortfolioCard extends StatelessWidget {
  const _PortfolioCard({required this.summary});

  final PortfolioSummary summary;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final double par30 = double.tryParse(summary.par30Percentage) ?? 0;
    // 5% is the threshold most microfinance funders treat as a warning sign.
    final bool isElevated = par30 > 5;

    return SectionCard(
      title: 'Portfolio',
      child: Column(
        children: <Widget>[
          DetailRow('Active loans', '${summary.activeLoans}'),
          DetailRow('In arrears', '${summary.overdueLoans}'),
          DetailRow('Closed loans', '${summary.closedLoans}'),
          const Divider(height: 24),
          DetailRow(
            'Outstanding principal',
            null,
            valueWidget: MoneyText(summary.outstandingPrincipal, emphasise: true),
          ),
          DetailRow('Total collected', Formatters.money(summary.totalCollected)),
          DetailRow('Principal at risk (30d)', Formatters.money(summary.principalAtRisk30)),
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: isElevated
                  ? theme.colorScheme.errorContainer
                  : theme.colorScheme.primaryContainer,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(
              children: <Widget>[
                Icon(
                  isElevated ? Icons.trending_up : Icons.check_circle_outline,
                  color: isElevated
                      ? theme.colorScheme.onErrorContainer
                      : theme.colorScheme.onPrimaryContainer,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Text(
                        'Portfolio at risk over 30 days',
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: isElevated
                              ? theme.colorScheme.onErrorContainer
                              : theme.colorScheme.onPrimaryContainer,
                        ),
                      ),
                      Text(
                        Formatters.percent(summary.par30Percentage),
                        style: theme.textTheme.titleLarge?.copyWith(
                          fontWeight: FontWeight.w700,
                          color: isElevated
                              ? theme.colorScheme.onErrorContainer
                              : theme.colorScheme.onPrimaryContainer,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _ArrearsCard extends StatelessWidget {
  const _ArrearsCard({required this.aging});

  final ArrearsAging aging;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final double total = double.tryParse(aging.totalOutstanding) ?? 0;

    return SectionCard(
      title: 'Arrears ageing',
      child: Column(
        children: ArrearsAging.bucketLabels.entries.map((MapEntry<String, String> entry) {
          final int count = aging.loanCounts[entry.key] ?? 0;
          final String principal = aging.outstandingPrincipal[entry.key] ?? '0';
          final double amount = double.tryParse(principal) ?? 0;
          final double share = total > 0 ? amount / total : 0;
          final bool isRisk = entry.key != 'CURRENT';

          return Padding(
            padding: const EdgeInsets.symmetric(vertical: 8),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                Row(
                  children: <Widget>[
                    Expanded(child: Text(entry.value, style: theme.textTheme.bodyMedium)),
                    Text('$count loans', style: theme.textTheme.bodySmall),
                    const SizedBox(width: 12),
                    MoneyText(principal),
                  ],
                ),
                const SizedBox(height: 6),
                ClipRRect(
                  borderRadius: BorderRadius.circular(4),
                  child: LinearProgressIndicator(
                    value: share.clamp(0.0, 1.0),
                    minHeight: 6,
                    backgroundColor: theme.colorScheme.surfaceContainerHighest,
                    valueColor: AlwaysStoppedAnimation<Color>(
                      isRisk ? theme.colorScheme.error : theme.colorScheme.primary,
                    ),
                  ),
                ),
              ],
            ),
          );
        }).toList(),
      ),
    );
  }
}
