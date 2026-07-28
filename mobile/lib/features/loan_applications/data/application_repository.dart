import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/config/api_endpoints.dart';
import '../../../core/network/api_client.dart';
import '../../../core/providers.dart';
import '../../shared/domain/paged.dart';
import '../domain/loan_application.dart';

class ApplicationRepository {
  const ApplicationRepository(this._client);

  final ApiClient _client;

  Future<Paged<LoanApplication>> search({
    String? status,
    String? customerId,
    String? query,
    int page = 0,
  }) async {
    final Map<String, dynamic> json = await _client.get(
      ApiEndpoints.loanApplications,
      queryParameters: <String, dynamic>{
        'status': status,
        'customerId': customerId,
        'query': query,
        'page': page,
      },
    );
    return Paged<LoanApplication>.fromJson(json, LoanApplication.fromJson);
  }

  Future<LoanApplication> get(String id) async =>
      LoanApplication.fromJson(await _client.get(ApiEndpoints.loanApplication(id)));

  Future<LoanApplication> create(Map<String, dynamic> body) async =>
      LoanApplication.fromJson(await _client.post(ApiEndpoints.loanApplications, body: body));

  Future<LoanApplication> submit(String id) async =>
      LoanApplication.fromJson(await _client.post(ApiEndpoints.applicationSubmit(id)));

  /// Records one approval. The loan is only APPROVED once every configured level
  /// has signed off, which is why the response is re-read rather than assumed.
  Future<LoanApplication> approve(
    String id, {
    String? comment,
    String? approvedAmount,
    int? approvedInstallments,
  }) async =>
      LoanApplication.fromJson(
        await _client.post(
          ApiEndpoints.applicationApprove(id),
          body: <String, dynamic>{
            'comment': comment,
            'approvedAmount': approvedAmount,
            'approvedInstallments': approvedInstallments,
          },
        ),
      );

  Future<LoanApplication> reject(String id, String reason) async => LoanApplication.fromJson(
        await _client.post(
          ApiEndpoints.applicationReject(id),
          body: <String, dynamic>{'reason': reason},
        ),
      );

  Future<LoanApplication> disburse(
    String id, {
    required String method,
    required String reference,
    required DateTime disbursementDate,
    required String amount,
  }) async =>
      LoanApplication.fromJson(
        await _client.post(
          ApiEndpoints.applicationDisburse(id),
          body: <String, dynamic>{
            'method': method,
            'reference': reference,
            'disbursementDate': disbursementDate.toIso8601String().substring(0, 10),
            'amount': amount,
          },
        ),
      );

  Future<List<LoanProduct>> products() async {
    final Map<String, dynamic> json = await _client.get(
      ApiEndpoints.loanProducts,
      queryParameters: <String, dynamic>{'status': 'ACTIVE', 'size': 100},
    );
    final List<dynamic> content = (json['content'] as List<dynamic>?) ?? <dynamic>[];
    return content
        .map((dynamic item) => LoanProduct.fromJson(item as Map<String, dynamic>))
        .toList();
  }
}

final Provider<ApplicationRepository> applicationRepositoryProvider =
    Provider<ApplicationRepository>(
  (Ref ref) => ApplicationRepository(ref.watch(apiClientProvider)),
);

/// Status filter for the applications work queue.
final StateProvider<String?> applicationStatusFilterProvider =
    StateProvider<String?>((Ref ref) => null);

final FutureProvider<Paged<LoanApplication>> applicationListProvider =
    FutureProvider<Paged<LoanApplication>>((Ref ref) {
  final String? status = ref.watch(applicationStatusFilterProvider);
  return ref.watch(applicationRepositoryProvider).search(status: status);
});

final FutureProviderFamily<LoanApplication, String> applicationProvider =
    FutureProvider.family<LoanApplication, String>(
  (Ref ref, String id) => ref.watch(applicationRepositoryProvider).get(id),
);

final FutureProvider<List<LoanProduct>> loanProductsProvider = FutureProvider<List<LoanProduct>>(
  (Ref ref) => ref.watch(applicationRepositoryProvider).products(),
);
