import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/utils/formatters.dart';
import '../../../core/utils/validators.dart';
import '../../../core/widgets/async_view.dart';
import '../../../core/widgets/common_widgets.dart';
import '../data/calculator_providers.dart';
import '../domain/loan_schedule.dart';
import 'schedule_table.dart';

/// The loan calculator.
///
/// The customer-facing question this screen answers is "what will I pay each
/// month, and what does it cost me in total?" - so the installment, total
/// interest and total repayable are shown first, and the full amortisation
/// table is available underneath for the borrower who wants to see the working.
class LoanCalculatorScreen extends ConsumerStatefulWidget {
  const LoanCalculatorScreen({this.productId, super.key});

  /// When supplied, terms are validated against that product's policy.
  final String? productId;

  @override
  ConsumerState<LoanCalculatorScreen> createState() => _LoanCalculatorScreenState();
}

class _LoanCalculatorScreenState extends ConsumerState<LoanCalculatorScreen> {
  final GlobalKey<FormState> _formKey = GlobalKey<FormState>();
  final TextEditingController _principalController =
      TextEditingController(text: '100000');
  final TextEditingController _rateController = TextEditingController(text: '12');
  final TextEditingController _installmentsController = TextEditingController(text: '12');

  String _frequency = 'MONTHLY';
  String _method = 'REDUCING_BALANCE';
  DateTime _disbursementDate = DateTime.now();
  DateTime? _firstRepaymentDate;
  int _gracePeriods = 0;

  /// The terms actually submitted. Held separately from the controllers so the
  /// calculator does not fire a request on every keystroke.
  CalculationInput? _submitted;

  static const Map<String, String> _frequencies = <String, String>{
    'WEEKLY': 'Weekly',
    'BIWEEKLY': 'Every 2 weeks',
    'MONTHLY': 'Monthly',
    'QUARTERLY': 'Quarterly',
    'SEMI_ANNUAL': 'Every 6 months',
    'ANNUAL': 'Yearly',
  };

  static const Map<String, String> _methods = <String, String>{
    'REDUCING_BALANCE': 'Reducing balance',
    'FLAT': 'Flat rate',
  };

  @override
  void dispose() {
    _principalController.dispose();
    _rateController.dispose();
    _installmentsController.dispose();
    super.dispose();
  }

  void _calculate() {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    FocusScope.of(context).unfocus();
    setState(() {
      _submitted = CalculationInput(
        productId: widget.productId,
        principal: _principalController.text.replaceAll(',', '').trim(),
        annualInterestRate: _rateController.text.trim(),
        numberOfInstallments: int.parse(_installmentsController.text.trim()),
        repaymentFrequency: _frequency,
        interestMethod: _method,
        disbursementDate: _disbursementDate,
        firstRepaymentDate: _firstRepaymentDate,
        graceType: _gracePeriods > 0 ? 'PRINCIPAL_GRACE' : null,
        gracePeriods: _gracePeriods,
      );
    });
  }

  Future<void> _pickDate({required bool isFirstRepayment}) async {
    final DateTime initial = isFirstRepayment
        ? (_firstRepaymentDate ?? _disbursementDate.add(const Duration(days: 30)))
        : _disbursementDate;
    final DateTime? picked = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: DateTime.now().subtract(const Duration(days: 365)),
      lastDate: DateTime.now().add(const Duration(days: 365 * 5)),
    );
    if (picked == null) {
      return;
    }
    setState(() {
      if (isFirstRepayment) {
        _firstRepaymentDate = picked;
      } else {
        _disbursementDate = picked;
        // A first repayment before disbursement is meaningless; drop it.
        if (_firstRepaymentDate != null && !_firstRepaymentDate!.isAfter(picked)) {
          _firstRepaymentDate = null;
        }
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Loan calculator')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: <Widget>[
            Responsive.constrain(_buildForm(context)),
            const SizedBox(height: 24),
            if (_submitted != null) Responsive.constrain(_buildResult(context, _submitted!)),
            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }

  Widget _buildForm(BuildContext context) {
    return SectionCard(
      title: 'Loan terms',
      child: Form(
        key: _formKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            TextFormField(
              controller: _principalController,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              inputFormatters: <TextInputFormatter>[
                FilteringTextInputFormatter.allow(RegExp(r'[0-9.,]')),
              ],
              decoration: const InputDecoration(
                labelText: 'Loan amount',
                prefixIcon: Icon(Icons.payments_outlined),
              ),
              validator: (String? value) => Validators.amount(value, field: 'Loan amount'),
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _rateController,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              inputFormatters: <TextInputFormatter>[
                FilteringTextInputFormatter.allow(RegExp(r'[0-9.]')),
              ],
              decoration: const InputDecoration(
                labelText: 'Annual interest rate',
                suffixText: '% p.a.',
                prefixIcon: Icon(Icons.percent),
              ),
              validator: Validators.rate,
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _installmentsController,
              keyboardType: TextInputType.number,
              inputFormatters: <TextInputFormatter>[FilteringTextInputFormatter.digitsOnly],
              decoration: const InputDecoration(
                labelText: 'Number of installments',
                prefixIcon: Icon(Icons.event_repeat_outlined),
              ),
              validator: Validators.installments,
            ),
            const SizedBox(height: 16),
            DropdownButtonFormField<String>(
              initialValue: _frequency,
              decoration: const InputDecoration(
                labelText: 'Repayment frequency',
                prefixIcon: Icon(Icons.calendar_month_outlined),
              ),
              items: _frequencies.entries
                  .map(
                    (MapEntry<String, String> entry) => DropdownMenuItem<String>(
                      value: entry.key,
                      child: Text(entry.value),
                    ),
                  )
                  .toList(),
              onChanged: (String? value) => setState(() => _frequency = value ?? 'MONTHLY'),
            ),
            const SizedBox(height: 16),
            DropdownButtonFormField<String>(
              initialValue: _method,
              decoration: const InputDecoration(
                labelText: 'Interest method',
                prefixIcon: Icon(Icons.functions_outlined),
              ),
              items: _methods.entries
                  .map(
                    (MapEntry<String, String> entry) => DropdownMenuItem<String>(
                      value: entry.key,
                      child: Text(entry.value),
                    ),
                  )
                  .toList(),
              onChanged: (String? value) =>
                  setState(() => _method = value ?? 'REDUCING_BALANCE'),
            ),
            const SizedBox(height: 8),
            Text(
              _method == 'REDUCING_BALANCE'
                  ? 'Interest is charged on the balance still owed, so it falls as the loan is repaid.'
                  : 'Interest is charged on the original amount for the whole term.',
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
            ),
            const SizedBox(height: 16),
            Row(
              children: <Widget>[
                Expanded(
                  child: _DateField(
                    label: 'Disbursement',
                    value: _disbursementDate,
                    onTap: () => _pickDate(isFirstRepayment: false),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _DateField(
                    label: 'First repayment',
                    value: _firstRepaymentDate,
                    hint: 'Automatic',
                    onTap: () => _pickDate(isFirstRepayment: true),
                    onClear: _firstRepaymentDate == null
                        ? null
                        : () => setState(() => _firstRepaymentDate = null),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Row(
              children: <Widget>[
                Expanded(
                  child: Text(
                    'Interest-only period',
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                ),
                IconButton(
                  onPressed: _gracePeriods == 0
                      ? null
                      : () => setState(() => _gracePeriods--),
                  icon: const Icon(Icons.remove_circle_outline),
                ),
                Text(
                  '$_gracePeriods',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                IconButton(
                  onPressed: _gracePeriods >= 12
                      ? null
                      : () => setState(() => _gracePeriods++),
                  icon: const Icon(Icons.add_circle_outline),
                ),
              ],
            ),
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: _calculate,
              icon: const Icon(Icons.calculate_outlined),
              label: const Text('Calculate'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildResult(BuildContext context, CalculationInput input) {
    final AsyncValue<LoanSchedule> schedule = ref.watch(loanScheduleProvider(input));

    return AsyncView<LoanSchedule>(
      value: schedule,
      onRetry: () => ref.invalidate(loanScheduleProvider(input)),
      data: (LoanSchedule result) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          _SummaryCard(schedule: result),
          const SizedBox(height: 16),
          SectionCard(
            title: 'Repayment schedule',
            trailing: Text(
              '${result.installments.length} installments',
              style: Theme.of(context).textTheme.bodySmall,
            ),
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            child: ScheduleTable(
              installments: result.installments,
              currency: result.currency,
            ),
          ),
        ],
      ),
    );
  }
}

/// The headline figures: what the borrower pays, and what it costs them.
class _SummaryCard extends StatelessWidget {
  const _SummaryCard({required this.schedule});

  final LoanSchedule schedule;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final String? currency = schedule.currency;

    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          Text(
            'Repayment per installment',
            style: theme.textTheme.labelMedium
                ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
          ),
          const SizedBox(height: 4),
          Text(
            Formatters.money(schedule.installmentAmount, currency: currency),
            style: theme.textTheme.displaySmall?.copyWith(
              fontWeight: FontWeight.w700,
              color: theme.colorScheme.primary,
              fontFeatures: const <FontFeature>[FontFeature.tabularFigures()],
            ),
          ),
          const Divider(height: 32),
          DetailRow('Loan amount', Formatters.money(schedule.principal, currency: currency)),
          DetailRow('Total interest', Formatters.money(schedule.totalInterest, currency: currency)),
          if (double.tryParse(schedule.totalFees) != null &&
              double.parse(schedule.totalFees) > 0)
            DetailRow('Fees', Formatters.money(schedule.totalFees, currency: currency)),
          DetailRow(
            'Total repayable',
            null,
            valueWidget: MoneyText(schedule.totalRepayable, currency: currency, emphasise: true),
          ),
          if (schedule.netDisbursedAmount != schedule.principal)
            DetailRow(
              'Amount you receive',
              Formatters.money(schedule.netDisbursedAmount, currency: currency),
            ),
          const Divider(height: 24),
          DetailRow('Method', Formatters.humanise(schedule.interestMethod), dense: true),
          DetailRow('Frequency', Formatters.humanise(schedule.repaymentFrequency), dense: true),
          DetailRow('First payment', Formatters.date(schedule.firstDueDate), dense: true),
          DetailRow('Final payment', Formatters.date(schedule.maturityDate), dense: true),
          if (schedule.hasBrokenPeriodInterest) ...<Widget>[
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: theme.colorScheme.tertiaryContainer,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text(
                'The first payment includes '
                '${Formatters.money(schedule.brokenPeriodInterest, currency: currency)} of extra '
                'interest, because the gap before it is longer than a normal period.',
                style: theme.textTheme.bodySmall
                    ?.copyWith(color: theme.colorScheme.onTertiaryContainer),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _DateField extends StatelessWidget {
  const _DateField({
    required this.label,
    required this.value,
    required this.onTap,
    this.hint,
    this.onClear,
  });

  final String label;
  final DateTime? value;
  final VoidCallback onTap;
  final String? hint;
  final VoidCallback? onClear;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: InputDecorator(
        decoration: InputDecoration(
          labelText: label,
          suffixIcon: onClear != null
              ? IconButton(icon: const Icon(Icons.clear, size: 18), onPressed: onClear)
              : const Icon(Icons.calendar_today_outlined, size: 18),
        ),
        child: Text(value == null ? (hint ?? '-') : Formatters.date(value)),
      ),
    );
  }
}
