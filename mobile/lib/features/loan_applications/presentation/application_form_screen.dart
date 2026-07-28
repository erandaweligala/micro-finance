import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/error/api_exception.dart';
import '../../../core/router/app_router.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/utils/validators.dart';
import '../../../core/widgets/async_view.dart';
import '../../../core/widgets/common_widgets.dart';
import '../../customers/data/customer_repository.dart';
import '../../customers/domain/customer.dart';
import '../../loan_calculator/data/calculator_providers.dart';
import '../../loan_calculator/domain/loan_schedule.dart';
import '../data/application_repository.dart';
import '../domain/loan_application.dart';

/// Creating a loan application.
///
/// The live quotation underneath the form is deliberate: a loan officer should
/// see the installment the customer will actually pay *before* submitting, not
/// after. It is produced by the same backend engine that will generate the real
/// schedule at disbursement.
class ApplicationFormScreen extends ConsumerStatefulWidget {
  const ApplicationFormScreen({this.customerId, super.key});

  final String? customerId;

  @override
  ConsumerState<ApplicationFormScreen> createState() => _ApplicationFormScreenState();
}

class _ApplicationFormScreenState extends ConsumerState<ApplicationFormScreen> {
  final GlobalKey<FormState> _formKey = GlobalKey<FormState>();
  final TextEditingController _amount = TextEditingController();
  final TextEditingController _installments = TextEditingController();
  final TextEditingController _rate = TextEditingController();
  final TextEditingController _purpose = TextEditingController();

  String? _customerId;
  LoanProduct? _product;
  CalculationInput? _quotation;
  bool _isSubmitting = false;
  String? _formError;

  @override
  void initState() {
    super.initState();
    _customerId = widget.customerId;
  }

  @override
  void dispose() {
    _amount.dispose();
    _installments.dispose();
    _rate.dispose();
    _purpose.dispose();
    super.dispose();
  }

  /// Applies the product's defaults so the officer starts from a valid position.
  void _onProductSelected(LoanProduct product) {
    setState(() {
      _product = product;
      _amount.text = product.minPrincipal;
      _installments.text = '${product.defaultInstallments}';
      _rate.text = product.defaultAnnualRate;
      _quotation = null;
    });
  }

  void _quote() {
    if (_product == null || !_formKey.currentState!.validate()) {
      return;
    }
    setState(() {
      _quotation = CalculationInput(
        productId: _product!.id,
        principal: _amount.text.replaceAll(',', '').trim(),
        annualInterestRate: _rate.text.trim(),
        numberOfInstallments: int.parse(_installments.text.trim()),
        repaymentFrequency: _product!.repaymentFrequency,
        interestMethod: _product!.interestMethod,
        disbursementDate: DateTime.now(),
      );
    });
  }

  Future<void> _submit() async {
    if (_customerId == null) {
      setState(() => _formError = 'Choose a customer first');
      return;
    }
    if (_product == null || !_formKey.currentState!.validate()) {
      return;
    }
    setState(() {
      _isSubmitting = true;
      _formError = null;
    });

    try {
      final LoanApplication created =
          await ref.read(applicationRepositoryProvider).create(<String, dynamic>{
        'customerId': _customerId,
        'productId': _product!.id,
        'requestedAmount': _amount.text.replaceAll(',', '').trim(),
        'requestedInstallments': int.parse(_installments.text.trim()),
        'annualInterestRate': _rate.text.trim(),
        'interestMethod': _product!.interestMethod,
        'repaymentFrequency': _product!.repaymentFrequency,
        'purpose': _purpose.text.trim().isEmpty ? null : _purpose.text.trim(),
      });
      ref.invalidate(applicationListProvider);
      if (mounted) {
        showMessage(context, 'Application ${created.applicationNumber} created');
        context.go(Routes.applicationDetail(created.id));
      }
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _formError = error.message);
      }
    } finally {
      if (mounted) {
        setState(() => _isSubmitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final AsyncValue<List<LoanProduct>> products = ref.watch(loanProductsProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('New loan application')),
      body: SafeArea(
        child: Responsive.constrain(
          AsyncView<List<LoanProduct>>(
            value: products,
            onRetry: () => ref.invalidate(loanProductsProvider),
            isEmpty: (List<LoanProduct> list) => list.isEmpty,
            emptyTitle: 'No active loan products',
            emptyMessage: 'An administrator must configure and activate a product before '
                'loans can be applied for.',
            emptyIcon: Icons.inventory_2_outlined,
            data: (List<LoanProduct> list) => Form(
              key: _formKey,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: <Widget>[
                  if (_formError != null) ...<Widget>[
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: Theme.of(context).colorScheme.errorContainer,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Text(_formError!),
                    ),
                    const SizedBox(height: 16),
                  ],
                  SectionCard(
                    title: 'Customer',
                    child: _CustomerPicker(
                      selectedId: _customerId,
                      onSelected: (String id) => setState(() => _customerId = id),
                    ),
                  ),
                  const SizedBox(height: 16),
                  SectionCard(
                    title: 'Product and terms',
                    child: Column(
                      children: <Widget>[
                        DropdownButtonFormField<LoanProduct>(
                          initialValue: _product,
                          decoration: const InputDecoration(labelText: 'Loan product'),
                          items: list
                              .map(
                                (LoanProduct product) => DropdownMenuItem<LoanProduct>(
                                  value: product,
                                  child: Text(product.name),
                                ),
                              )
                              .toList(),
                          validator: (LoanProduct? value) =>
                              value == null ? 'Choose a loan product' : null,
                          onChanged: (LoanProduct? product) {
                            if (product != null) {
                              _onProductSelected(product);
                            }
                          },
                        ),
                        if (_product != null) ...<Widget>[
                          const SizedBox(height: 8),
                          Text(
                            'Between ${Formatters.money(_product!.minPrincipal, currency: _product!.currency)} '
                            'and ${Formatters.money(_product!.maxPrincipal, currency: _product!.currency)}, '
                            'over ${_product!.minInstallments}-${_product!.maxInstallments} installments.',
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                        ],
                        const SizedBox(height: 12),
                        TextFormField(
                          controller: _amount,
                          keyboardType: const TextInputType.numberWithOptions(decimal: true),
                          inputFormatters: <TextInputFormatter>[
                            FilteringTextInputFormatter.allow(RegExp(r'[0-9.,]')),
                          ],
                          decoration: const InputDecoration(labelText: 'Amount requested'),
                          validator: (String? value) => Validators.amount(
                            value,
                            field: 'Amount',
                            min: double.tryParse(_product?.minPrincipal ?? '0'),
                            max: double.tryParse(_product?.maxPrincipal ?? '0'),
                          ),
                        ),
                        const SizedBox(height: 12),
                        TextFormField(
                          controller: _installments,
                          keyboardType: TextInputType.number,
                          inputFormatters: <TextInputFormatter>[
                            FilteringTextInputFormatter.digitsOnly,
                          ],
                          decoration: const InputDecoration(labelText: 'Installments'),
                          validator: Validators.installments,
                        ),
                        const SizedBox(height: 12),
                        TextFormField(
                          controller: _rate,
                          keyboardType: const TextInputType.numberWithOptions(decimal: true),
                          decoration: const InputDecoration(
                            labelText: 'Annual interest rate',
                            suffixText: '%',
                          ),
                          validator: Validators.rate,
                        ),
                        const SizedBox(height: 12),
                        TextFormField(
                          controller: _purpose,
                          maxLines: 2,
                          decoration: const InputDecoration(labelText: 'Purpose of the loan'),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                  OutlinedButton.icon(
                    onPressed: _quote,
                    icon: const Icon(Icons.calculate_outlined),
                    label: const Text('Preview repayments'),
                  ),
                  if (_quotation != null) ...<Widget>[
                    const SizedBox(height: 16),
                    _QuotationPreview(input: _quotation!),
                  ],
                  const SizedBox(height: 24),
                  FilledButton(
                    onPressed: _isSubmitting ? null : _submit,
                    child: _isSubmitting
                        ? const SizedBox(
                            height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2))
                        : const Text('Create application'),
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

/// Inline customer search, so an officer can start an application without
/// leaving the form to go and find the customer first.
class _CustomerPicker extends ConsumerStatefulWidget {
  const _CustomerPicker({required this.selectedId, required this.onSelected});

  final String? selectedId;
  final ValueChanged<String> onSelected;

  @override
  ConsumerState<_CustomerPicker> createState() => _CustomerPickerState();
}

class _CustomerPickerState extends ConsumerState<_CustomerPicker> {
  @override
  Widget build(BuildContext context) {
    if (widget.selectedId != null) {
      final AsyncValue<Customer> customer = ref.watch(customerProvider(widget.selectedId!));
      return customer.when(
        loading: () => const LinearProgressIndicator(),
        error: (Object error, StackTrace stack) => Text(
          'Could not load that customer',
          style: TextStyle(color: Theme.of(context).colorScheme.error),
        ),
        data: (Customer data) => ListTile(
          contentPadding: EdgeInsets.zero,
          leading: CircleAvatar(child: Text(Formatters.initials(data.fullName))),
          title: Text(data.fullName),
          subtitle: Text(data.customerNumber),
          trailing: data.eligibleForLending
              ? const Icon(Icons.check_circle_outline)
              : Tooltip(
                  message: 'KYC is not verified',
                  child: Icon(Icons.warning_amber_outlined,
                      color: Theme.of(context).colorScheme.error),
                ),
        ),
      );
    }

    final AsyncValue<dynamic> customers = ref.watch(customerListProvider);
    return customers.when(
      loading: () => const LinearProgressIndicator(),
      error: (Object error, StackTrace stack) => const Text('Could not load customers'),
      data: (dynamic page) {
        final List<Customer> eligible = (page.content as List<Customer>)
            .where((Customer customer) => customer.eligibleForLending)
            .toList();
        if (eligible.isEmpty) {
          return const Text(
            'No KYC-verified customers are available. Complete a customer\'s KYC first.',
          );
        }
        return DropdownButtonFormField<String>(
          decoration: const InputDecoration(labelText: 'Customer'),
          items: eligible
              .map(
                (Customer customer) => DropdownMenuItem<String>(
                  value: customer.id,
                  child: Text('${customer.fullName} (${customer.customerNumber})'),
                ),
              )
              .toList(),
          onChanged: (String? value) {
            if (value != null) {
              widget.onSelected(value);
            }
          },
        );
      },
    );
  }
}

class _QuotationPreview extends ConsumerWidget {
  const _QuotationPreview({required this.input});

  final CalculationInput input;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final AsyncValue<LoanSchedule> schedule = ref.watch(loanScheduleProvider(input));

    return AsyncView<LoanSchedule>(
      value: schedule,
      onRetry: () => ref.invalidate(loanScheduleProvider(input)),
      data: (LoanSchedule result) => SectionCard(
        title: 'Repayment preview',
        child: Column(
          children: <Widget>[
            DetailRow(
              'Per installment',
              null,
              valueWidget: MoneyText(
                result.installmentAmount,
                currency: result.currency,
                emphasise: true,
              ),
            ),
            DetailRow('Total interest',
                Formatters.money(result.totalInterest, currency: result.currency)),
            DetailRow('Total repayable',
                Formatters.money(result.totalRepayable, currency: result.currency)),
            DetailRow('Final payment', Formatters.date(result.maturityDate)),
          ],
        ),
      ),
    );
  }
}
