/// One line of the customer loan ledger.
class LedgerEntry {
  const LedgerEntry({
    required this.id,
    required this.transactionDate,
    required this.transactionReference,
    required this.transactionType,
    required this.debitAmount,
    required this.creditAmount,
    this.narrative,
    this.principalAllocation = '0',
    this.interestAllocation = '0',
    this.feeAllocation = '0',
    this.penaltyAllocation = '0',
    this.outstandingPrincipal = '0',
    this.totalOutstanding = '0',
    this.currency,
    this.reversesEntryId,
  });

  final String id;
  final DateTime? transactionDate;
  final String transactionReference;
  final String transactionType;
  final String? narrative;
  final String debitAmount;
  final String creditAmount;
  final String principalAllocation;
  final String interestAllocation;
  final String feeAllocation;
  final String penaltyAllocation;
  final String outstandingPrincipal;
  final String totalOutstanding;
  final String? currency;
  final String? reversesEntryId;

  bool get isDebit => (double.tryParse(debitAmount) ?? 0) > 0;

  bool get isContraEntry => reversesEntryId != null;

  factory LedgerEntry.fromJson(Map<String, dynamic> json) => LedgerEntry(
        id: json['id'] as String,
        transactionDate: _date(json['transactionDate']),
        transactionReference: json['transactionReference'] as String? ?? '',
        transactionType: json['transactionType'] as String? ?? '',
        narrative: json['narrative'] as String?,
        debitAmount: json['debitAmount']?.toString() ?? '0',
        creditAmount: json['creditAmount']?.toString() ?? '0',
        principalAllocation: json['principalAllocation']?.toString() ?? '0',
        interestAllocation: json['interestAllocation']?.toString() ?? '0',
        feeAllocation: json['feeAllocation']?.toString() ?? '0',
        penaltyAllocation: json['penaltyAllocation']?.toString() ?? '0',
        outstandingPrincipal: json['outstandingPrincipal']?.toString() ?? '0',
        totalOutstanding: json['totalOutstanding']?.toString() ?? '0',
        currency: json['currency'] as String?,
        reversesEntryId: json['reversesEntryId'] as String?,
      );

  static DateTime? _date(Object? value) =>
      value is String && value.isNotEmpty ? DateTime.tryParse(value) : null;
}

/// A statement for a period, reconciling opening to closing balance.
class Statement {
  const Statement({
    required this.entries,
    required this.openingBalance,
    required this.totalDebits,
    required this.totalCredits,
    required this.closingBalance,
    this.loanAccountNumber,
    this.periodStart,
    this.periodEnd,
    this.closingOutstanding = '0',
  });

  final List<LedgerEntry> entries;
  final String openingBalance;
  final String totalDebits;
  final String totalCredits;
  final String closingBalance;
  final String closingOutstanding;
  final String? loanAccountNumber;
  final DateTime? periodStart;
  final DateTime? periodEnd;

  factory Statement.fromJson(Map<String, dynamic> json) => Statement(
        entries: ((json['entries'] as List<dynamic>?) ?? <dynamic>[])
            .map((dynamic item) => LedgerEntry.fromJson(item as Map<String, dynamic>))
            .toList(),
        openingBalance: json['openingBalance']?.toString() ?? '0',
        totalDebits: json['totalDebits']?.toString() ?? '0',
        totalCredits: json['totalCredits']?.toString() ?? '0',
        closingBalance: json['closingBalance']?.toString() ?? '0',
        closingOutstanding: json['closingOutstanding']?.toString() ?? '0',
        loanAccountNumber: json['loanAccountNumber'] as String?,
        periodStart: LedgerEntry._date(json['periodStart']),
        periodEnd: LedgerEntry._date(json['periodEnd']),
      );
}
