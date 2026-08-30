import '../entities/signal.dart';

abstract class SignalRepository {
  Future<List<Signal>> getSignals();
}
