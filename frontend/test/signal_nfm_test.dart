import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:cryptosignals/core/theme/app_theme.dart';
import 'package:cryptosignals/data/models/signal_model.dart';
import 'package:cryptosignals/domain/entities/signal.dart';
import 'package:cryptosignals/presentation/screens/signals/signal_details_screen.dart';

Map<String, dynamic> _jsonWithNfm() => {
  'id': 'sig-1',
  'symbol': 'BTCUSDT',
  'status': 'ACTIVE',
  'side': 'LONG',
  'entryPrice': 100.0,
  'targetPrice': 105.0,
  'stopLoss': 98.0,
  'createdAt': '2026-10-01T10:00:00Z',
  'score': 81,
  'signalGrade': 'BUY',
  'tradingMode': 'FUTURES',
  'nfmContext': {
    'newsEventId': 'evt-1',
    'eventType': 'BTC_ETF',
    'eventCategory': 'CRYPTO_STRUCTURAL',
    'eventStage': 'APPROVAL',
    'sourceTier': 'TIER_1',
    'source': 'reuters',
    'eventTime': '2026-10-01T09:55:00Z',
    'priceReactionPct': 4.5,
    'volumeMultiplier': 2.5,
    'openInterestChangePct': null,
    'fundingState': 'UNKNOWN',
    'liquidationState': 'UNKNOWN',
    'eventConfluenceScore': 81,
    'marketInterpretation': 'POSITIVE',
    'configVersion': 'NFM_FUTURES_V1',
  },
};

void main() {
  test('signal model parses NFM context and trading mode', () {
    final signal = SignalModel.fromJson(_jsonWithNfm()).toEntity();

    expect(signal.tradingMode, 'FUTURES');
    expect(signal.nfmContext, isNotNull);
    expect(signal.nfmContext!.eventType, 'BTC_ETF');
    expect(signal.nfmContext!.sourceTier, 'TIER_1');
    expect(signal.nfmContext!.priceReactionPct, 4.5);
    expect(signal.nfmContext!.volumeMultiplier, 2.5);
    expect(signal.nfmContext!.openInterestChangePct, isNull);
    expect(signal.nfmContext!.fundingState, 'UNKNOWN');
    expect(signal.nfmContext!.configVersion, 'NFM_FUTURES_V1');
  });

  test('signal model without nfmContext leaves it null', () {
    final json = _jsonWithNfm()..remove('nfmContext');
    final signal = SignalModel.fromJson(json).toEntity();
    expect(signal.nfmContext, isNull);
  });

  testWidgets('signal details renders the NFM event card for NFM signals', (
    WidgetTester tester,
  ) async {
    final signal = SignalModel.fromJson(_jsonWithNfm()).toEntity();

    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.dark(),
        home: SignalDetailsScreen(signal: signal),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('News Event (NFM)'), findsOneWidget);
    expect(find.text('Event Type'), findsOneWidget);
    expect(find.text('BTC_ETF'), findsOneWidget);
    expect(find.text('Funding'), findsOneWidget);
    expect(find.text('UNKNOWN'), findsWidgets);
    expect(find.text('FUTURES'), findsOneWidget);
  });

  testWidgets('signal details hides the NFM card for non-NFM signals', (
    WidgetTester tester,
  ) async {
    final signal = Signal(
      id: 's',
      symbol: 'ETHUSDT',
      status: SignalStatus.active,
      side: PositionSide.long,
      createdAt: DateTime(2026, 1, 1),
    );

    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.dark(),
        home: SignalDetailsScreen(signal: signal),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('News Event (NFM)'), findsNothing);
  });
}
