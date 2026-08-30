import '../entities/signal.dart';
import '../repositories/signal_repository.dart';

class GetSignals {
  const GetSignals(this._repository);
  final SignalRepository _repository;

  Future<List<Signal>> call() => _repository.getSignals();
}
