import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/error/api_exception.dart';
import '../../../core/providers.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/async_view.dart';
import '../../../core/widgets/common_widgets.dart';
import '../../auth/domain/session.dart';
import '../data/customer_repository.dart';
import '../domain/customer.dart';

/// KYC management: attach documents, verify them, and record the overall decision.
///
/// The screen makes the gate explicit - approval stays disabled until every
/// mandatory document is verified and unexpired, which is the same rule the
/// backend enforces, shown rather than merely applied.
class KycScreen extends ConsumerStatefulWidget {
  const KycScreen({required this.customerId, super.key});

  final String customerId;

  @override
  ConsumerState<KycScreen> createState() => _KycScreenState();
}

class _KycScreenState extends ConsumerState<KycScreen> {
  bool _isWorking = false;

  Future<void> _verifyDocument(String documentId, bool approved) async {
    String? reason;
    if (!approved) {
      reason = await _promptForReason('Why is this document being rejected?');
      if (reason == null) {
        return;
      }
    }
    await _run(() async {
      await ref.read(customerRepositoryProvider).verifyDocument(
            customerId: widget.customerId,
            documentId: documentId,
            approved: approved,
            reason: reason,
          );
      ref.invalidate(kycDocumentsProvider(widget.customerId));
      ref.invalidate(customerProvider(widget.customerId));
    });
  }

  Future<void> _decide(bool approved) async {
    String? reason;
    if (!approved) {
      reason = await _promptForReason('Why is KYC being rejected?');
      if (reason == null) {
        return;
      }
    }
    await _run(() async {
      await ref.read(customerRepositoryProvider).decideKyc(
            customerId: widget.customerId,
            approved: approved,
            reason: reason,
          );
      ref.invalidate(customerProvider(widget.customerId));
      ref.invalidate(kycDocumentsProvider(widget.customerId));
      if (mounted) {
        showMessage(context, approved ? 'KYC approved' : 'KYC rejected');
      }
    });
  }

  Future<void> _run(Future<void> Function() action) async {
    setState(() => _isWorking = true);
    try {
      await action();
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

  Future<String?> _promptForReason(String title) async {
    final TextEditingController controller = TextEditingController();
    final String? reason = await showDialog<String>(
      context: context,
      builder: (BuildContext context) => AlertDialog(
        title: Text(title),
        content: TextField(
          controller: controller,
          autofocus: true,
          maxLines: 3,
          decoration: const InputDecoration(
            hintText: 'The customer will be told this reason',
          ),
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
    return reason == null || reason.isEmpty ? null : reason;
  }

  @override
  Widget build(BuildContext context) {
    final AsyncValue<List<KycDocument>> documents =
        ref.watch(kycDocumentsProvider(widget.customerId));
    final AsyncValue<Customer> customer = ref.watch(customerProvider(widget.customerId));
    final Session? session = ref.watch(currentSessionProvider);
    final bool canDecide = session?.canApproveLoans ?? false;

    return Scaffold(
      appBar: AppBar(title: const Text('KYC')),
      body: AsyncView<List<KycDocument>>(
        value: documents,
        onRetry: () => ref.invalidate(kycDocumentsProvider(widget.customerId)),
        data: (List<KycDocument> docs) {
          final List<String> missing = KycDocument.requiredTypes
              .where(
                (String type) => !docs.any(
                  (KycDocument doc) => doc.documentType == type && doc.isVerified && !doc.expired,
                ),
              )
              .toList();

          return ListView(
            padding: const EdgeInsets.all(16),
            children: <Widget>[
              customer.maybeWhen(
                data: (Customer data) => SectionCard(
                  title: data.fullName,
                  trailing: StatusChip(data.kycStatus, compact: true),
                  child: Column(
                    children: <Widget>[
                      DetailRow('Customer number', data.customerNumber, dense: true),
                      DetailRow('ID type', Formatters.humanise(data.idType), dense: true),
                      DetailRow('ID number', data.nationalIdMasked, dense: true),
                    ],
                  ),
                ),
                orElse: () => const SizedBox.shrink(),
              ),
              const SizedBox(height: 16),
              if (missing.isNotEmpty)
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.tertiaryContainer,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    'Still required: '
                    '${missing.map((String type) => KycDocument.typeLabels[type] ?? type).join(', ')}',
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.onTertiaryContainer,
                    ),
                  ),
                ),
              if (missing.isNotEmpty) const SizedBox(height: 16),
              Text('Documents', style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 8),
              if (docs.isEmpty)
                const EmptyState(
                  title: 'No documents yet',
                  message: 'Attach an identity document and a proof of address to begin.',
                  icon: Icons.folder_open_outlined,
                )
              else
                ...docs.map(
                  (KycDocument document) => Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: _DocumentCard(
                      document: document,
                      canVerify: canDecide && !_isWorking,
                      onVerify: (bool approved) => _verifyDocument(document.id, approved),
                    ),
                  ),
                ),
              const SizedBox(height: 24),
              if (canDecide) ...<Widget>[
                FilledButton.icon(
                  // Disabled until the mandatory set is complete: the backend
                  // would refuse anyway, and a disabled button explains why.
                  onPressed: (_isWorking || missing.isNotEmpty) ? null : () => _decide(true),
                  icon: const Icon(Icons.verified_outlined),
                  label: const Text('Approve KYC'),
                ),
                const SizedBox(height: 8),
                OutlinedButton.icon(
                  onPressed: _isWorking ? null : () => _decide(false),
                  icon: const Icon(Icons.block_outlined),
                  label: const Text('Reject KYC'),
                ),
              ],
              const SizedBox(height: 32),
            ],
          );
        },
      ),
    );
  }
}

class _DocumentCard extends StatelessWidget {
  const _DocumentCard({
    required this.document,
    required this.canVerify,
    required this.onVerify,
  });

  final KycDocument document;
  final bool canVerify;
  final ValueChanged<bool> onVerify;

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
                  KycDocument.typeLabels[document.documentType] ?? document.documentType,
                  style: theme.textTheme.titleSmall,
                ),
              ),
              StatusChip(document.verificationStatus, compact: true),
            ],
          ),
          const SizedBox(height: 8),
          DetailRow('Number', document.documentNumberMasked, dense: true),
          DetailRow('Issued by', document.issuingAuthority, dense: true),
          DetailRow('Expires', Formatters.date(document.expiresOn), dense: true),
          if (document.expired)
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Text(
                'This document has expired and cannot support verification.',
                style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.error),
              ),
            ),
          if (document.rejectionReason != null)
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Text(
                document.rejectionReason!,
                style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.error),
              ),
            ),
          if (canVerify && document.verificationStatus == 'PENDING') ...<Widget>[
            const SizedBox(height: 12),
            Row(
              children: <Widget>[
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => onVerify(false),
                    child: const Text('Reject'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: FilledButton(
                    onPressed: document.expired ? null : () => onVerify(true),
                    child: const Text('Verify'),
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}
