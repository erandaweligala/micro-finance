import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/config/api_endpoints.dart';
import '../../../core/network/api_client.dart';
import '../../../core/providers.dart';
import '../domain/dashboard_summary.dart';

class DashboardRepository {
  const DashboardRepository(this._client);

  final ApiClient _client;

  Future<DashboardSummary> dashboard() async =>
      DashboardSummary.fromJson(await _client.get(ApiEndpoints.dashboard));

  Future<PortfolioSummary> portfolio() async =>
      PortfolioSummary.fromJson(await _client.get(ApiEndpoints.portfolioReport));

  Future<ArrearsAging> arrearsAging() async =>
      ArrearsAging.fromJson(await _client.get(ApiEndpoints.arrearsReport));
}

final Provider<DashboardRepository> dashboardRepositoryProvider =
    Provider<DashboardRepository>((Ref ref) => DashboardRepository(ref.watch(apiClientProvider)));

final FutureProvider<DashboardSummary> dashboardSummaryProvider =
    FutureProvider<DashboardSummary>(
  (Ref ref) => ref.watch(dashboardRepositoryProvider).dashboard(),
);

final FutureProvider<PortfolioSummary> portfolioProvider = FutureProvider<PortfolioSummary>(
  (Ref ref) => ref.watch(dashboardRepositoryProvider).portfolio(),
);

final FutureProvider<ArrearsAging> arrearsAgingProvider = FutureProvider<ArrearsAging>(
  (Ref ref) => ref.watch(dashboardRepositoryProvider).arrearsAging(),
);
