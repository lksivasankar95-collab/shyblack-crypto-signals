import 'package:cryptosignals/data/models/portfolio_account_model.dart';
import 'package:cryptosignals/domain/entities/portfolio_account.dart';
import 'package:flutter_test/flutter_test.dart';

/// Parsing contract for the unified Portfolio payloads.
///
/// The rule under test: a missing numeric field is null, never zero. Every
/// financial field in this file exists to prove that.
void main() {
  group('mode and category parsing', () {
    test('parses the exact backend enum values', () {
      expect(PortfolioMode.parse('PAPER'), PortfolioMode.paper);
      expect(PortfolioMode.parse('LIVE'), PortfolioMode.live);
      expect(PortfolioAccountModel.parseCategory('MAIN'), PortfolioCategory.main);
      expect(PortfolioAccountModel.parseCategory('SPOT'), PortfolioCategory.spot);
      expect(PortfolioAccountModel.parseCategory('FUTURES'), PortfolioCategory.futures);
      expect(PortfolioAccountModel.parseCategory('OPTIONS'), PortfolioCategory.options);
    });

    test('sends the exact API values the backend expects', () {
      expect(PortfolioMode.paper.apiValue, 'PAPER');
      expect(PortfolioMode.live.apiValue, 'LIVE');
      expect(PortfolioCategory.main.apiValue, 'MAIN');
      expect(PortfolioCategory.futures.apiValue, 'FUTURES');
    });

    test('an unrecognised value falls back to paper and main rather than throwing', () {
      expect(PortfolioMode.parse('NONSENSE'), PortfolioMode.paper);
      expect(PortfolioAccountModel.parseCategory('NONSENSE'), PortfolioCategory.main);
    });
  });

  group('availability parsing', () {
    test('maps every availability the backend can report', () {
      expect(PortfolioAvailability.parse('AVAILABLE'), PortfolioAvailability.available);
      expect(PortfolioAvailability.parse('STALE'), PortfolioAvailability.stale);
      expect(PortfolioAvailability.parse('UNAVAILABLE'), PortfolioAvailability.unavailable);
      expect(PortfolioAvailability.parse('NOT_SUPPORTED'), PortfolioAvailability.unsupported);
      expect(PortfolioAvailability.parse('NOT_CONNECTED'), PortfolioAvailability.notConnected);
      expect(PortfolioAvailability.parse('ERROR'), PortfolioAvailability.error);
    });

    test('an unknown or absent availability is treated as unavailable, never available', () {
      expect(PortfolioAvailability.parse(null), PortfolioAvailability.unavailable);
      expect(PortfolioAvailability.parse('WHATEVER'), PortfolioAvailability.unavailable);
      expect(PortfolioAvailability.parse('SOMETHING').isAvailable, isFalse);
    });
  });

  group('null is never zero', () {
    test('a fully absent account parses every financial field as null', () {
      final account = PortfolioAccountModel.accountFromJson(const {
        'accountMode': 'LIVE',
        'accountCategory': 'SPOT',
        'availability': 'UNAVAILABLE',
      });

      expect(account.equity, isNull);
      expect(account.availableBalance, isNull);
      expect(account.invested, isNull);
      expect(account.realizedPnl, isNull);
      expect(account.unrealizedPnl, isNull);
      expect(account.totalPositionCount, isNull);
      expect(account.openPositionCount, isNull);
      expect(account.orderCount, isNull);
      expect(account.quoteCurrency, isNull);
      expect(account.exchange, isNull);
      expect(account.statusMessage, isNull);
    });

    test('an explicit zero from the backend is preserved as a real zero', () {
      final account = PortfolioAccountModel.accountFromJson(const {
        'accountMode': 'PAPER',
        'accountCategory': 'MAIN',
        'availability': 'AVAILABLE',
        'equity': 0,
        'availableBalance': 0,
        'invested': 0,
        'realizedPnl': 0,
        'unrealizedPnl': 0,
        'openPositionCount': 0,
        'totalPositionCount': 0,
      });

      expect(account.equity, 0);
      expect(account.availableBalance, 0);
      expect(account.openPositionCount, 0);
    });

    test('a zero and a null are therefore distinguishable', () {
      final withZero = PortfolioAccountModel.accountFromJson(const {
        'accountMode': 'PAPER',
        'accountCategory': 'MAIN',
        'availability': 'AVAILABLE',
        'equity': 0,
      });
      final withNull = PortfolioAccountModel.accountFromJson(const {
        'accountMode': 'PAPER',
        'accountCategory': 'MAIN',
        'availability': 'AVAILABLE',
        'equity': null,
      });

      expect(withZero.equity, isNotNull);
      expect(withNull.equity, isNull);
    });

    test('numeric strings are parsed, and unparseable values stay null', () {
      final account = PortfolioAccountModel.accountFromJson(const {
        'accountMode': 'PAPER',
        'accountCategory': 'MAIN',
        'availability': 'AVAILABLE',
        'equity': '1234.56',
        'availableBalance': 'not-a-number',
      });

      expect(account.equity, 1234.56);
      expect(account.availableBalance, isNull);
    });
  });

  group('overview parsing', () {
    test('parses all four accounts and reports the mode', () {
      final overview = PortfolioAccountModel.overviewFromJson(const {
        'accountMode': 'PAPER',
        'categories': ['MAIN', 'SPOT', 'FUTURES', 'OPTIONS'],
        'accounts': [
          {'accountMode': 'PAPER', 'accountCategory': 'MAIN', 'availability': 'AVAILABLE', 'equity': 1000},
          {'accountMode': 'PAPER', 'accountCategory': 'SPOT', 'availability': 'AVAILABLE'},
          {'accountMode': 'PAPER', 'accountCategory': 'FUTURES', 'availability': 'AVAILABLE'},
          {'accountMode': 'PAPER', 'accountCategory': 'OPTIONS', 'availability': 'UNSUPPORTED'},
        ],
      });

      expect(overview.accountMode, PortfolioMode.paper);
      expect(overview.accounts, hasLength(4));
      expect(overview.accountFor(PortfolioCategory.main)!.equity, 1000);
      expect(
        overview.accountFor(PortfolioCategory.options)!.availability,
        PortfolioAvailability.unsupported,
      );
    });

    test('an empty or malformed accounts list yields an empty overview, not a crash', () {
      expect(
        PortfolioAccountModel.overviewFromJson(const {'accountMode': 'LIVE'}).accounts,
        isEmpty,
      );
      expect(
        PortfolioAccountModel.overviewFromJson(const {
          'accountMode': 'LIVE',
          'accounts': 'not-a-list',
        }).accounts,
        isEmpty,
      );
    });
  });

  group('position parsing', () {
    test('parses the full futures position contract', () {
      final positions = PortfolioAccountModel.positionsFromJson(const {
        'accountMode': 'LIVE',
        'accountCategory': 'FUTURES',
        'availability': 'AVAILABLE',
        'positions': [
          {
            'accountMode': 'LIVE',
            'accountCategory': 'FUTURES',
            'symbol': 'BTCUSDT',
            'side': 'LONG',
            'quantity': 1.5,
            'entryPrice': 60000,
            'currentPrice': 61000,
            'liquidationPrice': 45000,
            'leverage': 3,
            'notional': 91500,
            'unrealizedPnl': 1500,
            'status': 'OPEN',
          },
        ],
      });

      expect(positions.availability, PortfolioAvailability.available);
      expect(positions.positions, hasLength(1));
      final p = positions.positions.first;
      expect(p.symbol, 'BTCUSDT');
      expect(p.side, 'LONG');
      expect(p.quantity, 1.5);
      expect(p.entryPrice, 60000);
      expect(p.currentPrice, 61000);
      expect(p.liquidationPrice, 45000);
      expect(p.leverage, 3);
      expect(p.notional, 91500);
      expect(p.unrealizedPnl, 1500);
      expect(p.status, 'OPEN');
    });

    test('a position with no stop loss keeps it null instead of deriving one', () {
      final positions = PortfolioAccountModel.positionsFromJson(const {
        'accountMode': 'LIVE',
        'accountCategory': 'FUTURES',
        'availability': 'AVAILABLE',
        'positions': [
          {'symbol': 'BTCUSDT', 'side': 'LONG', 'entryPrice': 60000, 'currentPrice': 61000},
        ],
      });

      final p = positions.positions.first;
      expect(p.stopLoss, isNull);
      expect(p.takeProfit1, isNull);
      expect(p.takeProfit2, isNull);
      expect(p.takeProfit3, isNull);
      expect(p.liquidationPrice, isNull);
      expect(p.leverage, isNull);
    });

    test('a position with no originating signal has a null category, not a guess', () {
      final positions = PortfolioAccountModel.positionsFromJson(const {
        'accountMode': 'PAPER',
        'accountCategory': 'MAIN',
        'availability': 'AVAILABLE',
        'positions': [
          {'symbol': 'ETHUSDT', 'side': 'LONG'},
        ],
      });

      expect(positions.positions.first.accountCategory, isNull);
    });

    test('an empty list with an unsupported availability is distinguishable', () {
      final unsupported = PortfolioAccountModel.positionsFromJson(const {
        'accountMode': 'LIVE',
        'accountCategory': 'SPOT',
        'availability': 'UNSUPPORTED',
        'positions': <dynamic>[],
        'statusMessage': 'Spot exposes balances, not open positions.',
      });

      expect(unsupported.positions, isEmpty);
      expect(unsupported.isEmptyBecauseUnsupported, isTrue);
      expect(unsupported.availability, PortfolioAvailability.unsupported);
    });

    test('a supported account holding nothing is distinguishable from unsupported', () {
      final empty = PortfolioAccountModel.positionsFromJson(const {
        'accountMode': 'PAPER',
        'accountCategory': 'SPOT',
        'availability': 'AVAILABLE',
        'positions': <dynamic>[],
      });

      expect(empty.positions, isEmpty);
      expect(empty.isEmptyBecauseUnsupported, isFalse);
    });
  });

  group('security', () {
    test('no credential, listen key or signing field is ever read from a payload', () {
      final account = PortfolioAccountModel.accountFromJson(const {
        'accountMode': 'LIVE',
        'accountCategory': 'SPOT',
        'availability': 'AVAILABLE',
        // A hostile or over-broad payload must not become part of the entity.
        'apiKey': 'leaked',
        'apiSecret': 'leaked',
        'listenKey': 'leaked',
        'credentialId': 'leaked',
        'signature': 'leaked',
      });

      expect(account.equity, isNull);
      // The entity simply has no field that could carry any of those values.
      expect(
        account.toString(),
        isNot(contains('leaked')),
      );
    });
  });
}
