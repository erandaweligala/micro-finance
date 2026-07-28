import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/providers.dart';
import '../domain/loan_schedule.dart';
import 'calculator_repository.dart';

final Provider<CalculatorRepository> calculatorRepositoryProvider =
    Provider<CalculatorRepository>(
  (Ref ref) => CalculatorRepository(ref.watch(apiClientProvider)),
);

/// Calculates a schedule for the supplied terms.
///
/// Keyed on the input, so changing any field produces a new request while
/// re-displaying an identical set of terms is served from cache.
final FutureProviderFamily<LoanSchedule, CalculationInput> loanScheduleProvider =
    FutureProvider.family<LoanSchedule, CalculationInput>((Ref ref, CalculationInput input) {
  return ref.watch(calculatorRepositoryProvider).calculate(input);
});
