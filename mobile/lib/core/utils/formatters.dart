import 'package:intl/intl.dart';

/// Presentation helpers for money, dates and identifiers.
///
/// Amounts arrive from the API as strings, not doubles, and are formatted from
/// [num] only at the moment of display. Parsing them into a floating-point type
/// for arithmetic would reintroduce exactly the rounding error the backend takes
/// care to avoid.
class Formatters {
  const Formatters._();

  static final DateFormat _date = DateFormat('d MMM yyyy');
  static final DateFormat _dateTime = DateFormat('d MMM yyyy, HH:mm');
  static final DateFormat _isoDate = DateFormat('yyyy-MM-dd');

  /// Formats an amount for display, with thousands separators and a currency code.
  static String money(Object? amount, {String? currency, int decimals = 2}) {
    final num value = _toNum(amount);
    final NumberFormat format = NumberFormat.currency(
      symbol: currency == null ? '' : '$currency ',
      decimalDigits: decimals,
    );
    return format.format(value).trim();
  }

  /// Compact form for dashboard tiles: 1.2M, 350K.
  static String moneyCompact(Object? amount, {String? currency}) {
    final num value = _toNum(amount);
    final String prefix = currency == null ? '' : '$currency ';
    if (value.abs() >= 1000000) {
      return '$prefix${(value / 1000000).toStringAsFixed(1)}M';
    }
    if (value.abs() >= 1000) {
      return '$prefix${(value / 1000).toStringAsFixed(1)}K';
    }
    return '$prefix${value.toStringAsFixed(0)}';
  }

  static String percent(Object? value, {int decimals = 2}) =>
      '${_toNum(value).toStringAsFixed(decimals)}%';

  static String date(DateTime? value) => value == null ? '-' : _date.format(value.toLocal());

  static String dateTime(DateTime? value) =>
      value == null ? '-' : _dateTime.format(value.toLocal());

  /// ISO-8601 date, the format every API date parameter expects.
  static String isoDate(DateTime value) => _isoDate.format(value);

  static DateTime? parseDate(Object? value) {
    if (value is DateTime) {
      return value;
    }
    if (value is String && value.isNotEmpty) {
      return DateTime.tryParse(value);
    }
    return null;
  }

  /// Turns `PENALTY_FEE_INTEREST_PRINCIPAL` into `Penalty fee interest principal`.
  static String humanise(String? value) {
    if (value == null || value.isEmpty) {
      return '-';
    }
    final String spaced = value.replaceAll('_', ' ').toLowerCase();
    return spaced[0].toUpperCase() + spaced.substring(1);
  }

  static String initials(String? fullName) {
    if (fullName == null || fullName.trim().isEmpty) {
      return '?';
    }
    final List<String> parts = fullName.trim().split(RegExp(r'\s+'));
    if (parts.length == 1) {
      return parts.first.substring(0, 1).toUpperCase();
    }
    return (parts.first.substring(0, 1) + parts.last.substring(0, 1)).toUpperCase();
  }

  static num _toNum(Object? amount) {
    if (amount is num) {
      return amount;
    }
    if (amount is String) {
      return num.tryParse(amount) ?? 0;
    }
    return 0;
  }
}
