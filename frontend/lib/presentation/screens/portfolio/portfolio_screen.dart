import 'package:flutter/material.dart';

import '../paper_trading/paper_trading_screen.dart';

class PortfolioScreen extends StatelessWidget {
  const PortfolioScreen({super.key});

  @override
  Widget build(BuildContext context) {
    // The Portfolio tab now hosts the Paper Trading experience — a fully
    // simulated account driven by the signal engine (no real orders).
    return const PaperTradingScreen();
  }
}
