import 'package:cryptosignals/data/models/trading_strategy_model.dart';
import 'package:cryptosignals/domain/entities/trading_strategy.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('parses emaTrendFollowing config from the backend shape', () {
    final strategy = TradingStrategyModel.fromJson(<String, dynamic>{
      'id': 'etf-1',
      'name': 'EMA Trend Following',
      'tradingMode': 'SPOT',
      'strategyType': 'SYSTEM',
      'version': 1,
      'status': 'ACTIVE',
      'deletable': false,
      'editable': false,
      'engineKey': 'EMA_TREND_FOLLOWING',
      'config': {
        'emaTrendFollowing': {
          'htfFastEma': 34,
          'htfSlowEma': 144,
          'rsiFilterEnabled': false,
          'minimumScore': 75,
          'weightTrendAlignment': 40,
        },
      },
    });

    expect(strategy.engineKey, 'EMA_TREND_FOLLOWING');
    final c = strategy.config?.emaTrendFollowing;
    expect(c, isNotNull);
    expect(c!.htfFastEma, 34);
    expect(c.htfSlowEma, 144);
    expect(c.rsiFilterEnabled, isFalse);
    expect(c.minimumScore, 75);
    expect(c.weightTrendAlignment, 40);
  });

  test('defaults match the backend EMATrendFollowingConfig', () {
    const c = EmaTrendFollowingConfig();
    expect(c.htfTimeframe, '1H');
    expect(c.entryTimeframe, '15M');
    expect(c.htfFastEma, 50);
    expect(c.htfSlowEma, 200);
    expect(c.entryFastEma, 20);
    expect(c.entrySlowEma, 50);
    expect(c.minimumEmaSeparationPct, 0.10);
    expect(c.minimumRsiForLong, 50);
    expect(c.maximumRsiForLong, 70);
    expect(c.minimumVolumeRatio, 1.20);
    expect(c.minimumAtrPct, 0.30);
    expect(c.minimumScore, 70);
    expect(c.minRR, 1.5);
    expect(c.cooldownCandles, 4);
  });

  test('serializes engine fields for the PATCH/create body', () {
    const c = EmaTrendFollowingConfig(htfFastEma: 34, minimumScore: 75);
    final json = c.toJson();
    expect(json['htfFastEma'], 34);
    expect(json['minimumScore'], 75);
    expect(json['weightTrendAlignment'], 30);
    expect(json['weightVolatility'], 5);
    expect(json['rsiFilterEnabled'], isTrue);
    expect(json['volumeFilterEnabled'], isTrue);
    expect(json['atrFilterEnabled'], isTrue);
  });

  test('absent engine config leaves emaTrendFollowing null', () {
    final strategy = TradingStrategyModel.fromJson(<String, dynamic>{
      'id': 'x',
      'name': 'EMA + RSI',
      'tradingMode': 'SPOT',
      'strategyType': 'SYSTEM',
      'version': 1,
      'status': 'ACTIVE',
      'deletable': false,
      'editable': false,
      'config': {
        'scoring': {'minRiskReward': 1.5},
      },
    });
    expect(strategy.config?.emaTrendFollowing, isNull);
  });
}
