import 'package:equatable/equatable.dart';

/// The roles the backend grants. Kept as an enum so a screen cannot test for a
/// role that does not exist, and so a new role is a compile-time decision.
enum UserRole {
  platformAdmin('PLATFORM_ADMIN', 'Platform administrator'),
  tenantAdmin('TENANT_ADMIN', 'Administrator'),
  branchManager('BRANCH_MANAGER', 'Branch manager'),
  loanOfficer('LOAN_OFFICER', 'Loan officer'),
  cashier('CASHIER', 'Cashier'),
  auditor('AUDITOR', 'Auditor'),
  customer('CUSTOMER', 'Customer');

  const UserRole(this.wireName, this.label);

  final String wireName;
  final String label;

  static UserRole? fromWire(String value) {
    for (final UserRole role in UserRole.values) {
      if (role.wireName == value) {
        return role;
      }
    }
    // Unknown roles are ignored rather than crashing: the backend may add one
    // before the app is updated, and an unrecognised role should simply grant
    // nothing extra.
    return null;
  }
}

/// The signed-in user.
///
/// Capabilities are expressed as named getters rather than role checks scattered
/// through the widget tree, so "who may approve a loan" is answered in one place
/// and matches the backend's `@PreAuthorize` rules.
class Session extends Equatable {
  const Session({
    required this.userId,
    required this.username,
    required this.fullName,
    required this.roles,
    this.tenantId,
    this.tenantSlug,
    this.mustChangePassword = false,
  });

  final String userId;
  final String username;
  final String fullName;
  final Set<UserRole> roles;
  final String? tenantId;
  final String? tenantSlug;
  final bool mustChangePassword;

  factory Session.fromJson(Map<String, dynamic> json) {
    final List<dynamic> rawRoles = (json['roles'] as List<dynamic>?) ?? <dynamic>[];
    return Session(
      userId: json['userId'] as String? ?? json['id'] as String? ?? '',
      username: json['username'] as String? ?? '',
      fullName: json['fullName'] as String? ?? '',
      tenantId: json['tenantId'] as String?,
      tenantSlug: json['tenantSlug'] as String?,
      mustChangePassword: json['mustChangePassword'] as bool? ?? false,
      roles: rawRoles
          .map((dynamic role) => UserRole.fromWire(role as String))
          .whereType<UserRole>()
          .toSet(),
    );
  }

  bool hasRole(UserRole role) => roles.contains(role);

  bool hasAnyRole(Iterable<UserRole> candidates) => candidates.any(roles.contains);

  // --- Capabilities, mirroring the backend's authorisation rules ---

  bool get isPlatformAdmin => hasRole(UserRole.platformAdmin);

  bool get isBorrower => roles.length == 1 && hasRole(UserRole.customer);

  bool get canManageTenant => hasAnyRole(<UserRole>[UserRole.platformAdmin, UserRole.tenantAdmin]);

  bool get canManageCustomers => hasAnyRole(<UserRole>[
        UserRole.tenantAdmin,
        UserRole.branchManager,
        UserRole.loanOfficer,
      ]);

  bool get canCreateApplications => canManageCustomers;

  bool get canApproveLoans =>
      hasAnyRole(<UserRole>[UserRole.tenantAdmin, UserRole.branchManager]);

  bool get canCapturePayments => hasAnyRole(<UserRole>[
        UserRole.tenantAdmin,
        UserRole.branchManager,
        UserRole.cashier,
      ]);

  bool get canReversePayments =>
      hasAnyRole(<UserRole>[UserRole.tenantAdmin, UserRole.branchManager]);

  bool get canViewReports => hasAnyRole(<UserRole>[
        UserRole.platformAdmin,
        UserRole.tenantAdmin,
        UserRole.branchManager,
        UserRole.auditor,
      ]);

  bool get canViewAudit => hasAnyRole(<UserRole>[
        UserRole.platformAdmin,
        UserRole.tenantAdmin,
        UserRole.auditor,
      ]);

  /// The role shown in the UI, highest first.
  UserRole get primaryRole {
    for (final UserRole role in UserRole.values) {
      if (roles.contains(role)) {
        return role;
      }
    }
    return UserRole.customer;
  }

  @override
  List<Object?> get props => <Object?>[userId, tenantId, roles, mustChangePassword];
}

/// Public branding for an institution's sign-in screen.
class TenantBranding extends Equatable {
  const TenantBranding({
    required this.slug,
    required this.name,
    this.logoUrl,
    this.primaryColor,
    this.secondaryColor,
    this.defaultCurrency = 'KES',
    this.loginEnabled = true,
  });

  final String slug;
  final String name;
  final String? logoUrl;
  final String? primaryColor;
  final String? secondaryColor;
  final String defaultCurrency;
  final bool loginEnabled;

  factory TenantBranding.fromJson(Map<String, dynamic> json) => TenantBranding(
        slug: json['slug'] as String? ?? '',
        name: json['name'] as String? ?? '',
        logoUrl: json['logoUrl'] as String?,
        primaryColor: json['primaryColor'] as String?,
        secondaryColor: json['secondaryColor'] as String?,
        defaultCurrency: json['defaultCurrency'] as String? ?? 'KES',
        loginEnabled: json['loginEnabled'] as bool? ?? true,
      );

  @override
  List<Object?> get props => <Object?>[slug, name, primaryColor, loginEnabled];
}
