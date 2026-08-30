import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../domain/entities/kline_candle.dart';
import 'coin_letter_avatar.dart';

class CoinKlineChart extends StatelessWidget {
  const CoinKlineChart({super.key, required this.candles});

  final List<KlineCandle> candles;

  @override
  Widget build(BuildContext context) {
    if (candles.isEmpty) {
      return const SizedBox(
        height: 280,
        child: Center(
          child: Text('No chart data', style: TextStyle(color: AppColors.muted)),
        ),
      );
    }

    final spots = <CandlestickSpot>[
      for (var i = 0; i < candles.length; i++)
        CandlestickSpot(
          x: i.toDouble(),
          open: candles[i].open,
          high: candles[i].high,
          low: candles[i].low,
          close: candles[i].close,
        ),
    ];

    final painter = DefaultCandlestickPainter(
      candlestickStyleProvider: (spot, _) {
        final color = spot.isUp ? AppColors.accent : AppColors.loss;
        return CandlestickStyle(
          lineColor: color,
          lineWidth: 1.2,
          bodyStrokeColor: color,
          bodyStrokeWidth: 0,
          bodyFillColor: color,
          bodyWidth: 5,
          bodyRadius: 1,
        );
      },
    );

    return Column(
      children: [
        SizedBox(
          height: 220,
          child: CandlestickChart(
            CandlestickChartData(
              candlestickSpots: spots,
              candlestickPainter: painter,
              backgroundColor: AppColors.card,
              gridData: FlGridData(
                show: true,
                drawVerticalLine: false,
                horizontalInterval: _interval(candles.map((c) => c.high).reduce((a, b) => a > b ? a : b), candles.map((c) => c.low).reduce((a, b) => a < b ? a : b)),
                getDrawingHorizontalLine: (_) => FlLine(
                  color: AppColors.muted.withValues(alpha: 0.18),
                  strokeWidth: 0.6,
                ),
              ),
              borderData: FlBorderData(show: false),
              titlesData: FlTitlesData(
                show: true,
                topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                bottomTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                leftTitles: AxisTitles(
                  sideTitles: SideTitles(
                    showTitles: true,
                    reservedSize: 52,
                    getTitlesWidget: (value, meta) => Text(
                      MarketFormat.compact(value),
                      style: const TextStyle(color: AppColors.muted, fontSize: 10),
                    ),
                  ),
                ),
              ),
              candlestickTouchData: CandlestickTouchData(enabled: true),
            ),
          ),
        ),
        const SizedBox(height: 8),
        SizedBox(
          height: 72,
          child: BarChart(
            BarChartData(
              alignment: BarChartAlignment.spaceAround,
              gridData: const FlGridData(show: false),
              borderData: FlBorderData(show: false),
              titlesData: const FlTitlesData(show: false),
              barGroups: [
                for (var i = 0; i < candles.length; i++)
                  BarChartGroupData(
                    x: i,
                    barRods: [
                      BarChartRodData(
                        toY: candles[i].volume,
                        width: 2.4,
                        color: candles[i].isUp
                            ? AppColors.accent.withValues(alpha: 0.7)
                            : AppColors.loss.withValues(alpha: 0.7),
                        borderRadius: BorderRadius.circular(1),
                      ),
                    ],
                  ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  static double _interval(double max, double min) {
    final span = (max - min).abs();
    if (span == 0) {
      return 1;
    }
    return span / 4;
  }
}
