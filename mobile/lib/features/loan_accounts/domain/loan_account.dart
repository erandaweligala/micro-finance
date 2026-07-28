/// A live loan.
class LoanAccount {
  const LoanAccount({
    required this.id,
    required this.accountNumber,
    required this.customerId,
    required this.status,
    required this.principal,
    required this.outstandingPrincipal,
    required this.totalOutstanding,
    this.currency,
    this.totalInterest = '0',
    this.totalFees = '0',
    this.totalRepayable = '0',
    this.installmentAmount = '0',
    this.outstandingInterest = '0',
    this.outstandingFees = '0',
    this.outstandingPenalty = '0',
    this.principalPaid = '0',
    this.interestPaid = '0',
    this.penaltyPaid = '0',
    this.advanceBalance = '0',
    this.annualInterestRate,
    this.interestMethod,
    this.repaymentFrequency,
    this.numberOfInstallments = 0,
    this.daysPastDue = 0,
    this.overdueAmount = '0',
    this.disbursementDate,
    this.maturityDate,
    this.lastPaymentDate,
  });

  final String id;
  final String accountNumber;
  final String customerId;
  final String status;
  final String? currency;
  final String principal;
  final String totalInterest;
  final String totalFees;
  final String totalRepayable;
  final String installmentAmount;
  final String outstandingPrincipal;
  final String outstandingInterest;
  final String outstandingFees;
  final String outstandingPenalty;
  final String totalOutstanding;
  final String principalPaid;
  final String interestPaid;
  final String penaltyPaid;
  final String advanceBalance;
  final String? annualInterestRate;
  final String? interestMethod;
  final String? repaymentFrequency;
  final int numberOfInstallments;
  final int daysPastDue;
  final String overdueAmount;
  final DateTime? disbursementDate;
  final DateTime? maturityDate;
  final DateTime? lastPaymentDate;

  bool get isInArrears => daysPastDue > 0;

  bool get isClosed => status == 'CLOSED';

  bool get acceptsPayments => status != 'CLOSED' && status != 'WRITTEN_OFF';

  /// Share of the contract repaid, for the progress indicator.
  double get repaymentProgress {
    final double total = double.tryParse(totalRepayable) ?? 0;
    final double outstanding = double.tryParse(totalOutstanding) ?? 0;
    if (total <= 0) {
      return 0;
    }
    return ((total - outstanding) / total).clamp(0.0, 1.0);
  }

  factory LoanAccount.fromJson(Map<String, dynamic> json) => LoanAccount(
        id: json['id'] as String,
        accountNumber: json['accountNumber'] as String? ?? '',
        customerId: json['customerId'] as String? ?? '',
        status: json['status'] as String? ?? 'ACTIVE',
        currency: json['currency'] as String?,
        principal: json['principal']?.toString() ?? '0',
        totalInterest: json['totalInterest']?.toString() ?? '0',
        totalFees: json['totalFees']?.toString() ?? '0',
        totalRepayable: json['totalRepayable']?.toString() ?? '0',
        installmentAmount: json['installmentAmount']?.toString() ?? '0',
        outstandingPrincipal: json['outstandingPrincipal']?.toString() ?? '0',
        outstandingInterest: json['outstandingInterest']?.toString() ?? '0',
        outstandingFees: json['outstandingFees']?.toString() ?? '0',
        outstandingPenalty: json['outstandingPenalty']?.toString() ?? '0',
        totalOutstanding: json['totalOutstanding']?.toString() ?? '0',
        principalPaid: json['principalPaid']?.toString() ?? '0',
        interestPaid: json['interestPaid']?.toString() ?? '0',
        penaltyPaid: json['penaltyPaid']?.toString() ?? '0',
        advanceBalance: json['advanceBalance']?.toString() ?? '0',
        annualInterestRate: json['annualInterestRate']?.toString(),
        interestMethod: json['interestMethod'] as String?,
        repaymentFrequency: json['repaymentFrequency'] as String?,
        numberOfInstallments: (json['numberOfInstallments'] as num?)?.toInt() ?? 0,
        daysPastDue: (json['daysPastDue'] as num?)?.toInt() ?? 0,
        overdueAmount: json['overdueAmount']?.toString() ?? '0',
        disbursementDate: _date(json['disbursementDate']),
        maturityDate: _date(json['maturityDate']),
        lastPaymentDate: _date(json['lastPaymentDate']),
      );

  static DateTime? _date(Object? value) =>
      value is String && value.isNotEmpty ? DateTime.tryParse(value) : null;
}

/// What it costs to settle a loan today.
class PayoffQuote {
  const PayoffQuote({
    required this.settlementAmount,
    required this.outstandingPrincipal,
    required this.interestDueToDate,
    required this.penaltyDue,
    this.feesDueToDate = '0',
    this.creditBalance = '0',
    this.asOf,
    this.currency,
  });

  final String settlementAmount;
  final String outstandingPrincipal;
  final String interestDueToDate;
  final String feesDueToDate;
  final String penaltyDue;
  final String creditBalance;
  final DateTime? asOf;
  final String? currency;

  factory PayoffQuote.fromJson(Map<String, dynamic> json) => PayoffQuote(
        settlementAmount: json['settlementAmount']?.toString() ?? '0',
        outstandingPrincipal: json['outstandingPrincipal']?.toString() ?? '0',
        interestDueToDate: json['interestDueToDate']?.toString() ?? '0',
        feesDueToDate: json['feesDueToDate']?.toString() ?? '0',
        penaltyDue: json['penaltyDue']?.toString() ?? '0',
        creditBalance: json['creditBalance']?.toString() ?? '0',
        asOf: LoanAccount._date(json['asOf']),
        currency: json['currency'] as String?,
      );
}
