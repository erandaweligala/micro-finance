import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/config/api_endpoints.dart';
import '../../../core/config/app_config.dart';
import '../../../core/network/api_client.dart';
import '../../../core/providers.dart';
import '../../shared/domain/paged.dart';
import '../domain/customer.dart';

class CustomerRepository {
  const CustomerRepository(this._client);

  final ApiClient _client;

  Future<Paged<Customer>> search({
    String? query,
    String? status,
    String? kycStatus,
    int page = 0,
    int size = AppConfig.defaultPageSize,
  }) async {
    final Map<String, dynamic> json = await _client.get(
      ApiEndpoints.customers,
      queryParameters: <String, dynamic>{
        'query': query,
        'status': status,
        'kycStatus': kycStatus,
        'page': page,
        'size': size,
      },
    );
    return Paged<Customer>.fromJson(json, Customer.fromJson);
  }

  Future<Customer> get(String id) async =>
      Customer.fromJson(await _client.get(ApiEndpoints.customer(id)));

  Future<Customer> register(Map<String, dynamic> body) async =>
      Customer.fromJson(await _client.post(ApiEndpoints.customers, body: body));

  Future<Customer> update(String id, Map<String, dynamic> body) async =>
      Customer.fromJson(await _client.put(ApiEndpoints.customer(id), body: body));

  Future<Customer> deactivate(String id, String reason) async => Customer.fromJson(
        await _client.post(
          ApiEndpoints.customerDeactivate(id),
          body: <String, dynamic>{'reason': reason},
        ),
      );

  Future<List<KycDocument>> documents(String customerId) async {
    final List<dynamic> json = await _client.getList(ApiEndpoints.kycDocuments(customerId));
    return json
        .map((dynamic item) => KycDocument.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  Future<KycDocument> uploadDocument(String customerId, Map<String, dynamic> body) async =>
      KycDocument.fromJson(await _client.post(ApiEndpoints.kycDocuments(customerId), body: body));

  Future<void> verifyDocument({
    required String customerId,
    required String documentId,
    required bool approved,
    String? reason,
  }) async {
    await _client.post(
      ApiEndpoints.kycVerifyDocument(customerId, documentId),
      body: <String, dynamic>{'approved': approved, 'reason': reason},
    );
  }

  Future<void> decideKyc({
    required String customerId,
    required bool approved,
    String? reason,
  }) async {
    await _client.post(
      ApiEndpoints.kycDecision(customerId),
      body: <String, dynamic>{'approved': approved, 'reason': reason},
    );
  }
}

final Provider<CustomerRepository> customerRepositoryProvider =
    Provider<CustomerRepository>((Ref ref) => CustomerRepository(ref.watch(apiClientProvider)));

/// Filters for the customer list, held separately from the query so that
/// changing a filter re-runs the search without rebuilding the whole screen.
class CustomerQuery {
  const CustomerQuery({this.search, this.status, this.kycStatus});

  final String? search;
  final String? status;
  final String? kycStatus;

  CustomerQuery copyWith({String? search, String? status, String? kycStatus, bool clear = false}) =>
      CustomerQuery(
        search: clear ? null : (search ?? this.search),
        status: clear ? null : (status ?? this.status),
        kycStatus: clear ? null : (kycStatus ?? this.kycStatus),
      );

  @override
  bool operator ==(Object other) =>
      other is CustomerQuery &&
      other.search == search &&
      other.status == status &&
      other.kycStatus == kycStatus;

  @override
  int get hashCode => Object.hash(search, status, kycStatus);
}

final StateProvider<CustomerQuery> customerQueryProvider =
    StateProvider<CustomerQuery>((Ref ref) => const CustomerQuery());

final FutureProvider<Paged<Customer>> customerListProvider =
    FutureProvider<Paged<Customer>>((Ref ref) {
  final CustomerQuery query = ref.watch(customerQueryProvider);
  return ref.watch(customerRepositoryProvider).search(
        query: query.search,
        status: query.status,
        kycStatus: query.kycStatus,
      );
});

final FutureProviderFamily<Customer, String> customerProvider =
    FutureProvider.family<Customer, String>(
  (Ref ref, String id) => ref.watch(customerRepositoryProvider).get(id),
);

final FutureProviderFamily<List<KycDocument>, String> kycDocumentsProvider =
    FutureProvider.family<List<KycDocument>, String>(
  (Ref ref, String customerId) => ref.watch(customerRepositoryProvider).documents(customerId),
);
