import 'package:flutter_test/flutter_test.dart';
import 'package:mfin_mobile/core/utils/formatters.dart';

void main() {
  group('Money formatting', () {
    test('formats a string amount without losing precision', () {
      expect(Formatters.money('8884.88'), '8,884.88');
    });

    test('prefixes the currency when one is supplied', () {
      expect(Formatters.money('1000', currency: 'KES'), 'KES 1,000.00');
    });

    test('treats a null or unparseable amount as zero rather than crashing', () {
      expect(Formatters.money(null), '0.00');
      expect(Formatters.money('not-a-number'), '0.00');
    });

    test('compacts large figures for dashboard tiles', () {
      expect(Formatters.moneyCompact('2500000'), '2.5M');
      expect(Formatters.moneyCompact('3500'), '3.5K');
      expect(Formatters.moneyCompact('750'), '750');
    });
  });

  group('Humanising enum names', () {
    test('turns a wire constant into a readable label', () {
      expect(Formatters.humanise('REDUCING_BALANCE'), 'Reducing balance');
      expect(Formatters.humanise('PAR_90_PLUS'), 'Par 90 plus');
    });

    test('renders a missing value as a dash', () {
      expect(Formatters.humanise(null), '-');
    });
  });

  group('Initials', () {
    test('uses the first and last name', () {
      expect(Formatters.initials('Jane Wanjiru Otieno'), 'JO');
    });

    test('handles a single name', () {
      expect(Formatters.initials('Jane'), 'J');
    });

    test('handles empty input', () {
      expect(Formatters.initials(''), '?');
      expect(Formatters.initials(null), '?');
    });
  });
}
