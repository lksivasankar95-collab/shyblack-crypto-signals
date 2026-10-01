import '../entities/signal.dart';

abstract class SignalRepository {
  /// Signals for a trading mode (`SPOT` / `FUTURES`); null = backend default.
  Future<List<Signal>> getSignals({String? mode});
}
