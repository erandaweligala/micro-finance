import 'package:equatable/equatable.dart';

/// The calculation result returned by the backend's amortisation engine.
///
/// Amounts are kept as [String] exactly as the server sent them. They are never
/// parsed into a double for arithmetic: the app displays what the engine
/// computed, so a schedule shown to a customer always matches the one the loan
/// will actually be written on.
class LoanSchedule {
  const LoanSchedule({
    required this.principal,
    required this.installmentAmount,
    required this.totalInterest,
    required this.totalFees,
    required this.totalRepayable,
    required this.netDisbursedAmount,
    required this.installments,
    this.interestMethod,
    this.repaymentFrequency,
    this.periodicRate,
    this.firstDueDate,
    this.maturityDate,
    this.brokenPeriodInterest,
    this.currency,
  });

  final String principal;
  final String installmentAmount;
  final String totalInterest;
  final String totalFees;
  final String totalRepayable;
  final String netDisbursedAmount;
  final String? interestMethod;
  final String? repaymentFrequency;
  final String? periodicRate;
  final DateTime? firstDueDate;
  final DateTime? maturityDate;
  final String? brokenPeriodInterest;
  final String? currency;
  final List<ScheduledInstallment> installments;

  factory LoanSchedule.fromJson(Map<String, dynamic> json) {
    final List<dynamic> rows = (json['schedule'] as List<dynamic>?) ??
        (json['installments'] as List<dynamic>?) ??
        <dynamic>[];
    return LoanSchedule(
      principal: _text(json['principal']),
      installmentAmount: _text(json['installmentAmount'] ?? json['regularInstallment']),
      totalInterest: _text(json['totalInterest']),
      totalFees: _text(json['totalFees']),
      totalRepayable: _text(json['totalRepayable']),
      netDisbursedAmount: _text(json['netDisbursedAmount']),
      interestMethod: json['interestMethod'] as String?,
      repaymentFrequency: json['repaymentFrequency'] as String? ?? json['frequency'] as String?,
      periodicRate: json['periodicRate']?.toString(),
      firstDueDate: _date(json['firstDueDate'] ?? json['effectiveFirstDueDate']),
      maturityDate: _date(json['maturityDate']),
      brokenPeriodInterest: json['brokenPeriodInterest']?.toString(),
      currency: json['currency'] as String?,
      installments: rows
          .map((dynamic row) => ScheduledInstallment.fromJson(row as Map<String, dynamic>))
          .toList(),
    );
  }

  bool get hasBrokenPeriodInterest {
    final double? value = double.tryParse(brokenPeriodInterest ?? '0');
    return value != null && value > 0;
  }

  static String _text(Object? value) => value?.toString() ?? '0';

  static DateTime? _date(Object? value) =>
      value is String && value.isNotEmpty ? DateTime.tryParse(value) : null;
}

/// One row of the repayment schedule.
class ScheduledInstallment {
  const ScheduledInstallment({
    required this.installmentNumber,
    required this.dueDate,
    required this.principal,
    required this.interest,
    required this.totalDue,
    required this.closingBalance,
    this.openingBalance = '0',
    this.fee = '0',
    this.penalty = '0',
    this.status,
    this.graceInstallment = false,
  });

  final int installmentNumber;
  final DateTime? dueDate;
  final String openingBalance;
  final String principal;
  final String interest;
  final String fee;
  final String penalty;
  final String totalDue;

  /// Outstanding balance after this installment - what the customer asks about.
  final String closingBalance;

  /// Present on a live loan's schedule; absent on a quotation.
  final String? status;
  final bool graceInstallment;

  factory ScheduledInstallment.fromJson(Map<String, dynamic> json) => ScheduledInstallment(
        installmentNumber: (json['installmentNumber'] as num?)?.toInt() ?? 0,
        dueDate: LoanSchedule._date(json['dueDate']),
        openingBalance: LoanSchedule._text(json['openingBalance']),
        principal: LoanSchedule._text(json['principal'] ?? json['principalDue']),
        interest: LoanSchedule._text(json['interest'] ?? json['interestDue']),
        fee: LoanSchedule._text(json['fee'] ?? json['feeDue']),
        penalty: LoanSchedule._text(json['penalty'] ?? json['penaltyDue']),
        totalDue: LoanSchedule._text(json['totalDue']),
        closingBalance: LoanSchedule._text(json['closingBalance']),
        status: json['status'] as String?,
        graceInstallment: json['graceInstallment'] as bool? ?? false,
      );
}

/// Inputs to the calculator, mirroring the backend's request contract.
///
/// Value equality matters here: this type is a Riverpod family key, so two
/// identical sets of terms must be the same key or every rebuild would fire a
/// fresh request.
class CalculationInput extends Equatable {
  const CalculationInput({
    required this.principal,
    required this.annualInterestRate,
    required this.numberOfInstallments,
    this.repaymentFrequency = 'MONTHLY',
    this.interestMethod = 'REDUCING_BALANCE',
    this.productId,
    this.disbursementDate,
    this.firstRepaymentDate,
    this.graceType,
    this.gracePeriods = 0,
  });

  final String principal;
  final String annualInterestRate;
  final int numberOfInstallments;
  final String repaymentFrequency;
  final String interestMethod;
  final String? productId;
  final DateTime? disbursementDate;
  final DateTime? firstRepaymentDate;
  final String? graceType;
  final int gracePeriods;

  Map<String, dynamic> toJson() => <String, dynamic>{
        if (productId != null) 'productId': productId,
        'principal': principal,
        'annualInterestRate': annualInterestRate,
        'numberOfInstallments': numberOfInstallments,
        'repaymentFrequency': repaymentFrequency,
        'interestMethod': interestMethod,
        if (disbursementDate != null)
          'disbursementDate': disbursementDate!.toIso8601String().substring(0, 10),
        if (firstRepaymentDate != null)
          'firstRepaymentDate': firstRepaymentDate!.toIso8601String().substring(0, 10),
        if (graceType != null) 'graceType': graceType,
        'gracePeriods': gracePeriods,
      };

  @override
  List<Object?> get props => <Object?>[
        principal,
        annualInterestRate,
        numberOfInstallments,
        repaymentFrequency,
        interestMethod,
        productId,
        disbursementDate,
        firstRepaymentDate,
        graceType,
        gracePeriods,
      ];
}
