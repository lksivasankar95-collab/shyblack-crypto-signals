import 'package:flutter/material.dart';

import '../../../core/theme/app_colors.dart';
import '../../../domain/entities/signal.dart';
import '../../widgets/coin_letter_avatar.dart';

class SignalDetailsScreen extends StatelessWidget {
  const SignalDetailsScreen({super.key, required this.signal});

  final Signal signal;

  static void open(BuildContext context, Signal signal) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => SignalDetailsScreen(signal: signal),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final sideColor = signal.side == PositionSide.long
        ? AppColors.accent
        : AppColors.loss;

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: Text(signal.symbol)),
      body: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _SummaryCard(signal: signal, sideColor: sideColor),
            const SizedBox(height: 12),
            _PriceCard(signal: signal),
            if (signal.targetPrice2 != null || signal.targetPrice3 != null) ...[
              const SizedBox(height: 12),
              _ExtendedTargetsCard(signal: signal),
            ],
            if (signal.score != null || signal.signalGrade != null ||
                signal.entryType != null || signal.marketRegime != null ||
                signal.riskReward != null) ...[
              const SizedBox(height: 12),
              _SignalEngineCard(signal: signal),
            ],
            if (signal.technicalSummary case final summary?) ...[
              const SizedBox(height: 12),
              _InfoCard(
                title: 'Technical Summary',
                icon: Icons.insights_outlined,
                child: Text(
                  summary,
                  style: const TextStyle(
                    color: AppColors.onCard,
                    fontSize: 14,
                    height: 1.45,
                  ),
                ),
              ),
            ],
            if (signal.strategy case final strategy?) ...[
              const SizedBox(height: 12),
              _InfoCard(
                title: 'Strategy',
                icon: Icons.psychology_outlined,
                child: Text(
                  strategy,
                  style: const TextStyle(color: AppColors.onCard, fontSize: 14),
                ),
              ),
            ],
            const SizedBox(height: 12),
            _InfoCard(
              title: 'Signal Details',
              icon: Icons.badge_outlined,
              child: Column(
                children: [
                  _DetailRow(label: 'Status', value: signal.status.label),
                  _DetailRow(label: 'Side', value: signal.side.label),
                  _DetailRow(
                    label: 'Confidence',
                    value: _percent(signal.confidence?.toDouble()),
                  ),
                  _DetailRow(
                    label: 'Strategy Win Rate',
                    value: _percent(signal.strategyWinRate),
                  ),
                  _DetailRow(
                    label: 'Suggested Risk',
                    value: _percent(signal.suggestedRiskPercent),
                  ),
                  if (signal.closedAt case final closedAt?)
                    _DetailRow(label: 'Closed', value: _dateTime(closedAt)),
                  _DetailRow(
                    label: 'Created',
                    value: _dateTime(signal.createdAt),
                  ),
                ],
              ),
            ),
            if (signal.disclaimer case final disclaimer?) ...[
              const SizedBox(height: 12),
              Text(
                disclaimer,
                style: const TextStyle(
                  color: AppColors.muted,
                  fontSize: 11,
                  height: 1.4,
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  static String _percent(double? value) =>
      value == null ? '—' : '${value.toStringAsFixed(1)}%';

  static String _dateTime(DateTime value) {
    final local = value.toLocal();
    final date = '${local.year}-${_two(local.month)}-${_two(local.day)}';
    return '$date ${_two(local.hour)}:${_two(local.minute)}';
  }

  static String _two(int value) => value.toString().padLeft(2, '0');
}

// ─────────────────────────────────────────────────────────────────────────────
// Extended targets card: TP2 + TP3
// ─────────────────────────────────────────────────────────────────────────────

class _ExtendedTargetsCard extends StatelessWidget {
  const _ExtendedTargetsCard({required this.signal});

  final Signal signal;

  @override
  Widget build(BuildContext context) {
    return _InfoCard(
      title: 'Extended Targets',
      icon: Icons.flag_outlined,
      child: Row(
        children: [
          _PriceCell(label: 'TP2', value: signal.targetPrice2),
          const _Divider(),
          _PriceCell(label: 'TP3', value: signal.targetPrice3),
        ],
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Signal engine card: score, grade, regime, entry type, R:R
// ─────────────────────────────────────────────────────────────────────────────

class _SignalEngineCard extends StatelessWidget {
  const _SignalEngineCard({required this.signal});

  final Signal signal;

  @override
  Widget build(BuildContext context) {
    return _InfoCard(
      title: 'Signal Engine',
      icon: Icons.auto_graph_outlined,
      child: Column(
        children: [
          if (signal.score case final score?) ...[
            _ScoreBar(score: score),
            const SizedBox(height: 8),
          ],
          if (signal.signalGrade case final grade?)
            _DetailRow(
              label: 'Grade',
              value: grade.label,
            ),
          if (signal.entryType case final et?)
            _DetailRow(label: 'Entry Type', value: et.label),
          if (signal.marketRegime case final regime?)
            _DetailRow(label: 'Market Regime', value: regime.label),
          if (signal.riskReward case final rr?)
            _DetailRow(
              label: 'Risk / Reward',
              value: '${rr.toStringAsFixed(2)} : 1',
            ),
        ],
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Score bar widget
// ─────────────────────────────────────────────────────────────────────────────

class _ScoreBar extends StatelessWidget {
  const _ScoreBar({required this.score});

  final int score;

  @override
  Widget build(BuildContext context) {
    final fraction = score.clamp(0, 100) / 100.0;
    final color = score >= 85
        ? const Color(0xFF00E676)
        : score >= 75
        ? AppColors.accent
        : score >= 65
        ? const Color(0xFFFFB300)
        : AppColors.muted;

    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    'Score',
                    style: TextStyle(color: AppColors.muted, fontSize: 12),
                  ),
                  Text(
                    '$score / 100',
                    style: TextStyle(
                      color: color,
                      fontWeight: FontWeight.w800,
                      fontSize: 14,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 6),
              ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: LinearProgressIndicator(
                  value: fraction,
                  backgroundColor: const Color(0xFF2A2A2A),
                  valueColor: AlwaysStoppedAnimation<Color>(color),
                  minHeight: 6,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Existing widgets (unchanged from original)
// ─────────────────────────────────────────────────────────────────────────────

class _SummaryCard extends StatelessWidget {
  const _SummaryCard({required this.signal, required this.sideColor});

  final Signal signal;
  final Color sideColor;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: sideColor.withValues(alpha: 0.5), width: 1.4),
      ),
      child: Column(
        children: [
          Row(
            children: [
              CoinLetterAvatar(
                symbol: signal.symbol,
                name: signal.symbol,
                radius: 22,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      signal.symbol,
                      style: const TextStyle(
                        color: AppColors.onBackground,
                        fontWeight: FontWeight.w800,
                        fontSize: 18,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Row(
                      children: [
                        _Badge(label: signal.side.label, color: sideColor),
                        const SizedBox(width: 6),
                        _Badge(
                          label: signal.status.label,
                          color: AppColors.muted,
                        ),
                        if (signal.signalGrade case final grade?) ...[
                          const SizedBox(width: 6),
                          _Badge(
                            label: grade.label,
                            color: _gradeColor(grade),
                          ),
                        ],
                      ],
                    ),
                  ],
                ),
              ),
              if (signal.score case final score?)
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    const Text(
                      'Score',
                      style: TextStyle(color: AppColors.muted, fontSize: 11),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      '$score',
                      style: TextStyle(
                        color: _scoreColor(score),
                        fontWeight: FontWeight.w800,
                        fontSize: 20,
                      ),
                    ),
                  ],
                )
              else if (signal.confidence case final confidence?)
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    const Text(
                      'Confidence',
                      style: TextStyle(color: AppColors.muted, fontSize: 11),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      '$confidence%',
                      style: TextStyle(
                        color: sideColor,
                        fontWeight: FontWeight.w800,
                        fontSize: 16,
                      ),
                    ),
                  ],
                ),
            ],
          ),
        ],
      ),
    );
  }

  static Color _gradeColor(SignalGrade grade) => switch (grade) {
    SignalGrade.strongBuy => const Color(0xFF00E676),
    SignalGrade.buy => AppColors.accent,
    SignalGrade.watch => const Color(0xFFFFB300),
    SignalGrade.noTrade => AppColors.loss,
  };

  static Color _scoreColor(int score) {
    if (score >= 85) return const Color(0xFF00E676);
    if (score >= 75) return AppColors.accent;
    if (score >= 65) return const Color(0xFFFFB300);
    return AppColors.muted;
  }
}

class _PriceCard extends StatelessWidget {
  const _PriceCard({required this.signal});

  final Signal signal;

  @override
  Widget build(BuildContext context) {
    return _InfoCard(
      title: 'Price Levels',
      icon: Icons.currency_exchange_outlined,
      child: Row(
        children: [
          _PriceCell(label: 'Entry', value: signal.entryPrice),
          const _Divider(),
          _PriceCell(label: 'Target', value: signal.targetPrice),
          const _Divider(),
          _PriceCell(label: 'Stop Loss', value: signal.stopLoss),
        ],
      ),
    );
  }
}

class _PriceCell extends StatelessWidget {
  const _PriceCell({required this.label, required this.value});

  final String label;
  final double? value;

  @override
  Widget build(BuildContext context) {
    final color = value == null
        ? AppColors.muted
        : label == 'Stop Loss'
        ? AppColors.loss
        : AppColors.accent;
    return Expanded(
      child: Column(
        children: [
          Text(
            label,
            style: const TextStyle(color: AppColors.muted, fontSize: 11),
          ),
          const SizedBox(height: 6),
          Text(
            value == null ? '—' : MarketFormat.price(value!),
            style: TextStyle(
              color: color,
              fontWeight: FontWeight.w800,
              fontSize: 15,
            ),
          ),
        ],
      ),
    );
  }
}

class _Divider extends StatelessWidget {
  const _Divider();

  @override
  Widget build(BuildContext context) {
    return Container(width: 1, height: 34, color: const Color(0xFF2A2A2A));
  }
}

class _InfoCard extends StatelessWidget {
  const _InfoCard({
    required this.title,
    required this.icon,
    required this.child,
  });

  final String title;
  final IconData icon;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(14),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Icon(icon, color: AppColors.accent, size: 18),
              const SizedBox(width: 8),
              Text(
                title,
                style: const TextStyle(
                  color: AppColors.muted,
                  fontSize: 12,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 0.6,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          child,
        ],
      ),
    );
  }
}

class _DetailRow extends StatelessWidget {
  const _DetailRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Expanded(
            child: Text(
              label,
              style: const TextStyle(color: AppColors.muted, fontSize: 13),
            ),
          ),
          Text(
            value,
            style: const TextStyle(
              color: AppColors.onCard,
              fontWeight: FontWeight.w700,
              fontSize: 13,
            ),
          ),
        ],
      ),
    );
  }
}

class _Badge extends StatelessWidget {
  const _Badge({required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.14),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: color,
          fontSize: 11,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }
}
