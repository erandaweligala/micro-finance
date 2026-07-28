import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/config/api_endpoints.dart';
import '../../../core/network/api_client.dart';
import '../../../core/providers.dart';
import '../../shared/domain/paged.dart';
import '../domain/payment.dart';

class PaymentRepository {
  const PaymentRepository(this._client);

  final ApiClient _client;

  /// Records a repayment.
  ///
  /// [idempotencyKey] must be generated once per logical payment and reused for
  /// every retry of it. That is what makes a timeout safe: the server returns the
  /// original receipt instead of taking the money twice.
  Future<Payment> capture({
    required String idempotencyKey,
    required String loanAccountId,
    required String amount,
    required String method,
    required DateTime valueDate,
    String? externalReference,
    String? narrative,
  }) async {
    final Map<String, dynamic> json = await _client.post(
      ApiEndpoints.payments,
      idempotencyKey: idempotencyKey,
      body: <String, dynamic>{
        'loanAccountId': loanAccountId,
        'amount': amount,
        'method': method,
        'valueDate': valueDate.toIso8601String().substring(0, 10),
        'externalReference': externalReference,
        'narrative': narrative,
      },
    );
    return Payment.fromJson(json);
  }

  Future<Payment> get(String id) async =>
      Payment.fromJson(await _client.get(ApiEndpoints.payment(id)));

  Future<Payment> reverse(String id, String reason) async => Payment.fromJson(
        await _client.post(
          ApiEndpoints.reversePayment(id),
          body: <String, dynamic>{'reason': reason},
        ),
      );

  Future<Paged<Payment>> search({String? loanAccountId, String? customerId, int page = 0}) async {
    final Map<String, dynamic> json = await _client.get(
      ApiEndpoints.payments,
      queryParameters: <String, dynamic>{
        'loanAccountId': loanAccountId,
        'customerId': customerId,
        'page': page,
      },
    );
    return Paged<Payment>.fromJson(json, Payment.fromJson);
  }
}

final Provider<PaymentRepository> paymentRepositoryProvider =
    Provider<PaymentRepository>((Ref ref) => PaymentRepository(ref.watch(apiClientProvider)));

final FutureProviderFamily<Payment, String> paymentProvider =
    FutureProvider.family<Payment, String>(
  (Ref ref, String id) => ref.watch(paymentRepositoryProvider).get(id),
);

final FutureProviderFamily<Paged<Payment>, String> loanPaymentsProvider =
    FutureProvider.family<Paged<Payment>, String>(
  (Ref ref, String loanAccountId) =>
      ref.watch(paymentRepositoryProvider).search(loanAccountId: loanAccountId),
);
