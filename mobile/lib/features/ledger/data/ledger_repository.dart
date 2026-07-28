import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/config/api_endpoints.dart';
import '../../../core/network/api_client.dart';
import '../../../core/providers.dart';
import '../domain/ledger_entry.dart';

class LedgerRepository {
  const LedgerRepository(this._client);

  final ApiClient _client;

  Future<Statement> statement(String loanAccountId, {DateTime? from, DateTime? to}) async {
    final Map<String, dynamic> json = await _client.get(
      ApiEndpoints.statement(loanAccountId),
      queryParameters: <String, dynamic>{
        'from': from?.toIso8601String().substring(0, 10),
        'to': to?.toIso8601String().substring(0, 10),
      },
    );
    return Statement.fromJson(json);
  }
}

final Provider<LedgerRepository> ledgerRepositoryProvider =
    Provider<LedgerRepository>((Ref ref) => LedgerRepository(ref.watch(apiClientProvider)));

final FutureProviderFamily<Statement, String> statementProvider =
    FutureProvider.family<Statement, String>(
  (Ref ref, String loanAccountId) =>
      ref.watch(ledgerRepositoryProvider).statement(loanAccountId),
);
