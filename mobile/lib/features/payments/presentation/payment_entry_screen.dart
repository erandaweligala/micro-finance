import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/error/api_exception.dart';
import '../../../core/network/api_client.dart';
import '../../../core/router/app_router.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/utils/validators.dart';
import '../../../core/widgets/async_view.dart';
import '../../../core/widgets/common_widgets.dart';
import '../../loan_accounts/data/loan_account_repository.dart';
import '../../loan_accounts/domain/loan_account.dart';
import '../data/payment_repository.dart';
import '../domain/payment.dart';

/// Taking a repayment.
///
/// The idempotency key is generated once when the screen opens and reused for
/// every retry of that payment. This is the single most important detail on this
/// screen: without it, a cashier who taps twice on a slow connection takes the
/// customer's money twice.
class PaymentEntryScreen extends ConsumerStatefulWidget {
  const PaymentEntryScreen({required this.loanAccountId, super.key});

  final String loanAccountId;

  @override
  ConsumerState<PaymentEntryScreen> createState() => _PaymentEntryScreenState();
}

class _PaymentEntryScreenState extends ConsumerState<PaymentEntryScreen> {
  final GlobalKey<FormState> _formKey = GlobalKey<FormState>();
  final TextEditingController _amount = TextEditingController();
  final TextEditingController _reference = TextEditingController();
  final TextEditingController _narrative = TextEditingController();

  /// Created once per payment attempt, not per request.
  late String _idempotencyKey = ApiClient.newIdempotencyKey();

  String _method = 'CASH';
  DateTime _valueDate = DateTime.now();
  bool _isSubmitting = false;
  String? _error;

  @override
  void dispose() {
    _amount.dispose();
    _reference.dispose();
    _narrative.dispose();
    super.dispose();
  }

  Future<void> _submit(LoanAccount account) async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    final bool? confirmed = await _confirm(account);
    if (confirmed != true) {
      return;
    }

    setState(() {
      _isSubmitting = true;
      _error = null;
    });

    try {
      final Payment payment = await ref.read(paymentRepositoryProvider).capture(
            idempotencyKey: _idempotencyKey,
            loanAccountId: widget.loanAccountId,
            amount: _amount.text.replaceAll(',', '').trim(),
            method: _method,
            valueDate: _valueDate,
            externalReference:
                _reference.text.trim().isEmpty ? null : _reference.text.trim(),
            narrative: _narrative.text.trim().isEmpty ? null : _narrative.text.trim(),
          );

      ref.invalidate(loanAccountProvider(widget.loanAccountId));
      ref.invalidate(loanScheduleProvider(widget.loanAccountId));

      if (mounted) {
        context.go(Routes.receipt(payment.id));
      }
    } on ApiException catch (error) {
      if (mounted) {
        setState(() {
          _error = error.message;
          // A conflict means this key was already used with a different payload,
          // so the next attempt must be a genuinely new payment.
          if (error.isConflict) {
            _idempotencyKey = ApiClient.newIdempotencyKey();
          }
        });
      }
    } finally {
      if (mounted) {
        setState(() => _isSubmitting = false);
      }
    }
  }

  Future<bool?> _confirm(LoanAccount account) {
    return showDialog<bool>(
      context: context,
      builder: (BuildContext context) => AlertDialog(
        title: const Text('Confirm payment'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Text(
              Formatters.money(
                _amount.text.replaceAll(',', '').trim(),
                currency: account.currency,
              ),
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            const SizedBox(height: 8),
            Text('Loan ${account.accountNumber}'),
            Text('${Payment.methodLabels[_method]} · ${Formatters.date(_valueDate)}'),
            const SizedBox(height: 12),
            const Text(
              'The payment will be allocated to penalties, fees, interest and principal '
              'according to your institution\'s policy.',
            ),
          ],
        ),
        actions: <Widget>[
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Take payment'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final AsyncValue<LoanAccount> account =
        ref.watch(loanAccountProvider(widget.loanAccountId));

    return Scaffold(
      appBar: AppBar(title: const Text('Record a payment')),
      body: SafeArea(
        child: AsyncView<LoanAccount>(
          value: account,
          onRetry: () => ref.invalidate(loanAccountProvider(widget.loanAccountId)),
          data: (LoanAccount data) => Responsive.constrain(
            Form(
              key: _formKey,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: <Widget>[
                  SectionCard(
                    child: Column(
                      children: <Widget>[
                        DetailRow('Loan', data.accountNumber),
                        DetailRow(
                          'Total outstanding',
                          null,
                          valueWidget: MoneyText(
                            data.totalOutstanding,
                            currency: data.currency,
                            emphasise: true,
                          ),
                        ),
                        DetailRow(
                          'Installment',
                          Formatters.money(data.installmentAmount, currency: data.currency),
                        ),
                        if (data.isInArrears)
                          DetailRow(
                            'Overdue',
                            Formatters.money(data.overdueAmount, currency: data.currency),
                          ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                  SectionCard(
                    title: 'Payment',
                    child: Column(
                      children: <Widget>[
                        TextFormField(
                          controller: _amount,
                          autofocus: true,
                          keyboardType: const TextInputType.numberWithOptions(decimal: true),
                          inputFormatters: <TextInputFormatter>[
                            FilteringTextInputFormatter.allow(RegExp(r'[0-9.,]')),
                          ],
                          style: Theme.of(context).textTheme.headlineSmall,
                          decoration: InputDecoration(
                            labelText: 'Amount received',
                            prefixText: data.currency == null ? null : '${data.currency} ',
                          ),
                          validator: (String? value) =>
                              Validators.amount(value, field: 'Amount'),
                        ),
                        const SizedBox(height: 8),
                        Wrap(
                          spacing: 8,
                          children: <Widget>[
                            ActionChip(
                              label: const Text('Installment'),
                              onPressed: () =>
                                  setState(() => _amount.text = data.installmentAmount),
                            ),
                            if (data.isInArrears)
                              ActionChip(
                                label: const Text('Overdue'),
                                onPressed: () =>
                                    setState(() => _amount.text = data.overdueAmount),
                              ),
                            ActionChip(
                              label: const Text('Full balance'),
                              onPressed: () =>
                                  setState(() => _amount.text = data.totalOutstanding),
                            ),
                          ],
                        ),
                        const SizedBox(height: 16),
                        DropdownButtonFormField<String>(
                          initialValue: _method,
                          decoration: const InputDecoration(labelText: 'Method'),
                          items: Payment.methodLabels.entries
                              .map(
                                (MapEntry<String, String> entry) => DropdownMenuItem<String>(
                                  value: entry.key,
                                  child: Text(entry.value),
                                ),
                              )
                              .toList(),
                          onChanged: (String? value) =>
                              setState(() => _method = value ?? 'CASH'),
                        ),
                        const SizedBox(height: 12),
                        TextFormField(
                          controller: _reference,
                          decoration: InputDecoration(
                            labelText: _method == 'CASH'
                                ? 'Receipt reference (optional)'
                                : 'Transaction reference',
                          ),
                          validator: (String? value) => _method == 'CASH'
                              ? null
                              : Validators.required(value, field: 'Transaction reference'),
                        ),
                        const SizedBox(height: 12),
                        InkWell(
                          onTap: () async {
                            final DateTime? picked = await showDatePicker(
                              context: context,
                              initialDate: _valueDate,
                              firstDate: data.disbursementDate ??
                                  DateTime.now().subtract(const Duration(days: 365)),
                              // Never in the future: a payment cannot be received
                              // before it happens.
                              lastDate: DateTime.now(),
                            );
                            if (picked != null) {
                              setState(() => _valueDate = picked);
                            }
                          },
                          child: InputDecorator(
                            decoration: const InputDecoration(labelText: 'Value date'),
                            child: Text(Formatters.date(_valueDate)),
                          ),
                        ),
                        const SizedBox(height: 12),
                        TextFormField(
                          controller: _narrative,
                          decoration: const InputDecoration(labelText: 'Note (optional)'),
                        ),
                      ],
                    ),
                  ),
                  if (_error != null) ...<Widget>[
                    const SizedBox(height: 16),
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: Theme.of(context).colorScheme.errorContainer,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Text(_error!),
                    ),
                  ],
                  const SizedBox(height: 24),
                  FilledButton.icon(
                    onPressed: _isSubmitting ? null : () => _submit(data),
                    icon: _isSubmitting
                        ? const SizedBox(
                            height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2))
                        : const Icon(Icons.check),
                    label: Text(_isSubmitting ? 'Posting...' : 'Take payment'),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Retrying a timed-out payment is safe: the same receipt is returned '
                    'rather than a second payment being taken.',
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                  const SizedBox(height: 32),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
