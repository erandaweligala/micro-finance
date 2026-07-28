import '../../../core/config/api_endpoints.dart';
import '../../../core/network/api_client.dart';
import '../domain/loan_schedule.dart';

/// Calls the backend calculator.
///
/// The app never computes an amortisation schedule itself. Duplicating the
/// formula in Dart would guarantee that the quotation and the booked loan
/// eventually disagree - the two implementations would drift on rounding alone.
class CalculatorRepository {
  const CalculatorRepository(this._client);

  final ApiClient _client;

  Future<LoanSchedule> calculate(CalculationInput input) async {
    final String path = input.productId == null
        ? ApiEndpoints.loanCalculations
        : ApiEndpoints.productCalculate(input.productId!);
    final Map<String, dynamic> json = await _client.post(path, body: input.toJson());
    return LoanSchedule.fromJson(json);
  }
}
