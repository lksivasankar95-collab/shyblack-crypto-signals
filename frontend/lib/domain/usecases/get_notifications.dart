import '../entities/notification_item.dart';
import '../repositories/notification_repository.dart';

class GetNotifications {
  const GetNotifications(this._repository);
  final NotificationRepository _repository;

  Future<List<NotificationItem>> call() => _repository.getNotifications();
}
