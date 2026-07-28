/// A loan application and its approval history.
class LoanApplication {
  const LoanApplication({
    required this.id,
    required this.applicationNumber,
    required this.customerId,
    required this.status,
    required this.requestedAmount,
    required this.requestedInstallments,
    this.customerName,
    this.productId,
    this.productName,
    this.currency,
    this.approvedAmount,
    this.approvedInstallments,
    this.annualInterestRate,
    this.interestMethod,
    this.repaymentFrequency,
    this.purpose,
    this.currentApprovalLevel = 0,
    this.requiredApprovalLevels = 1,
    this.rejectionReason,
    this.disbursementDate,
    this.disbursementReference,
    this.disbursedAmount,
    this.netDisbursedAmount,
    this.approvals = const <ApprovalRecord>[],
    this.submittedAt,
    this.createdAt,
  });

  final String id;
  final String applicationNumber;
  final String customerId;
  final String? customerName;
  final String? productId;
  final String? productName;
  final String? currency;
  final String status;
  final String requestedAmount;
  final int requestedInstallments;
  final String? approvedAmount;
  final int? approvedInstallments;
  final String? annualInterestRate;
  final String? interestMethod;
  final String? repaymentFrequency;
  final String? purpose;
  final int currentApprovalLevel;
  final int requiredApprovalLevels;
  final String? rejectionReason;
  final DateTime? disbursementDate;
  final String? disbursementReference;
  final String? disbursedAmount;
  final String? netDisbursedAmount;
  final List<ApprovalRecord> approvals;
  final DateTime? submittedAt;
  final DateTime? createdAt;

  bool get isDraft => status == 'DRAFT';

  bool get isAwaitingDecision => status == 'SUBMITTED' || status == 'UNDER_REVIEW';

  bool get isApproved => status == 'APPROVED';

  bool get isDisbursed => status == 'DISBURSED';

  /// The amount that will actually be lent, once approved.
  String get effectiveAmount => approvedAmount ?? requestedAmount;

  /// How far through the approval hierarchy this application has come.
  String get approvalProgress => '$currentApprovalLevel of $requiredApprovalLevels';

  factory LoanApplication.fromJson(Map<String, dynamic> json) => LoanApplication(
        id: json['id'] as String,
        applicationNumber: json['applicationNumber'] as String? ?? '',
        customerId: json['customerId'] as String? ?? '',
        customerName: json['customerName'] as String?,
        productId: json['productId'] as String?,
        productName: json['productName'] as String?,
        currency: json['currency'] as String?,
        status: json['status'] as String? ?? 'DRAFT',
        requestedAmount: json['requestedAmount']?.toString() ?? '0',
        requestedInstallments: (json['requestedInstallments'] as num?)?.toInt() ?? 0,
        approvedAmount: json['approvedAmount']?.toString(),
        approvedInstallments: (json['approvedInstallments'] as num?)?.toInt(),
        annualInterestRate: json['annualInterestRate']?.toString(),
        interestMethod: json['interestMethod'] as String?,
        repaymentFrequency: json['repaymentFrequency'] as String?,
        purpose: json['purpose'] as String?,
        currentApprovalLevel: (json['currentApprovalLevel'] as num?)?.toInt() ?? 0,
        requiredApprovalLevels: (json['requiredApprovalLevels'] as num?)?.toInt() ?? 1,
        rejectionReason: json['rejectionReason'] as String?,
        disbursementDate: _date(json['disbursementDate']),
        disbursementReference: json['disbursementReference'] as String?,
        disbursedAmount: json['disbursedAmount']?.toString(),
        netDisbursedAmount: json['netDisbursedAmount']?.toString(),
        approvals: ((json['approvals'] as List<dynamic>?) ?? <dynamic>[])
            .map((dynamic item) => ApprovalRecord.fromJson(item as Map<String, dynamic>))
            .toList(),
        submittedAt: _date(json['submittedAt']),
        createdAt: _date(json['createdAt']),
      );

  static DateTime? _date(Object? value) =>
      value is String && value.isNotEmpty ? DateTime.tryParse(value) : null;
}

/// One entry in an application's approval history.
class ApprovalRecord {
  const ApprovalRecord({
    required this.id,
    required this.approvalLevel,
    required this.decision,
    this.comment,
    this.approvedAmount,
    this.decidedAt,
  });

  final String id;
  final int approvalLevel;
  final String decision;
  final String? comment;
  final String? approvedAmount;
  final DateTime? decidedAt;

  factory ApprovalRecord.fromJson(Map<String, dynamic> json) => ApprovalRecord(
        id: json['id'] as String? ?? '',
        approvalLevel: (json['approvalLevel'] as num?)?.toInt() ?? 0,
        decision: json['decision'] as String? ?? '',
        comment: json['comment'] as String?,
        approvedAmount: json['approvedAmount']?.toString(),
        decidedAt: LoanApplication._date(json['decidedAt']),
      );
}

/// A loan product, as the application form needs it.
class LoanProduct {
  const LoanProduct({
    required this.id,
    required this.code,
    required this.name,
    required this.currency,
    required this.minPrincipal,
    required this.maxPrincipal,
    required this.defaultAnnualRate,
    required this.minInstallments,
    required this.maxInstallments,
    required this.defaultInstallments,
    this.interestMethod = 'REDUCING_BALANCE',
    this.repaymentFrequency = 'MONTHLY',
    this.minAnnualRate = '0',
    this.maxAnnualRate = '0',
  });

  final String id;
  final String code;
  final String name;
  final String currency;
  final String minPrincipal;
  final String maxPrincipal;
  final String minAnnualRate;
  final String maxAnnualRate;
  final String defaultAnnualRate;
  final int minInstallments;
  final int maxInstallments;
  final int defaultInstallments;
  final String interestMethod;
  final String repaymentFrequency;

  factory LoanProduct.fromJson(Map<String, dynamic> json) => LoanProduct(
        id: json['id'] as String,
        code: json['code'] as String? ?? '',
        name: json['name'] as String? ?? '',
        currency: json['currency'] as String? ?? 'KES',
        minPrincipal: json['minPrincipal']?.toString() ?? '0',
        maxPrincipal: json['maxPrincipal']?.toString() ?? '0',
        minAnnualRate: json['minAnnualRate']?.toString() ?? '0',
        maxAnnualRate: json['maxAnnualRate']?.toString() ?? '0',
        defaultAnnualRate: json['defaultAnnualRate']?.toString() ?? '0',
        minInstallments: (json['minInstallments'] as num?)?.toInt() ?? 1,
        maxInstallments: (json['maxInstallments'] as num?)?.toInt() ?? 60,
        defaultInstallments: (json['defaultInstallments'] as num?)?.toInt() ?? 12,
        interestMethod: json['interestMethod'] as String? ?? 'REDUCING_BALANCE',
        repaymentFrequency: json['repaymentFrequency'] as String? ?? 'MONTHLY',
      );
}
