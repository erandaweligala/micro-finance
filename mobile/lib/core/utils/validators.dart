/// Form validators.
///
/// These mirror the backend's Bean Validation rules so a user is told about a
/// problem before a round trip. The server still validates everything: client
/// validation is a courtesy, never a control.
class Validators {
  const Validators._();

  static String? required(String? value, {String field = 'This field'}) {
    if (value == null || value.trim().isEmpty) {
      return '$field is required';
    }
    return null;
  }

  static String? organisation(String? value) {
    final String? missing = required(value, field: 'Organisation');
    if (missing != null) {
      return missing;
    }
    if (!RegExp(r'^[a-z0-9-]+$').hasMatch(value!.trim())) {
      return 'Use lower-case letters, numbers and hyphens only';
    }
    return null;
  }

  static String? email(String? value, {bool isRequired = true}) {
    if (value == null || value.trim().isEmpty) {
      return isRequired ? 'Email address is required' : null;
    }
    if (!RegExp(r'^[\w.+-]+@[\w-]+\.[\w.-]+$').hasMatch(value.trim())) {
      return 'Enter a valid email address';
    }
    return null;
  }

  static String? phone(String? value, {bool isRequired = true}) {
    if (value == null || value.trim().isEmpty) {
      return isRequired ? 'Phone number is required' : null;
    }
    if (!RegExp(r'^\+?[0-9 ()-]{7,20}$').hasMatch(value.trim())) {
      return 'Enter a valid phone number';
    }
    return null;
  }

  /// Matches the backend password policy, so the rules are never a surprise.
  static String? password(String? value) {
    if (value == null || value.isEmpty) {
      return 'Password is required';
    }
    final List<String> unmet = <String>[];
    if (value.length < 12) {
      unmet.add('12 characters');
    }
    if (!RegExp('[A-Z]').hasMatch(value)) {
      unmet.add('an upper-case letter');
    }
    if (!RegExp('[a-z]').hasMatch(value)) {
      unmet.add('a lower-case letter');
    }
    if (!RegExp('[0-9]').hasMatch(value)) {
      unmet.add('a digit');
    }
    if (!RegExp(r'[^A-Za-z0-9]').hasMatch(value)) {
      unmet.add('a symbol');
    }
    return unmet.isEmpty ? null : 'Password needs ${unmet.join(', ')}';
  }

  /// Validates a monetary input. Rejects anything that is not a plain decimal,
  /// so a stray character can never be sent to a financial endpoint.
  static String? amount(
    String? value, {
    String field = 'Amount',
    bool isRequired = true,
    double? min,
    double? max,
  }) {
    if (value == null || value.trim().isEmpty) {
      return isRequired ? '$field is required' : null;
    }
    final String cleaned = value.replaceAll(',', '').trim();
    final double? parsed = double.tryParse(cleaned);
    if (parsed == null) {
      return 'Enter a valid amount';
    }
    if (parsed <= 0) {
      return '$field must be greater than zero';
    }
    if (min != null && parsed < min) {
      return '$field must be at least ${min.toStringAsFixed(2)}';
    }
    if (max != null && parsed > max) {
      return '$field must not exceed ${max.toStringAsFixed(2)}';
    }
    return null;
  }

  static String? rate(String? value) {
    if (value == null || value.trim().isEmpty) {
      return 'Interest rate is required';
    }
    final double? parsed = double.tryParse(value.trim());
    if (parsed == null) {
      return 'Enter a valid rate';
    }
    if (parsed < 0) {
      return 'Rate cannot be negative';
    }
    if (parsed > 200) {
      return 'Rate cannot exceed 200%';
    }
    return null;
  }

  static String? installments(String? value) {
    if (value == null || value.trim().isEmpty) {
      return 'Number of installments is required';
    }
    final int? parsed = int.tryParse(value.trim());
    if (parsed == null) {
      return 'Enter a whole number';
    }
    if (parsed < 1 || parsed > 600) {
      return 'Must be between 1 and 600';
    }
    return null;
  }

  /// A borrower must be an adult to hold a loan contract.
  static String? dateOfBirth(DateTime? value) {
    if (value == null) {
      return 'Date of birth is required';
    }
    final DateTime today = DateTime.now();
    if (!value.isBefore(today)) {
      return 'Date of birth must be in the past';
    }
    final int age = today.year -
        value.year -
        ((today.month < value.month || (today.month == value.month && today.day < value.day))
            ? 1
            : 0);
    if (age < 18) {
      return 'Customer must be at least 18 years old';
    }
    return null;
  }
}
