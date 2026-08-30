import '../../domain/entities/notification_item.dart';
import '../../domain/repositories/notification_repository.dart';
import '../datasources/notification_remote_data_source.dart';

class NotificationRepositoryImpl implements NotificationRepository {
  NotificationRepositoryImpl(this._remote);
  final NotificationRemoteDataSource _remote;

  @override
  Future<List<NotificationItem>> getNotifications() async {
    final models = await _remote.getNotifications();
    return models.map((model) => model.toEntity()).toList();
  }
}
