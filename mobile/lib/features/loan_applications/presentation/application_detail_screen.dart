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
import '../data/application_repository.dart';
import '../domain/loan_application.dart';

/// Application detail, approval and disbursement.
///
/// The available actions come from the application's own state and the user's
/// role, so the screen cannot offer something the backend would refuse - and an
/// approver always sees how many further approvals are still required.
class ApplicationDetailScreen extends ConsumerStatefulWidget {
  const ApplicationDetailScreen({required this.applicationId, super.key});

  final String applicationId;

  @override
  ConsumerState<ApplicationDetailScreen> createState() => _ApplicationDetailScreenState();
}

class _ApplicationDetailScreenState extends ConsumerState<ApplicationDetailScreen> {
  bool _isWorking = false;

  Future<void> _run(Future<void> Function() action) async {
    setState(() => _isWorking = true);
    try {
      await action();
      ref.invalidate(applicationProvider(widget.applicationId));
      ref.invalidate(applicationListProvider);
    } on ApiException catch (error) {
      if (mounted) {
        showMessage(context, error.message, isError: true);
      }
    } finally {
      if (mounted) {
        setState(() => _isWorking = false);
      }
    }
  }

  Future<void> _submit() => _run(() async {
        await ref.read(applicationRepositoryProvider).submit(widget.applicationId);
        if (mounted) {
          showMessage(context, 'Submitted for approval');
        }
      });

  Future<void> _approve(LoanApplication application) async {
    final _ApprovalResult? result = await showModalBottomSheet<_ApprovalResult>(
      context: context,
      isScrollControlled: true,
      builder: (BuildContext context) => _ApprovalSheet(application: application),
    );
    if (result == null) {
      return;
    }
    await _run(() async {
      final LoanApplication updated =
          await ref.read(applicationRepositoryProvider).approve(
                widget.applicationId,
                comment: result.comment,
                approvedAmount: result.approvedAmount,
                approvedInstallments: result.approvedInstallments,
              );
      if (mounted) {
        showMessage(
          context,
          updated.isApproved
              ? 'Approved and ready to disburse'
              : 'Approval recorded (${updated.approvalProgress})',
        );
      }
    });
  }

  Future<void> _reject() async {
    final String? reason = await _promptForText(
      title: 'Reject this application',
      hint: 'The reason is recorded on the approval history',
    );
    if (reason == null) {
      return;
    }
    await _run(() async {
      await ref.read(applicationRepositoryProvider).reject(widget.applicationId, reason);
      if (mounted) {
        showMessage(context, 'Application rejected');
      }
    });
  }

  Future<void> _disburse(LoanApplication application) async {
    final _DisbursementResult? result = await showModalBottomSheet<_DisbursementResult>(
      context: context,
      isScrollControlled: true,
      builder: (BuildContext context) => _DisbursementSheet(application: application),
    );
    if (result == null) {
      return;
    }
    await _run(() async {
      await ref.read(applicationRepositoryProvider).disburse(
            widget.applicationId,
            method: result.method,
            reference: result.reference,
            disbursementDate: result.date,
            amount: application.effectiveAmount,
          );
      if (mounted) {
        showMessage(context, 'Disbursement recorded. The loan account is being opened.');
      }
    });
  }

  Future<String?> _promptForText({required String title, required String hint}) async {
    final TextEditingController controller = TextEditingController();
    final String? value = await showDialog<String>(
      context: context,
      builder: (BuildContext context) => AlertDialog(
        title: Text(title),
        content: TextField(
          controller: controller,
          autofocus: true,
          maxLines: 3,
          decoration: InputDecoration(hintText: hint),
        ),
        actions: <Widget>[
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(controller.text.trim()),
            child: const Text('Confirm'),
          ),
        ],
      ),
    );
    controller.dispose();
    return value == null || value.isEmpty ? null : value;
  }

  @override
  Widget build(BuildContext context) {
    final AsyncValue<LoanApplication> application =
        ref.watch(applicationProvider(widget.applicationId));
    final Session? session = ref.watch(currentSessionProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Loan application')),
      body: AsyncView<LoanApplication>(
        value: application,
        onRetry: () => ref.invalidate(applicationProvider(widget.applicationId)),
        data: (LoanApplication data) => ListView(
          padding: const EdgeInsets.all(16),
          children: <Widget>[
            SectionCard(
              title: data.applicationNumber,
              trailing: StatusChip(data.status, compact: true),
              child: Column(
                children: <Widget>[
                  DetailRow('Customer', data.customerName),
                  DetailRow('Product', data.productName),
                  DetailRow(
                    'Requested',
                    Formatters.money(data.requestedAmount, currency: data.currency),
                  ),
                  if (data.approvedAmount != null)
                    DetailRow(
                      'Approved',
                      null,
                      valueWidget: MoneyText(
                        data.approvedAmount,
                        currency: data.currency,
                        emphasise: true,
                      ),
                    ),
                  DetailRow(
                    'Installments',
                    '${data.approvedInstallments ?? data.requestedInstallments}',
                  ),
                  DetailRow('Interest rate', Formatters.percent(data.annualInterestRate)),
                  DetailRow('Method', Formatters.humanise(data.interestMethod)),
                  DetailRow('Purpose', data.purpose),
                  if (data.requiredApprovalLevels > 1)
                    DetailRow('Approvals', data.approvalProgress),
                  if (data.rejectionReason != null)
                    DetailRow('Reason', data.rejectionReason),
                ],
              ),
            ),
            if (data.isDisbursed) ...<Widget>[
              const SizedBox(height: 16),
              SectionCard(
                title: 'Disbursement',
                child: Column(
                  children: <Widget>[
                    DetailRow('Date', Formatters.date(data.disbursementDate)),
                    DetailRow('Reference', data.disbursementReference),
                    DetailRow(
                      'Amount',
                      Formatters.money(data.disbursedAmount, currency: data.currency),
                    ),
                    DetailRow(
                      'Paid to customer',
                      Formatters.money(data.netDisbursedAmount, currency: data.currency),
                    ),
                  ],
                ),
              ),
            ],
            if (data.approvals.isNotEmpty) ...<Widget>[
              const SizedBox(height: 16),
              SectionCard(
                title: 'Approval history',
                child: Column(
                  children: data.approvals
                      .map(
                        (ApprovalRecord record) => ListTile(
                          contentPadding: EdgeInsets.zero,
                          dense: true,
                          leading: Icon(_iconFor(record.decision)),
                          title: Text(
                            '${Formatters.humanise(record.decision)} · level ${record.approvalLevel}',
                          ),
                          subtitle: Text(
                            <String?>[
                              Formatters.dateTime(record.decidedAt),
                              record.comment,
                            ].where((String? part) => part != null && part.isNotEmpty).join('\n'),
                          ),
                          isThreeLine: record.comment != null,
                        ),
                      )
                      .toList(),
                ),
              ),
            ],
            const SizedBox(height: 24),
            ..._actionsFor(data, session),
            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }

  IconData _iconFor(String decision) {
    switch (decision) {
      case 'APPROVED':
        return Icons.check_circle_outline;
      case 'REJECTED':
        return Icons.cancel_outlined;
      case 'CANCELLED':
        return Icons.block_outlined;
      default:
        return Icons.visibility_outlined;
    }
  }

  List<Widget> _actionsFor(LoanApplication application, Session? session) {
    if (session == null || _isWorking) {
      return <Widget>[
        if (_isWorking) const Center(child: CircularProgressIndicator()),
      ];
    }

    final List<Widget> actions = <Widget>[];

    if (application.isDraft && session.canCreateApplications) {
      actions.add(
        FilledButton.icon(
          onPressed: _submit,
          icon: const Icon(Icons.send_outlined),
          label: const Text('Submit for approval'),
        ),
      );
    }

    if (application.isAwaitingDecision && session.canApproveLoans) {
      actions.addAll(<Widget>[
        FilledButton.icon(
          onPressed: () => _approve(application),
          icon: const Icon(Icons.check),
          label: Text(
            application.requiredApprovalLevels > 1
                ? 'Approve (${application.approvalProgress})'
                : 'Approve',
          ),
        ),
        const SizedBox(height: 8),
        OutlinedButton.icon(
          onPressed: _reject,
          icon: const Icon(Icons.close),
          label: const Text('Reject'),
        ),
      ]);
    }

    if (application.isApproved && session.canApproveLoans) {
      actions.add(
        FilledButton.icon(
          onPressed: () => _disburse(application),
          icon: const Icon(Icons.account_balance_wallet_outlined),
          label: const Text('Record disbursement'),
        ),
      );
    }

    if (application.isDisbursed) {
      actions.add(
        OutlinedButton.icon(
          onPressed: () => context.go(Routes.applications),
          icon: const Icon(Icons.list_alt),
          label: const Text('Back to applications'),
        ),
      );
    }

    return actions;
  }
}

class _ApprovalResult {
  const _ApprovalResult({this.comment, this.approvedAmount, this.approvedInstallments});

  final String? comment;
  final String? approvedAmount;
  final int? approvedInstallments;
}

/// Approval sheet. An approver may sanction less than was asked for, which is
/// why the amount and term are editable here rather than fixed.
class _ApprovalSheet extends StatefulWidget {
  const _ApprovalSheet({required this.application});

  final LoanApplication application;

  @override
  State<_ApprovalSheet> createState() => _ApprovalSheetState();
}

class _ApprovalSheetState extends State<_ApprovalSheet> {
  late final TextEditingController _amount =
      TextEditingController(text: widget.application.effectiveAmount);
  late final TextEditingController _installments = TextEditingController(
    text: '${widget.application.approvedInstallments ?? widget.application.requestedInstallments}',
  );
  final TextEditingController _comment = TextEditingController();

  @override
  void dispose() {
    _amount.dispose();
    _installments.dispose();
    _comment.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.fromLTRB(
        16,
        16,
        16,
        MediaQuery.viewInsetsOf(context).bottom + 16,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          Text('Approve application', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 16),
          TextField(
            controller: _amount,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: const InputDecoration(
              labelText: 'Approved amount',
              helperText: 'You may approve less than was requested, but not more',
            ),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _installments,
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(labelText: 'Installments'),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _comment,
            maxLines: 2,
            decoration: const InputDecoration(labelText: 'Comment (optional)'),
          ),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(
              _ApprovalResult(
                comment: _comment.text.trim().isEmpty ? null : _comment.text.trim(),
                approvedAmount: _amount.text.replaceAll(',', '').trim(),
                approvedInstallments: int.tryParse(_installments.text.trim()),
              ),
            ),
            child: const Text('Confirm approval'),
          ),
        ],
      ),
    );
  }
}

class _DisbursementResult {
  const _DisbursementResult({
    required this.method,
    required this.reference,
    required this.date,
  });

  final String method;
  final String reference;
  final DateTime date;
}

class _DisbursementSheet extends StatefulWidget {
  const _DisbursementSheet({required this.application});

  final LoanApplication application;

  @override
  State<_DisbursementSheet> createState() => _DisbursementSheetState();
}

class _DisbursementSheetState extends State<_DisbursementSheet> {
  final TextEditingController _reference = TextEditingController();
  String _method = 'MOBILE_MONEY';
  DateTime _date = DateTime.now();
  String? _error;

  @override
  void dispose() {
    _reference.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.fromLTRB(
        16,
        16,
        16,
        MediaQuery.viewInsetsOf(context).bottom + 16,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          Text('Record disbursement', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          Text(
            'Releasing ${Formatters.money(widget.application.effectiveAmount, currency: widget.application.currency)}',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          const SizedBox(height: 16),
          DropdownButtonFormField<String>(
            initialValue: _method,
            decoration: const InputDecoration(labelText: 'Method'),
            items: const <DropdownMenuItem<String>>[
              DropdownMenuItem<String>(value: 'CASH', child: Text('Cash')),
              DropdownMenuItem<String>(value: 'MOBILE_MONEY', child: Text('Mobile money')),
              DropdownMenuItem<String>(value: 'BANK_TRANSFER', child: Text('Bank transfer')),
              DropdownMenuItem<String>(value: 'CHEQUE', child: Text('Cheque')),
            ],
            onChanged: (String? value) => setState(() => _method = value ?? 'CASH'),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _reference,
            decoration: const InputDecoration(
              labelText: 'Reference',
              helperText: 'Bank or mobile-money transaction id, or cash receipt number',
            ),
          ),
          const SizedBox(height: 12),
          InkWell(
            onTap: () async {
              final DateTime? picked = await showDatePicker(
                context: context,
                initialDate: _date,
                firstDate: DateTime.now().subtract(const Duration(days: 90)),
                // A future-dated disbursement would claim money moved before it did.
                lastDate: DateTime.now(),
              );
              if (picked != null) {
                setState(() => _date = picked);
              }
            },
            child: InputDecorator(
              decoration: const InputDecoration(labelText: 'Value date'),
              child: Text(Formatters.date(_date)),
            ),
          ),
          if (_error != null) ...<Widget>[
            const SizedBox(height: 12),
            Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
          ],
          const SizedBox(height: 16),
          FilledButton(
            onPressed: () {
              if (_reference.text.trim().isEmpty) {
                setState(() => _error = 'A reference is required for the audit trail');
                return;
              }
              Navigator.of(context).pop(
                _DisbursementResult(
                  method: _method,
                  reference: _reference.text.trim(),
                  date: _date,
                ),
              );
            },
            child: const Text('Confirm disbursement'),
          ),
        ],
      ),
    );
  }
}
