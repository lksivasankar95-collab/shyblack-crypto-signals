import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../domain/entities/backtest_equity_point.dart';
import '../../../domain/entities/backtest_run.dart';
import '../../../domain/entities/backtest_trade.dart';
import '../../../domain/repositories/backtesting_repository.dart';
import '../../providers/backtesting_controller.dart';

/// End-to-end backtesting screen: configuration form → run list → detail
/// (summary + equity curve + trades). Values are rendered directly from
/// backend responses — no frontend metric recalculation.
class BacktestingScreen extends ConsumerWidget {
  const BacktestingScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncView = ref.watch(backtestingControllerProvider);
    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: asyncView.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, _) => Center(
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              const Text('Could not load backtests',
                  style: TextStyle(color: AppColors.muted)),
              TextButton(
                  onPressed: () => ref.invalidate(backtestingControllerProvider),
                  child: const Text('Retry')),
            ]),
          ),
          data: (view) => RefreshIndicator(
            onRefresh: () => ref
                .read(backtestingControllerProvider.notifier).refresh(silent: false),
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                const Text('Backtesting',
                    style: TextStyle(
                        color: AppColors.onBackground,
                        fontSize: 22, fontWeight: FontWeight.w800)),
                const SizedBox(height: 4),
                const Text(
                  'Simulate a strategy against historical data. No look-ahead — '
                  'entries fill at the next candle open, and SL is assumed first '
                  'when a single candle touches both SL and TP.',
                  style: TextStyle(color: AppColors.muted, height: 1.35),
                ),
                const SizedBox(height: 12),
                _ConfigForm(strategies: view.strategies),
                const SizedBox(height: 16),
                const Text('Runs',
                    style: TextStyle(
                        color: AppColors.muted, fontSize: 12,
                        fontWeight: FontWeight.w800, letterSpacing: 1.1)),
                const SizedBox(height: 6),
                if (view.runs.isEmpty)
                  const _Empty('No runs yet.')
                else
                  ...view.runs.map((r) => _RunCard(run: r)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ConfigForm extends ConsumerStatefulWidget {
  const _ConfigForm({required this.strategies});
  final List<StrategyDescriptor> strategies;

  @override
  ConsumerState<_ConfigForm> createState() => _ConfigFormState();
}

class _ConfigFormState extends ConsumerState<_ConfigForm> {
  final _symbolCtrl = TextEditingController(text: 'BTCUSDT');
  final _timeframeCtrl = TextEditingController(text: '1h');
  final _capitalCtrl = TextEditingController(text: '10000');
  final _riskCtrl = TextEditingController(text: '1.0');
  final _feeCtrl = TextEditingController(text: '0.10');
  final _slipCtrl = TextEditingController(text: '0.05');
  final _leverageCtrl = TextEditingController(text: '1');
  BacktestTradingMode _mode = BacktestTradingMode.spot;
  DateTime _start = DateTime.now().subtract(const Duration(days: 30));
  DateTime _end = DateTime.now();
  bool _submitting = false;

  @override
  void dispose() {
    _symbolCtrl.dispose();
    _timeframeCtrl.dispose();
    _capitalCtrl.dispose();
    _riskCtrl.dispose();
    _feeCtrl.dispose();
    _slipCtrl.dispose();
    _leverageCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final strategyId = widget.strategies.isNotEmpty
        ? widget.strategies.first.id : 'ema-rsi';
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
          color: AppColors.card, borderRadius: BorderRadius.circular(12)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Text('New backtest',
              style: TextStyle(color: AppColors.onCard,
                  fontWeight: FontWeight.w800, fontSize: 14)),
          const SizedBox(height: 10),
          Row(children: [
            Expanded(child: _text(_symbolCtrl, 'Symbol')),
            const SizedBox(width: 8),
            Expanded(child: _text(_timeframeCtrl, 'Timeframe')),
          ]),
          const SizedBox(height: 8),
          Row(children: [
            Expanded(child: _text(_capitalCtrl, 'Initial capital')),
            const SizedBox(width: 8),
            Expanded(child: _text(_riskCtrl, 'Risk %/trade')),
          ]),
          const SizedBox(height: 8),
          Row(children: [
            Expanded(child: _text(_feeCtrl, 'Fee %')),
            const SizedBox(width: 8),
            Expanded(child: _text(_slipCtrl, 'Slippage %')),
            const SizedBox(width: 8),
            Expanded(child: _text(_leverageCtrl, 'Leverage')),
          ]),
          const SizedBox(height: 8),
          Row(children: [
            Expanded(child: _modeButton(BacktestTradingMode.spot, 'SPOT')),
            const SizedBox(width: 8),
            Expanded(child: _modeButton(BacktestTradingMode.futures, 'FUTURES')),
          ]),
          const SizedBox(height: 10),
          FilledButton(
            onPressed: _submitting ? null : () => _submit(strategyId),
            child: _submitting
                ? const SizedBox(width: 18, height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2))
                : const Text('RUN BACKTEST'),
          ),
        ],
      ),
    );
  }

  Widget _modeButton(BacktestTradingMode m, String label) {
    final selected = _mode == m;
    return OutlinedButton(
      onPressed: () => setState(() => _mode = m),
      style: OutlinedButton.styleFrom(
        foregroundColor: selected ? AppColors.accent : AppColors.muted,
        side: BorderSide(color: selected ? AppColors.accent : AppColors.muted),
        backgroundColor: selected
            ? AppColors.accent.withValues(alpha: 0.15) : Colors.transparent,
      ),
      child: Text(label, style: const TextStyle(fontWeight: FontWeight.w700)),
    );
  }

  Widget _text(TextEditingController ctrl, String label) {
    return TextField(
      controller: ctrl,
      decoration: InputDecoration(
        labelText: label,
        labelStyle: const TextStyle(color: AppColors.muted, fontSize: 12),
        filled: true,
        fillColor: AppColors.background,
        contentPadding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
      ),
      style: const TextStyle(color: AppColors.onCard, fontSize: 13),
    );
  }

  Future<void> _submit(String strategyId) async {
    setState(() => _submitting = true);
    try {
      final config = BacktestConfigInput(
        strategyId: strategyId,
        symbol: _symbolCtrl.text.trim().toUpperCase(),
        timeframe: _timeframeCtrl.text.trim(),
        tradingMode: _mode,
        startDate: _start,
        endDate: _end,
        initialCapital: double.tryParse(_capitalCtrl.text.trim()) ?? 10000,
        riskPerTradePct: double.tryParse(_riskCtrl.text.trim()) ?? 1.0,
        feePct: double.tryParse(_feeCtrl.text.trim()) ?? 0.10,
        slippagePct: double.tryParse(_slipCtrl.text.trim()) ?? 0.05,
        leverage: int.tryParse(_leverageCtrl.text.trim()) ?? 1,
      );
      await ref.read(backtestingControllerProvider.notifier).startRun(config);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Backtest queued')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed: $e'), backgroundColor: AppColors.loss),
      );
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }
}

class _RunCard extends ConsumerWidget {
  const _RunCard({required this.run});
  final BacktestRun run;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final statusColor = switch (run.status) {
      BacktestStatus.completed => AppColors.profit,
      BacktestStatus.failed || BacktestStatus.cancelled => AppColors.loss,
      _ => AppColors.accent,
    };
    final pnl = run.totalNetPnl ?? 0;
    final pnlColor = pnl >= 0 ? AppColors.profit : AppColors.loss;
    return InkWell(
      onTap: () => Navigator.of(context).push(MaterialPageRoute<void>(
          builder: (_) => BacktestDetailScreen(runId: run.id))),
      borderRadius: BorderRadius.circular(12),
      child: Container(
        margin: const EdgeInsets.only(bottom: 10),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
            color: AppColors.card, borderRadius: BorderRadius.circular(12)),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(children: [
              Expanded(
                child: Text(
                  '${run.symbol} • ${run.timeframe} • '
                  '${run.tradingMode == BacktestTradingMode.futures ? "FUT" : "SPOT"}',
                  style: const TextStyle(
                      color: AppColors.onBackground,
                      fontWeight: FontWeight.w800, fontSize: 14),
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: statusColor.withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(run.status.name.toUpperCase(),
                    style: TextStyle(color: statusColor, fontSize: 10,
                        fontWeight: FontWeight.w800)),
              ),
            ]),
            const SizedBox(height: 4),
            Text('${run.strategyId} @ ${run.strategyVersion}',
                style: const TextStyle(color: AppColors.muted, fontSize: 11)),
            if (!run.isTerminal) Padding(
              padding: const EdgeInsets.only(top: 6),
              child: LinearProgressIndicator(value: run.progressPct),
            ),
            if (run.status == BacktestStatus.completed) Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Row(children: [
                _kv('Net P&L', pnl.toStringAsFixed(2), pnlColor),
                _kv('Return', '${(run.totalReturnPct ?? 0).toStringAsFixed(2)}%'),
                _kv('Trades', '${run.totalTrades}'),
                _kv('Win %', '${(run.winRatePct ?? 0).toStringAsFixed(1)}'),
                _kv('MaxDD', '${(run.maxDrawdownPct ?? 0).toStringAsFixed(2)}%'),
              ]),
            ),
            if (run.failureReason != null)
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text(run.failureReason!,
                    style: const TextStyle(color: AppColors.loss, fontSize: 11)),
              ),
          ],
        ),
      ),
    );
  }

  Widget _kv(String k, String v, [Color? c]) {
    return Expanded(
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(k, style: const TextStyle(color: AppColors.muted, fontSize: 10)),
        Text(v, style: TextStyle(
            color: c ?? AppColors.onCard,
            fontWeight: FontWeight.w700, fontSize: 12)),
      ]),
    );
  }
}

class _Empty extends StatelessWidget {
  const _Empty(this.text);
  final String text;
  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(14),
        alignment: Alignment.center,
        decoration: BoxDecoration(
            color: AppColors.card, borderRadius: BorderRadius.circular(12)),
        child: Text(text, style: const TextStyle(color: AppColors.muted)),
      );
}

/// Detail view — equity curve + trades + summary metrics for one run.
class BacktestDetailScreen extends ConsumerWidget {
  const BacktestDetailScreen({super.key, required this.runId});
  final String runId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncDetail = ref.watch(backtestRunDetailProvider(runId));
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        title: const Text('Backtest'),
        centerTitle: false,
      ),
      body: asyncDetail.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(child: Text('Error: $error',
            style: const TextStyle(color: AppColors.muted))),
        data: (detail) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            _SummaryCard(run: detail.run),
            const SizedBox(height: 12),
            const Text('EQUITY CURVE',
                style: TextStyle(color: AppColors.muted, fontSize: 12,
                    fontWeight: FontWeight.w800, letterSpacing: 1.1)),
            const SizedBox(height: 6),
            _EquityChart(points: detail.equity),
            const SizedBox(height: 12),
            const Text('TRADES',
                style: TextStyle(color: AppColors.muted, fontSize: 12,
                    fontWeight: FontWeight.w800, letterSpacing: 1.1)),
            const SizedBox(height: 6),
            if (detail.trades.isEmpty)
              const _Empty('No trades')
            else
              ...detail.trades.map(_tradeRow),
          ],
        ),
      ),
    );
  }

  Widget _tradeRow(BacktestTrade t) {
    final pnlColor = t.netPnl >= 0 ? AppColors.profit : AppColors.loss;
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
          color: AppColors.card, borderRadius: BorderRadius.circular(10)),
      child: Row(children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
          decoration: BoxDecoration(
            color: AppColors.accent.withValues(alpha: 0.15),
            borderRadius: BorderRadius.circular(6),
          ),
          child: Text(t.side == BacktestSide.long ? 'LONG' : 'SHORT',
              style: const TextStyle(color: AppColors.accent, fontSize: 10,
                  fontWeight: FontWeight.w800)),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(t.symbol, style: const TextStyle(
                  color: AppColors.onCard, fontWeight: FontWeight.w700, fontSize: 13)),
              Text('${t.entryPrice.toStringAsFixed(2)} → ${t.exitPrice.toStringAsFixed(2)} '
                  '· ${t.exitReason.name}',
                  style: const TextStyle(color: AppColors.muted, fontSize: 10)),
            ],
          ),
        ),
        Text('${t.netPnl >= 0 ? "+" : ""}${t.netPnl.toStringAsFixed(2)}',
            style: TextStyle(color: pnlColor, fontWeight: FontWeight.w800)),
      ]),
    );
  }
}

class _SummaryCard extends StatelessWidget {
  const _SummaryCard({required this.run});
  final BacktestRun run;

  @override
  Widget build(BuildContext context) {
    final pnl = run.totalNetPnl ?? 0;
    final pnlColor = pnl >= 0 ? AppColors.profit : AppColors.loss;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
          color: AppColors.card, borderRadius: BorderRadius.circular(12)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('${run.symbol} • ${run.timeframe} • ${run.tradingMode.name.toUpperCase()}',
              style: const TextStyle(color: AppColors.onBackground,
                  fontSize: 16, fontWeight: FontWeight.w800)),
          Text('${run.strategyId} @ ${run.strategyVersion}',
              style: const TextStyle(color: AppColors.muted, fontSize: 11)),
          const SizedBox(height: 10),
          Row(children: [
            _kv('Net P&L', pnl.toStringAsFixed(2), pnlColor),
            _kv('Return', '${(run.totalReturnPct ?? 0).toStringAsFixed(2)}%'),
            _kv('Trades', '${run.totalTrades}'),
          ]),
          const SizedBox(height: 6),
          Row(children: [
            _kv('Win %', '${(run.winRatePct ?? 0).toStringAsFixed(1)}'),
            _kv('Profit factor', (run.profitFactor ?? 0).toStringAsFixed(2)),
            _kv('Sharpe', (run.sharpeRatio ?? 0).toStringAsFixed(2)),
          ]),
          const SizedBox(height: 6),
          Row(children: [
            _kv('MaxDD', '${(run.maxDrawdownPct ?? 0).toStringAsFixed(2)}%'),
            _kv('Fees', (run.totalFees ?? 0).toStringAsFixed(2)),
            _kv('Expectancy', (run.expectancy ?? 0).toStringAsFixed(3)),
          ]),
        ],
      ),
    );
  }

  Widget _kv(String k, String v, [Color? c]) => Expanded(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(k, style: const TextStyle(color: AppColors.muted, fontSize: 10)),
          Text(v, style: TextStyle(color: c ?? AppColors.onCard,
              fontWeight: FontWeight.w700, fontSize: 13)),
        ]),
      );
}

class _EquityChart extends StatelessWidget {
  const _EquityChart({required this.points});
  final List<BacktestEquityPoint> points;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 200,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
          color: AppColors.card, borderRadius: BorderRadius.circular(12)),
      child: points.isEmpty
          ? const Center(child: Text('No equity data',
              style: TextStyle(color: AppColors.muted)))
          : CustomPaint(painter: _EquityPainter(points), size: Size.infinite),
    );
  }
}

class _EquityPainter extends CustomPainter {
  _EquityPainter(this.points);
  final List<BacktestEquityPoint> points;

  @override
  void paint(Canvas canvas, Size size) {
    if (points.isEmpty) return;
    double minY = points.first.equity;
    double maxY = minY;
    for (final p in points) {
      if (p.equity < minY) minY = p.equity;
      if (p.equity > maxY) maxY = p.equity;
    }
    final range = (maxY - minY).abs() < 1 ? 1 : maxY - minY;

    final gridPaint = Paint()
      ..color = AppColors.muted.withValues(alpha: 0.15)
      ..strokeWidth = 0.5;
    for (int i = 0; i <= 4; i++) {
      final y = size.height * i / 4;
      canvas.drawLine(Offset(0, y), Offset(size.width, y), gridPaint);
    }

    final linePaint = Paint()
      ..color = AppColors.accent
      ..strokeWidth = 1.5
      ..style = PaintingStyle.stroke;
    final path = Path();
    for (int i = 0; i < points.length; i++) {
      final x = size.width * i / (points.length - 1).clamp(1, 999999);
      final y = size.height - (points[i].equity - minY) / range * size.height;
      if (i == 0) {
        path.moveTo(x, y);
      } else {
        path.lineTo(x, y);
      }
    }
    canvas.drawPath(path, linePaint);
  }

  @override
  bool shouldRepaint(covariant _EquityPainter old) => old.points != points;
}
