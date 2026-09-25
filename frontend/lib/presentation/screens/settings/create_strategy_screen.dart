import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../domain/entities/trading_strategy.dart';
import '../../providers/strategy_providers.dart';

class CreateStrategyScreen extends ConsumerStatefulWidget {
  final StrategyTradingMode mode;
  const CreateStrategyScreen({super.key, required this.mode});

  @override
  ConsumerState<CreateStrategyScreen> createState() => _CreateStrategyScreenState();
}

class _CreateStrategyScreenState extends ConsumerState<CreateStrategyScreen> {
  final _nameCtrl = TextEditingController();
  final _descCtrl = TextEditingController();
  bool _saving = false;

  @override
  void dispose() {
    _nameCtrl.dispose();
    _descCtrl.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final name = _nameCtrl.text.trim();
    if (name.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Strategy name is required')),
      );
      return;
    }
    setState(() => _saving = true);
    try {
      await ref.read(strategyTabProvider(widget.mode).notifier)
          .createStrategy(name, _descCtrl.text.trim().isEmpty ? null : _descCtrl.text.trim());
      if (mounted) Navigator.of(context).pop();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to create strategy: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final modeLabel = widget.mode == StrategyTradingMode.spot ? 'Spot' : 'Futures';
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        foregroundColor: AppColors.onBackground,
        title: Text('New $modeLabel Strategy',
            style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 17)),
        actions: [
          TextButton(
            onPressed: _saving ? null : _save,
            child: _saving
                ? const SizedBox(width: 16, height: 16,
                    child: CircularProgressIndicator(strokeWidth: 2, color: AppColors.accent))
                : const Text('Save', style: TextStyle(color: AppColors.accent, fontWeight: FontWeight.w800)),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _label('Strategy Name *'),
          const SizedBox(height: 6),
          _field(_nameCtrl, 'e.g. My EMA Breakout Strategy'),
          const SizedBox(height: 16),
          _label('Description'),
          const SizedBox(height: 6),
          _field(_descCtrl, 'Describe your strategy rules...', maxLines: 4),
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: AppColors.card,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: const Color(0xFF2A2A2A)),
            ),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Text('Trading Mode',
                  style: TextStyle(color: AppColors.muted, fontSize: 12, fontWeight: FontWeight.w600)),
              const SizedBox(height: 8),
              Row(children: [
                const Icon(Icons.lock, color: AppColors.muted, size: 16),
                const SizedBox(width: 6),
                Text(
                  widget.mode == StrategyTradingMode.spot ? 'SPOT' : 'FUTURES',
                  style: const TextStyle(color: AppColors.onBackground, fontWeight: FontWeight.w700),
                ),
              ]),
            ]),
          ),
          const SizedBox(height: 24),
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: AppColors.card,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: const Color(0xFF2A2A2A)),
            ),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Text('Strategy Configuration',
                  style: TextStyle(color: AppColors.onBackground, fontWeight: FontWeight.w700, fontSize: 14)),
              const SizedBox(height: 6),
              const Text(
                'Your strategy will use the default EMA+RSI indicator configuration. '
                'Advanced parameter customization is available after creation.',
                style: TextStyle(color: AppColors.muted, fontSize: 12),
              ),
              const SizedBox(height: 12),
              _configRow('EMA Periods', '20 / 50 / 200'),
              _configRow('RSI Period', '14'),
              _configRow('MACD', '12-26-9'),
              _configRow('Min R:R', '1.5'),
              if (widget.mode == StrategyTradingMode.futures)
                _configRow('Default Leverage', '3x'),
            ]),
          ),
          const SizedBox(height: 28),
          ElevatedButton(
            onPressed: _saving ? null : _save,
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.accent,
              foregroundColor: AppColors.background,
              minimumSize: const Size.fromHeight(50),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
            child: _saving
                ? const SizedBox(width: 20, height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2, color: AppColors.background))
                : const Text('CREATE STRATEGY',
                    style: TextStyle(fontWeight: FontWeight.w800, letterSpacing: 0.8)),
          ),
        ],
      ),
    );
  }

  static Widget _label(String text) => Text(text,
      style: const TextStyle(color: AppColors.muted, fontSize: 12, fontWeight: FontWeight.w600));

  static Widget _field(TextEditingController ctrl, String hint, {int maxLines = 1}) => TextField(
    controller: ctrl,
    maxLines: maxLines,
    style: const TextStyle(color: AppColors.onBackground),
    decoration: InputDecoration(
      hintText: hint,
      hintStyle: const TextStyle(color: AppColors.muted),
      filled: true,
      fillColor: AppColors.card,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: const BorderSide(color: Color(0xFF2A2A2A)),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: const BorderSide(color: Color(0xFF2A2A2A)),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: const BorderSide(color: AppColors.accent),
      ),
    ),
  );

  static Widget _configRow(String label, String value) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 4),
    child: Row(children: [
      Text(label, style: const TextStyle(color: AppColors.muted, fontSize: 12)),
      const Spacer(),
      Text(value, style: const TextStyle(color: AppColors.onBackground, fontSize: 12, fontWeight: FontWeight.w600)),
    ]),
  );
}
