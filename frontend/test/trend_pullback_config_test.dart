import 'package:cryptosignals/data/models/trading_strategy_model.dart';
import 'package:cryptosignals/domain/entities/trading_strategy.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('engineKey is parsed and PullbackConfig serializes back', () {
    final strategy = TradingStrategyModel.fromJson(<String, dynamic>{
      'id': 'tp',
      'name': 'Trend Pullback',
      'tradingMode': 'SPOT',
      'strategyType': 'USER',
      'version': 1,
      'status': 'ACTIVE',
      'deletable': true,
      'editable': true,
      'engineKey': 'TREND_PULLBACK',
      'config': {
        'pullback': {'minRR': 2.0, 'htf': '4H'},
      },
    });

    expect(strategy.engineKey, 'TREND_PULLBACK');
    expect(strategy.config?.pullback?.minRR, 2.0);
    expect(strategy.config?.pullback?.htf, '4H');

    final json = const PullbackConfig(minRR: 2.5).toJson();
    expect(json['minRR'], 2.5);
    expect(json['htf'], '1H');
    expect(json['zoneMode'], 'EMA20_TO_EMA50');
  });

  test('trading strategy parses a trend-pullback config', () {
    final json = <String, dynamic>{
      'id': 'abc',
      'name': 'Trend Pullback',
      'description': 'HTF trend + pullback',
      'tradingMode': 'SPOT',
      'strategyType': 'SYSTEM',
      'version': 1,
      'status': 'ACTIVE',
      'deletable': false,
      'editable': false,
      'config': {
        'pullback': {
          'htf': '1H',
          'entryTimeframe': '15M',
          'emaFastHtf': 50,
          'emaSlowHtf': 200,
          'minAdx': 20,
          'pullbackEma': 20,
          'entryEma': 50,
          'zoneMode': 'EMA20_TO_EMA50',
          'rsiMin': 40,
          'rsiMax': 60,
          'minRR': 1.5,
          'tp1R': 1.0,
          'tp2R': 2.0,
          'tp3R': 3.0,
          'minimumScore': 70,
        },
      },
    };

    final strategy = TradingStrategyModel.fromJson(json);
    final pb = strategy.config?.pullback;

    expect(pb, isNotNull);
    expect(pb!.htf, '1H');
    expect(pb.entryTimeframe, '15M');
    expect(pb.zoneMode, 'EMA20_TO_EMA50');
    expect(pb.minRR, 1.5);
    expect(pb.minimumScore, 70);
  });

  test('absent pullback config does not break parsing', () {
    final json = <String, dynamic>{
      'id': 'abc',
      'name': 'EMA + RSI',
      'tradingMode': 'SPOT',
      'strategyType': 'SYSTEM',
      'version': 1,
      'status': 'ACTIVE',
      'deletable': false,
      'editable': false,
      'config': {'scoring': {'minRiskReward': 1.5}},
    };

    final strategy = TradingStrategyModel.fromJson(json);
    expect(strategy.config?.pullback, isNull);
    expect(strategy.name, 'EMA + RSI');
  });
}
