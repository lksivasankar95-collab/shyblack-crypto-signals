import 'package:cryptosignals/data/models/portfolio_account_model.dart';
import 'package:cryptosignals/domain/entities/portfolio_account.dart';
import 'package:flutter_test/flutter_test.dart';

/// Phase 7 parsing rules.
///
/// The rules under test are the ones that decide whether the UI can lie: a
/// missing number must not become zero, an omitted value must stay absent, and a
/// truncated window must stay marked partial.
void main() {
  group('holdings parsing', () {
    test('reads every asset with free, locked and total', () {
      final holdings = PortfolioAccountModel.holdingsFromJson({
        'accountMode': 'LIVE',
        'accountCategory': 'SPOT',
        'availability': 'AVAILABLE',
        'source': 'EXCHANGE',
        'holdings': [
          {'asset': 'BTC', 'free': 0.1, 'locked': 0.02, 'total': 0.12},
          {'asset': 'USDT', 'free': 100.5, 'locked': 20.25, 'total': 120.75},
        ],
      });

      expect(holdings.holdings, hasLength(2));
      expect(holdings.holdings.first.asset, 'BTC');
      expect(holdings.holdings.first.free, 0.1);
      expect(holdings.holdings.first.total, 0.12);
      expect(holdings.source, 'EXCHANGE');
      expect(holdings.isEmptyBecauseUnsupported, isFalse);
    });

    test('an omitted balance stays null rather than becoming zero', () {
      final holdings = PortfolioAccountModel.holdingsFromJson({
        'accountMode': 'LIVE',
        'accountCategory': 'SPOT',
        'availability': 'AVAILABLE',
        'holdings': [
          {'asset': 'BTC', 'free': null, 'locked': null, 'total': null},
        ],
      });

      expect(holdings.holdings.single.free, isNull);
      expect(holdings.holdings.single.total, isNull);
    });

    test('an unsupported scope is not an empty wallet', () {
      final holdings = PortfolioAccountModel.holdingsFromJson({
        'accountMode': 'PAPER',
        'accountCategory': 'SPOT',
        'availability': 'UNSUPPORTED',
        'source': 'LOCAL_PAPER',
        'holdings': <dynamic>[],
        'statusMessage': 'A simulated account holds capital, not assets.',
      });

      expect(holdings.holdings, isEmpty);
      expect(holdings.availability.isUnsupported, isTrue);
      expect(
        holdings.isEmptyBecauseUnsupported,
        isTrue,
        reason: 'an unsupported capability must not read as an empty wallet',
      );
    });

    test('a genuinely empty but supported wallet is not flagged unsupported', () {
      final holdings = PortfolioAccountModel.holdingsFromJson({
        'accountMode': 'LIVE',
        'accountCategory': 'SPOT',
        'availability': 'AVAILABLE',
        'holdings': <dynamic>[],
      });

      expect(holdings.isEmptyBecauseUnsupported, isFalse);
    });
  });

  group('history parsing', () {
    test('reads an order record and its exchange identity', () {
      final history = PortfolioAccountModel.historyFromJson({
        'accountMode': 'LIVE',
        'accountCategory': 'SPOT',
        'availability': 'AVAILABLE',
        'source': 'EXCHANGE',
        'entryType': 'ORDER',
        'windowFrom': '2026-01-01T00:00:00Z',
        'windowTo': '2026-01-08T00:00:00Z',
        'complete': true,
        'entries': [
          {
            'entryType': 'ORDER',
            'accountMode': 'LIVE',
            'accountCategory': 'SPOT',
            'symbol': 'BTCUSDT',
            'orderId': 42,
            'side': 'BUY',
            'status': 'FILLED',
            'price': 100.5,
            'quantity': 2.0,
            'occurredAt': '2026-01-02T03:04:05Z',
          },
        ],
      });

      expect(history.entries, hasLength(1));
      expect(history.entries.single.orderId, 42);
      expect(history.entries.single.side, 'BUY');
      expect(history.entries.single.status, 'FILLED');
      expect(history.windowFrom, isNotNull);
      expect(history.windowTo, isNotNull);
      expect(history.isPartial, isFalse);
    });

    test('a truncated window stays marked partial', () {
      final history = PortfolioAccountModel.historyFromJson({
        'accountMode': 'LIVE',
        'accountCategory': 'FUTURES',
        'availability': 'AVAILABLE',
        'entryType': 'TRADE',
        'complete': false,
        'statusMessage': 'Reached the record cap; this window is partial.',
        'entries': <dynamic>[],
      });

      expect(history.isPartial, isTrue);
      expect(history.statusMessage, contains('partial'));
    });

    test('an absent complete flag means the window was complete', () {
      final history = PortfolioAccountModel.historyFromJson({
        'accountMode': 'LIVE',
        'accountCategory': 'SPOT',
        'availability': 'AVAILABLE',
        'entries': <dynamic>[],
      });

      expect(
        history.isPartial,
        isFalse,
        reason: 'only an explicit false marks a window partial',
      );
    });

    test('a fill with no exchange P&L keeps realizedPnl null', () {
      final history = PortfolioAccountModel.historyFromJson({
        'accountMode': 'LIVE',
        'accountCategory': 'SPOT',
        'availability': 'AVAILABLE',
        'entryType': 'TRADE',
        'complete': true,
        'entries': [
          {
            'entryType': 'TRADE',
            'accountMode': 'LIVE',
            'accountCategory': 'SPOT',
            'symbol': 'BTCUSDT',
            'tradeId': 55,
            'orderId': 9,
            'price': 100.0,
            'quantity': 1.0,
            'fee': 0.1,
            'feeAsset': 'BNB',
          },
        ],
      });

      final entry = history.entries.single;
      expect(entry.tradeId, 55);
      expect(entry.fee, 0.1);
      expect(entry.feeAsset, 'BNB');
      expect(
        entry.realizedPnl,
        isNull,
        reason: 'spot fills report no realized P&L and it must not be derived',
      );
      expect(entry.quoteQuantity, isNull);
    });

    test('an income record carries the exchange reported realized P&L', () {
      final history = PortfolioAccountModel.historyFromJson({
        'accountMode': 'LIVE',
        'accountCategory': 'FUTURES',
        'availability': 'AVAILABLE',
        'entryType': 'INCOME',
        'complete': true,
        'entries': [
          {
            'entryType': 'INCOME',
            'accountMode': 'LIVE',
            'accountCategory': 'FUTURES',
            'symbol': 'BTCUSDT',
            'tradeId': 900,
            'status': 'REALIZED_PNL',
            'realizedPnl': 55.25,
            'feeAsset': 'USDT',
          },
        ],
      });

      expect(history.entries.single.status, 'REALIZED_PNL');
      expect(history.entries.single.realizedPnl, 55.25);
    });

    test('a non numeric numeric field stays null rather than becoming zero', () {
      final history = PortfolioAccountModel.historyFromJson({
        'accountMode': 'LIVE',
        'accountCategory': 'SPOT',
        'availability': 'AVAILABLE',
        'entries': [
          {
            'entryType': 'ORDER',
            'price': 'not-a-number',
            'quantity': null,
            'orderId': '7',
          },
        ],
      });

      expect(history.entries.single.price, isNull);
      expect(history.entries.single.quantity, isNull);
      expect(history.entries.single.orderId, 7);
    });

    test('an unavailable scope is not an account with no activity', () {
      final history = PortfolioAccountModel.historyFromJson({
        'accountMode': 'LIVE',
        'accountCategory': 'SPOT',
        'availability': 'NOT_CONNECTED',
        'entries': <dynamic>[],
        'statusMessage': 'No exchange credential is connected.',
      });

      expect(history.isEmptyBecauseUnsupported, isTrue);
      expect(history.statusMessage, isNotNull);
    });
  });

  group('sync status parsing', () {
    test('reads freshness and the stale flag', () {
      final status = PortfolioAccountModel.syncStatusFromJson({
        'accountMode': 'LIVE',
        'accountCategory': 'SPOT',
        'connectionStatus': 'CONNECTED',
        'availability': 'STALE',
        'lastRestSync': '2026-01-02T03:04:05Z',
        'lastEvent': null,
        'stale': true,
        'message': 'Data is past the freshness window.',
      });

      expect(status.connectionStatus, 'CONNECTED');
      expect(status.lastRestSync, isNotNull);
      expect(status.lastEvent, isNull);
      expect(status.isStale, isTrue);
      expect(status.availability.isStale, isTrue);
    });

    test('a fresh scope is not stale', () {
      final status = PortfolioAccountModel.syncStatusFromJson({
        'accountMode': 'LIVE',
        'accountCategory': 'SPOT',
        'availability': 'AVAILABLE',
        'lastRestSync': '2026-01-02T03:04:05Z',
        'stale': false,
      });

      expect(status.isStale, isFalse);
    });

    test('an absent stale flag means not stale', () {
      final status = PortfolioAccountModel.syncStatusFromJson({
        'accountMode': 'PAPER',
        'accountCategory': 'MAIN',
        'availability': 'UNSUPPORTED',
      });

      expect(status.isStale, isFalse);
      expect(status.connectionStatus, isNull);
      expect(status.message, isNull);
    });
  });

  group('history type parsing', () {
    test('parses the three record types', () {
      expect(PortfolioHistoryType.parse('ORDER'), PortfolioHistoryType.order);
      expect(PortfolioHistoryType.parse('TRADE'), PortfolioHistoryType.trade);
      expect(PortfolioHistoryType.parse('INCOME'), PortfolioHistoryType.income);
    });

    test('an unknown type falls back to orders rather than throwing', () {
      expect(PortfolioHistoryType.parse('NONSENSE'), PortfolioHistoryType.order);
      expect(PortfolioHistoryType.parse(null), PortfolioHistoryType.order);
    });
  });
}