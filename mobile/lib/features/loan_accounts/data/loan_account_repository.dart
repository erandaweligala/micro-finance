import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/config/api_endpoints.dart';
import '../../../core/network/api_client.dart';
import '../../../core/providers.dart';
import '../../loan_calculator/domain/loan_schedule.dart';
import '../../shared/domain/paged.dart';
import '../domain/loan_account.dart';

class LoanAccountRepository {
  const LoanAccountRepository(this._client);

  final ApiClient _client;

  Future<Paged<LoanAccount>> search({
    String? status,
    String? customerId,
    String? query,
    int page = 0,
  }) async {
    final Map<String, dynamic> json = await _client.get(
      ApiEndpoints.loanAccounts,
      queryParameters: <String, dynamic>{
        'status': status,
        'customerId': customerId,
        'query': query,
        'page': page,
      },
    );
    return Paged<LoanAccount>.fromJson(json, LoanAccount.fromJson);
  }

  Future<LoanAccount> get(String id) async =>
      LoanAccount.fromJson(await _client.get(ApiEndpoints.loanAccount(id)));

  /// The live schedule, showing what has been paid against each installment.
  Future<List<ScheduledInstallment>> schedule(String id) async {
    final Map<String, dynamic> json = await _client.get(
      ApiEndpoints.loanSchedule(id),
      queryParameters: <String, dynamic>{'size': 600},
    );
    final List<dynamic> content = (json['content'] as List<dynamic>?) ?? <dynamic>[];
    return content
        .map((dynamic item) => ScheduledInstallment.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  Future<PayoffQuote> payoffQuote(String id) async =>
      PayoffQuote.fromJson(await _client.get(ApiEndpoints.payoffQuote(id)));
}

final Provider<LoanAccountRepository> loanAccountRepositoryProvider =
    Provider<LoanAccountRepository>(
  (Ref ref) => LoanAccountRepository(ref.watch(apiClientProvider)),
);

final FutureProviderFamily<LoanAccount, String> loanAccountProvider =
    FutureProvider.family<LoanAccount, String>(
  (Ref ref, String id) => ref.watch(loanAccountRepositoryProvider).get(id),
);

final FutureProviderFamily<List<ScheduledInstallment>, String> loanScheduleProvider =
    FutureProvider.family<List<ScheduledInstallment>, String>(
  (Ref ref, String id) => ref.watch(loanAccountRepositoryProvider).schedule(id),
);

final FutureProviderFamily<PayoffQuote, String> payoffQuoteProvider =
    FutureProvider.family<PayoffQuote, String>(
  (Ref ref, String id) => ref.watch(loanAccountRepositoryProvider).payoffQuote(id),
);

final FutureProviderFamily<Paged<LoanAccount>, String> customerLoansProvider =
    FutureProvider.family<Paged<LoanAccount>, String>(
  (Ref ref, String customerId) =>
      ref.watch(loanAccountRepositoryProvider).search(customerId: customerId),
);
