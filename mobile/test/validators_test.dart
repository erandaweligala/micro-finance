import 'package:flutter_test/flutter_test.dart';
import 'package:mfin_mobile/core/utils/validators.dart';

/// The client validators mirror the backend's Bean Validation rules. When the
/// two drift, users are told a value is fine and then rejected by the server -
/// so these tests pin the client side to the same expectations.
void main() {
  group('Password policy', () {
    test('accepts a password meeting every rule', () {
      expect(Validators.password('Str0ng!Passphrase'), isNull);
    });

    test('rejects a password that is too short', () {
      expect(Validators.password('Ab1!xyz'), contains('12 characters'));
    });

    test('lists every unmet rule at once rather than one at a time', () {
      final String? result = Validators.password('aaaaaaaaaaaa');
      expect(result, contains('upper-case'));
      expect(result, contains('digit'));
      expect(result, contains('symbol'));
    });

    test('rejects an empty password', () {
      expect(Validators.password(''), isNotNull);
    });
  });

  group('Amount', () {
    test('accepts a plain decimal', () {
      expect(Validators.amount('1500.50'), isNull);
    });

    test('accepts thousands separators', () {
      expect(Validators.amount('1,500.50'), isNull);
    });

    test('rejects zero and negatives', () {
      expect(Validators.amount('0'), contains('greater than zero'));
      expect(Validators.amount('-10'), contains('greater than zero'));
    });

    test('rejects anything that is not a number', () {
      expect(Validators.amount('abc'), contains('valid amount'));
    });

    test('enforces product bounds', () {
      expect(Validators.amount('50', min: 100), contains('at least'));
      expect(Validators.amount('5000', max: 1000), contains('not exceed'));
    });

    test('allows a blank optional amount', () {
      expect(Validators.amount('', isRequired: false), isNull);
    });
  });

  group('Interest rate', () {
    test('accepts a rate in range', () {
      expect(Validators.rate('12.5'), isNull);
    });

    test('accepts a zero-interest loan', () {
      expect(Validators.rate('0'), isNull);
    });

    test('rejects a negative rate', () {
      expect(Validators.rate('-1'), contains('negative'));
    });

    test('rejects an absurd rate, matching the engine bound', () {
      expect(Validators.rate('500'), contains('200'));
    });
  });

  group('Installments', () {
    test('accepts a normal term', () {
      expect(Validators.installments('12'), isNull);
    });

    test('rejects zero and over-long terms', () {
      expect(Validators.installments('0'), isNotNull);
      expect(Validators.installments('601'), isNotNull);
    });

    test('rejects a fractional term', () {
      expect(Validators.installments('12.5'), contains('whole number'));
    });
  });

  group('Date of birth', () {
    test('accepts an adult', () {
      final DateTime adult = DateTime.now().subtract(const Duration(days: 365 * 30));
      expect(Validators.dateOfBirth(adult), isNull);
    });

    test('rejects a minor, matching the backend rule', () {
      final DateTime minor = DateTime.now().subtract(const Duration(days: 365 * 15));
      expect(Validators.dateOfBirth(minor), contains('18'));
    });

    test('rejects a future date of birth', () {
      final DateTime future = DateTime.now().add(const Duration(days: 1));
      expect(Validators.dateOfBirth(future), contains('past'));
    });

    test('rejects a missing date', () {
      expect(Validators.dateOfBirth(null), isNotNull);
    });
  });

  group('Organisation identifier', () {
    test('accepts a slug', () {
      expect(Validators.organisation('acme-microfinance'), isNull);
    });

    test('rejects upper case and spaces', () {
      expect(Validators.organisation('Acme Micro'), isNotNull);
    });
  });
}
