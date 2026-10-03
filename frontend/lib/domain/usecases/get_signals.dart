import '../entities/signal.dart';
import '../repositories/signal_repository.dart';

class GetSignals {
  const GetSignals(this._repository);
  final SignalRepository _repository;

  Future<List<Signal>> call({String? mode}) => _repository.getSignals(mode: mode);
}
