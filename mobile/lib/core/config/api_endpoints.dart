/// Every API path the app uses, in one place.
///
/// Keeping them here rather than inline means a backend route change is a
/// single-file edit, and it makes the app's full API surface reviewable at a glance.
class ApiEndpoints {
  const ApiEndpoints._();

  // --- Authentication -------------------------------------------------------
  static const String login = '/api/v1/auth/login';
  static const String platformLogin = '/api/v1/auth/platform-login';
  static const String refresh = '/api/v1/auth/refresh';
  static const String logout = '/api/v1/auth/logout';
  static const String changePassword = '/api/v1/auth/change-password';
  static const String forgotPassword = '/api/v1/auth/forgot-password';
  static const String resetPassword = '/api/v1/auth/reset-password';
  static const String currentUser = '/api/v1/users/me';

  // --- Tenancy --------------------------------------------------------------
  static String tenantBranding(String slug) => '/api/v1/tenants/branding/$slug';
  static const String currentTenant = '/api/v1/tenants/current';
  static const String currentSubscription = '/api/v1/tenants/current/subscription';
  static const String branches = '/api/v1/tenants/current/branches';

  // --- Customers and KYC ----------------------------------------------------
  static const String customers = '/api/v1/customers';
  static String customer(String id) => '/api/v1/customers/$id';
  static String customerDeactivate(String id) => '/api/v1/customers/$id/deactivate';
  static String kycDocuments(String customerId) => '/api/v1/customers/$customerId/kyc/documents';
  static String kycDecision(String customerId) => '/api/v1/customers/$customerId/kyc/decision';
  static String kycVerifyDocument(String customerId, String documentId) =>
      '/api/v1/customers/$customerId/kyc/documents/$documentId/verify';

  // --- Products and the calculator ------------------------------------------
  static const String loanProducts = '/api/v1/loan-products';
  static String loanProduct(String id) => '/api/v1/loan-products/$id';
  static const String loanCalculations = '/api/v1/loan-calculations';
  static String productCalculate(String id) => '/api/v1/loan-products/$id/calculate';

  // --- Origination ----------------------------------------------------------
  static const String loanApplications = '/api/v1/loan-applications';
  static String loanApplication(String id) => '/api/v1/loan-applications/$id';
  static String applicationSubmit(String id) => '/api/v1/loan-applications/$id/submit';
  static String applicationApprove(String id) => '/api/v1/loan-applications/$id/approve';
  static String applicationReject(String id) => '/api/v1/loan-applications/$id/reject';
  static String applicationDisburse(String id) => '/api/v1/loan-applications/$id/disburse';
  static String applicationSchedule(String id) => '/api/v1/loan-applications/$id/schedule';

  // --- Loan accounts --------------------------------------------------------
  static const String loanAccounts = '/api/v1/loan-accounts';
  static String loanAccount(String id) => '/api/v1/loan-accounts/$id';
  static String loanSchedule(String id) => '/api/v1/loan-accounts/$id/schedule';
  static String payoffQuote(String id) => '/api/v1/loan-accounts/$id/payoff-quote';

  // --- Payments -------------------------------------------------------------
  static const String payments = '/api/v1/payments';
  static String payment(String id) => '/api/v1/payments/$id';
  static String receipt(String id) => '/api/v1/payments/$id/receipt';
  static String reversePayment(String id) => '/api/v1/payments/$id/reverse';

  // --- Ledger ---------------------------------------------------------------
  static const String ledgerEntries = '/api/v1/ledger/entries';
  static String statement(String loanAccountId) => '/api/v1/ledger/loans/$loanAccountId/statement';

  // --- Reporting ------------------------------------------------------------
  static const String dashboard = '/api/v1/dashboard';
  static const String portfolioReport = '/api/v1/reports/portfolio';
  static const String arrearsReport = '/api/v1/reports/arrears-aging';
  static const String activityReport = '/api/v1/reports/activity';
}
