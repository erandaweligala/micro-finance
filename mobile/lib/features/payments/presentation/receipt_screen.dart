import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/error/api_exception.dart';
import '../../../core/providers.dart';
import '../../../core/router/app_router.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/async_view.dart';
import '../../../core/widgets/common_widgets.dart';
import '../../auth/domain/session.dart';
import '../../loan_accounts/data/loan_account_repository.dart';
import '../data/payment_repository.dart';
import '../domain/payment.dart';

/// The receipt for a payment, and the confirmation screen after taking one.
///
/// It shows the allocation the loan account actually applied - penalties, fees,
/// interest, principal - because "where did my money go?" is the question a
/// borrower asks when the balance does not fall by the amount they paid.
class ReceiptScreen extends ConsumerStatefulWidget {
  const ReceiptScreen({required this.paymentId, super.key});

  final String paymentId;

  @override
  ConsumerState<ReceiptScreen> createState() => _ReceiptScreenState();
}

class _ReceiptScreenState extends ConsumerState<ReceiptScreen> {
  bool _isReversing = false;

  Future<void> _reverse(Payment payment) async {
    final TextEditingController controller = TextEditingController();
    final String? reason = await showDialog<String>(
      context: context,
      builder: (BuildContext context) => AlertDialog(
        title: const Text('Reverse this payment'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            const Text(
              'The loan will be restored to its state before this payment. The receipt '
              'stays on the customer\'s ledger marked as reversed.',
            ),
            const SizedBox(height: 12),
            TextField(
              controller: controller,
              autofocus: true,
              maxLines: 2,
              decoration: const InputDecoration(labelText: 'Reason'),
            ),
          ],
        ),
        actions: <Widget>[
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(controller.text.trim()),
            child: const Text('Reverse'),
          ),
        ],
      ),
    );
    controller.dispose();

    if (reason == null || reason.isEmpty) {
      return;
    }

    setState(() => _isReversing = true);
    try {
      await ref.read(paymentRepositoryProvider).reverse(widget.paymentId, reason);
      ref.invalidate(paymentProvider(widget.paymentId));
      ref.invalidate(loanAccountProvider(payment.loanAccountId));
      if (mounted) {
        showMessage(context, 'Payment reversed');
      }
    } on ApiException catch (error) {
      if (mounted) {
        showMessage(context, error.message, isError: true);
      }
    } finally {
      if (mounted) {
        setState(() => _isReversing = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final AsyncValue<Payment> payment = ref.watch(paymentProvider(widget.paymentId));
    final Session? session = ref.watch(currentSessionProvider);
    final ThemeData theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Receipt')),
      body: AsyncView<Payment>(
        value: payment,
        onRetry: () => ref.invalidate(paymentProvider(widget.paymentId)),
        data: (Payment data) => Responsive.constrain(
          ListView(
            padding: const EdgeInsets.all(16),
            children: <Widget>[
              if (!data.isReversed)
                Column(
                  children: <Widget>[
                    Icon(Icons.check_circle, size: 56, color: theme.colorScheme.primary),
                    const SizedBox(height: 12),
                    Text('Payment received', style: theme.textTheme.titleLarge),
                    const SizedBox(height: 4),
                    Text(
                      Formatters.money(data.amount, currency: data.currency),
                      style: theme.textTheme.headlineMedium?.copyWith(
                        fontWeight: FontWeight.w700,
                        fontFeatures: const <FontFeature>[FontFeature.tabularFigures()],
                      ),
                    ),
                  ],
                )
              else
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.errorContainer,
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Column(
                    children: <Widget>[
                      Icon(Icons.undo, color: theme.colorScheme.onErrorContainer),
                      const SizedBox(height: 8),
                      Text(
                        'This payment was reversed',
                        style: theme.textTheme.titleSmall
                            ?.copyWith(color: theme.colorScheme.onErrorContainer),
                      ),
                      if (data.reversalReason != null)
                        Text(
                          data.reversalReason!,
                          textAlign: TextAlign.center,
                          style: theme.textTheme.bodySmall
                              ?.copyWith(color: theme.colorScheme.onErrorContainer),
                        ),
                    ],
                  ),
                ),
              const SizedBox(height: 24),
              SectionCard(
                title: 'Receipt ${data.receiptNumber}',
                trailing: StatusChip(data.status, compact: true),
                child: Column(
                  children: <Widget>[
                    DetailRow('Loan', data.loanAccountNumber),
                    DetailRow('Method', Payment.methodLabels[data.method] ?? data.method),
                    DetailRow('Reference', data.externalReference),
                    DetailRow('Value date', Formatters.date(data.valueDate)),
                    DetailRow('Received', Formatters.dateTime(data.createdAt)),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              SectionCard(
                title: 'How this payment was applied',
                child: Column(
                  children: <Widget>[
                    DetailRow(
                      'Penalties',
                      Formatters.money(data.penaltyAllocated, currency: data.currency),
                    ),
                    DetailRow(
                      'Fees',
                      Formatters.money(data.feeAllocated, currency: data.currency),
                    ),
                    DetailRow(
                      'Interest',
                      Formatters.money(data.interestAllocated, currency: data.currency),
                    ),
                    DetailRow(
                      'Principal',
                      Formatters.money(data.principalAllocated, currency: data.currency),
                    ),
                    if (data.hasExcess)
                      DetailRow(
                        'Held as credit',
                        Formatters.money(data.excessAmount, currency: data.currency),
                      ),
                    const Divider(),
                    DetailRow(
                      'Balance after',
                      null,
                      valueWidget: MoneyText(
                        data.totalOutstandingAfter,
                        currency: data.currency,
                        emphasise: true,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),
              FilledButton.icon(
                onPressed: () => context.go(Routes.loanDetail(data.loanAccountId)),
                icon: const Icon(Icons.account_balance_wallet_outlined),
                label: const Text('Back to the loan'),
              ),
              if ((session?.canReversePayments ?? false) && !data.isReversed) ...<Widget>[
                const SizedBox(height: 8),
                OutlinedButton.icon(
                  onPressed: _isReversing ? null : () => _reverse(data),
                  icon: const Icon(Icons.undo),
                  label: const Text('Reverse this payment'),
                ),
              ],
              const SizedBox(height: 32),
            ],
          ),
        ),
      ),
    );
  }
}
