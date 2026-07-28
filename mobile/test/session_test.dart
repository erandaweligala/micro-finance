import 'package:flutter_test/flutter_test.dart';
import 'package:mfin_mobile/features/auth/domain/session.dart';

/// The capability getters on [Session] decide what the UI offers. They must
/// match the backend's @PreAuthorize rules - a mismatch either hides work a user
/// is entitled to do, or offers an action the server will refuse.
void main() {
  Session sessionWith(List<UserRole> roles) => Session(
        userId: 'user-1',
        username: 'test.user',
        fullName: 'Test User',
        roles: roles.toSet(),
        tenantId: 'tenant-1',
      );

  group('Role parsing', () {
    test('maps known wire names', () {
      expect(UserRole.fromWire('LOAN_OFFICER'), UserRole.loanOfficer);
    });

    test('ignores an unknown role rather than failing', () {
      // The backend may add a role before the app ships; it must grant nothing.
      expect(UserRole.fromWire('FUTURE_ROLE'), isNull);
    });

    test('drops unknown roles when building a session', () {
      final Session session = Session.fromJson(<String, dynamic>{
        'userId': 'u1',
        'username': 'u',
        'fullName': 'U',
        'roles': <String>['CASHIER', 'FUTURE_ROLE'],
      });
      expect(session.roles, <UserRole>{UserRole.cashier});
    });
  });

  group('Capabilities', () {
    test('a cashier may take payments but not approve loans', () {
      final Session session = sessionWith(<UserRole>[UserRole.cashier]);
      expect(session.canCapturePayments, isTrue);
      expect(session.canApproveLoans, isFalse);
      expect(session.canReversePayments, isFalse);
    });

    test('a loan officer may create applications but not approve them', () {
      final Session session = sessionWith(<UserRole>[UserRole.loanOfficer]);
      expect(session.canCreateApplications, isTrue);
      expect(session.canApproveLoans, isFalse);
    });

    test('a branch manager may approve loans and reverse payments', () {
      final Session session = sessionWith(<UserRole>[UserRole.branchManager]);
      expect(session.canApproveLoans, isTrue);
      expect(session.canReversePayments, isTrue);
      expect(session.canViewReports, isTrue);
    });

    test('an auditor may read reports and audit, but may not write', () {
      final Session session = sessionWith(<UserRole>[UserRole.auditor]);
      expect(session.canViewReports, isTrue);
      expect(session.canViewAudit, isTrue);
      expect(session.canManageCustomers, isFalse);
      expect(session.canCapturePayments, isFalse);
    });

    test('a borrower has none of the staff capabilities', () {
      final Session session = sessionWith(<UserRole>[UserRole.customer]);
      expect(session.isBorrower, isTrue);
      expect(session.canManageCustomers, isFalse);
      expect(session.canCapturePayments, isFalse);
      expect(session.canViewReports, isFalse);
    });

    test('only a platform administrator is marked as such', () {
      expect(sessionWith(<UserRole>[UserRole.platformAdmin]).isPlatformAdmin, isTrue);
      expect(sessionWith(<UserRole>[UserRole.tenantAdmin]).isPlatformAdmin, isFalse);
    });

    test('the primary role is the most senior one held', () {
      final Session session =
          sessionWith(<UserRole>[UserRole.cashier, UserRole.branchManager]);
      expect(session.primaryRole, UserRole.branchManager);
    });
  });
}
