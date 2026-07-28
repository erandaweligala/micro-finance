/// A recorded repayment, with the split the loan account applied to it.
class Payment {
  const Payment({
    required this.id,
    required this.receiptNumber,
    required this.loanAccountId,
    required this.amount,
    required this.status,
    this.loanAccountNumber,
    this.customerId,
    this.currency,
    this.method,
    this.externalReference,
    this.valueDate,
    this.narrative,
    this.penaltyAllocated = '0',
    this.feeAllocated = '0',
    this.interestAllocated = '0',
    this.principalAllocated = '0',
    this.excessAmount = '0',
    this.outstandingPrincipalAfter,
    this.totalOutstandingAfter,
    this.reversalReason,
    this.createdAt,
  });

  final String id;
  final String receiptNumber;
  final String loanAccountId;
  final String? loanAccountNumber;
  final String? customerId;
  final String amount;
  final String? currency;
  final String? method;
  final String? externalReference;
  final DateTime? valueDate;
  final String? narrative;
  final String penaltyAllocated;
  final String feeAllocated;
  final String interestAllocated;
  final String principalAllocated;
  final String excessAmount;
  final String? outstandingPrincipalAfter;
  final String? totalOutstandingAfter;
  final String status;
  final String? reversalReason;
  final DateTime? createdAt;

  bool get isReversed => status == 'REVERSED';

  bool get hasExcess => (double.tryParse(excessAmount) ?? 0) > 0;

  factory Payment.fromJson(Map<String, dynamic> json) => Payment(
        id: json['id'] as String,
        receiptNumber: json['receiptNumber'] as String? ?? '',
        loanAccountId: json['loanAccountId'] as String? ?? '',
        loanAccountNumber: json['loanAccountNumber'] as String?,
        customerId: json['customerId'] as String?,
        amount: json['amount']?.toString() ?? '0',
        currency: json['currency'] as String?,
        method: json['method'] as String?,
        externalReference: json['externalReference'] as String?,
        valueDate: _date(json['valueDate']),
        narrative: json['narrative'] as String?,
        penaltyAllocated: json['penaltyAllocated']?.toString() ?? '0',
        feeAllocated: json['feeAllocated']?.toString() ?? '0',
        interestAllocated: json['interestAllocated']?.toString() ?? '0',
        principalAllocated: json['principalAllocated']?.toString() ?? '0',
        excessAmount: json['excessAmount']?.toString() ?? '0',
        outstandingPrincipalAfter: json['outstandingPrincipalAfter']?.toString(),
        totalOutstandingAfter: json['totalOutstandingAfter']?.toString(),
        status: json['status'] as String? ?? 'POSTED',
        reversalReason: json['reversalReason'] as String?,
        createdAt: _date(json['createdAt']),
      );

  static DateTime? _date(Object? value) =>
      value is String && value.isNotEmpty ? DateTime.tryParse(value) : null;

  static const Map<String, String> methodLabels = <String, String>{
    'CASH': 'Cash',
    'MOBILE_MONEY': 'Mobile money',
    'BANK_TRANSFER': 'Bank transfer',
    'CHEQUE': 'Cheque',
    'DEBIT_ORDER': 'Debit order',
    'INTERNAL_TRANSFER': 'Internal transfer',
  };
}
