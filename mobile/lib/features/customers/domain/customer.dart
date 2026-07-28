/// A borrower, as the app sees them.
///
/// Identification numbers arrive already masked from the backend - the app never
/// receives a full national id or phone number in a list response, so a stolen
/// device cannot yield a customer database.
class Customer {
  const Customer({
    required this.id,
    required this.customerNumber,
    required this.fullName,
    required this.kycStatus,
    required this.status,
    this.firstName,
    this.middleName,
    this.lastName,
    this.dateOfBirth,
    this.gender,
    this.maritalStatus,
    this.idType,
    this.nationalIdMasked,
    this.phoneNumberMasked,
    this.email,
    this.address,
    this.occupation,
    this.employer,
    this.monthlyIncome,
    this.branchId,
    this.eligibleForLending = false,
    this.kycRejectionReason,
    this.createdAt,
  });

  final String id;
  final String customerNumber;
  final String fullName;
  final String kycStatus;
  final String status;
  final String? firstName;
  final String? middleName;
  final String? lastName;
  final DateTime? dateOfBirth;
  final String? gender;
  final String? maritalStatus;
  final String? idType;
  final String? nationalIdMasked;
  final String? phoneNumberMasked;
  final String? email;
  final CustomerAddress? address;
  final String? occupation;
  final String? employer;
  final String? monthlyIncome;
  final String? branchId;
  final bool eligibleForLending;
  final String? kycRejectionReason;
  final DateTime? createdAt;

  bool get isKycVerified => kycStatus == 'VERIFIED';

  factory Customer.fromJson(Map<String, dynamic> json) => Customer(
        id: json['id'] as String,
        customerNumber: json['customerNumber'] as String? ?? '',
        fullName: json['fullName'] as String? ?? '',
        kycStatus: json['kycStatus'] as String? ?? 'PENDING',
        status: json['status'] as String? ?? 'ACTIVE',
        firstName: json['firstName'] as String?,
        middleName: json['middleName'] as String?,
        lastName: json['lastName'] as String?,
        dateOfBirth: _date(json['dateOfBirth']),
        gender: json['gender'] as String?,
        maritalStatus: json['maritalStatus'] as String?,
        idType: json['idType'] as String?,
        nationalIdMasked: json['nationalIdMasked'] as String?,
        phoneNumberMasked: json['phoneNumberMasked'] as String?,
        email: json['email'] as String?,
        address: json['address'] == null
            ? null
            : CustomerAddress.fromJson(json['address'] as Map<String, dynamic>),
        occupation: json['occupation'] as String?,
        employer: json['employer'] as String?,
        monthlyIncome: json['monthlyIncome']?.toString(),
        branchId: json['branchId'] as String?,
        eligibleForLending: json['eligibleForLending'] as bool? ?? false,
        kycRejectionReason: json['kycRejectionReason'] as String?,
        createdAt: _date(json['createdAt']),
      );

  static DateTime? _date(Object? value) =>
      value is String && value.isNotEmpty ? DateTime.tryParse(value) : null;
}

class CustomerAddress {
  const CustomerAddress({
    this.line1,
    this.line2,
    this.city,
    this.stateProvince,
    this.postalCode,
    this.countryCode,
  });

  final String? line1;
  final String? line2;
  final String? city;
  final String? stateProvince;
  final String? postalCode;
  final String? countryCode;

  factory CustomerAddress.fromJson(Map<String, dynamic> json) => CustomerAddress(
        line1: json['line1'] as String?,
        line2: json['line2'] as String?,
        city: json['city'] as String?,
        stateProvince: json['stateProvince'] as String?,
        postalCode: json['postalCode'] as String?,
        countryCode: json['countryCode'] as String?,
      );

  Map<String, dynamic> toJson() => <String, dynamic>{
        'line1': line1,
        'line2': line2,
        'city': city,
        'stateProvince': stateProvince,
        'postalCode': postalCode,
        'countryCode': countryCode,
      };

  String get singleLine => <String?>[line1, line2, city, stateProvince, countryCode]
      .where((String? part) => part != null && part.isNotEmpty)
      .join(', ');
}

/// A KYC document attached to a customer.
class KycDocument {
  const KycDocument({
    required this.id,
    required this.documentType,
    required this.verificationStatus,
    this.documentNumberMasked,
    this.issuingAuthority,
    this.issuedOn,
    this.expiresOn,
    this.rejectionReason,
    this.expired = false,
  });

  final String id;
  final String documentType;
  final String verificationStatus;
  final String? documentNumberMasked;
  final String? issuingAuthority;
  final DateTime? issuedOn;
  final DateTime? expiresOn;
  final String? rejectionReason;
  final bool expired;

  bool get isVerified => verificationStatus == 'VERIFIED';

  factory KycDocument.fromJson(Map<String, dynamic> json) => KycDocument(
        id: json['id'] as String,
        documentType: json['documentType'] as String? ?? 'OTHER',
        verificationStatus: json['verificationStatus'] as String? ?? 'PENDING',
        documentNumberMasked: json['documentNumberMasked'] as String?,
        issuingAuthority: json['issuingAuthority'] as String?,
        issuedOn: Customer._date(json['issuedOn']),
        expiresOn: Customer._date(json['expiresOn']),
        rejectionReason: json['rejectionReason'] as String?,
        expired: json['expired'] as bool? ?? false,
      );

  /// The document types a customer must have verified before KYC can be approved.
  static const List<String> requiredTypes = <String>['IDENTITY_DOCUMENT', 'PROOF_OF_ADDRESS'];

  static const Map<String, String> typeLabels = <String, String>{
    'IDENTITY_DOCUMENT': 'Identity document',
    'PROOF_OF_ADDRESS': 'Proof of address',
    'PASSPORT_PHOTO': 'Passport photo',
    'PAYSLIP': 'Payslip',
    'BANK_STATEMENT': 'Bank statement',
    'BUSINESS_LICENCE': 'Business licence',
    'TAX_CERTIFICATE': 'Tax certificate',
    'OTHER': 'Other',
  };
}
