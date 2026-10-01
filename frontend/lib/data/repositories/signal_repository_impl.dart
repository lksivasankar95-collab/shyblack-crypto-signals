import '../../domain/entities/signal.dart';
import '../../domain/repositories/signal_repository.dart';
import '../datasources/signal_remote_data_source.dart';

class SignalRepositoryImpl implements SignalRepository {
  SignalRepositoryImpl(this._remote);
  final SignalRemoteDataSource _remote;

  @override
  Future<List<Signal>> getSignals({String? mode}) async {
    final models = await _remote.getSignals(mode: mode);
    return models.map((model) => model.toEntity()).toList();
  }
}
