import '../entities/notification_item.dart';

abstract class NotificationRepository {
  Future<List<NotificationItem>> getNotifications();
  Future<void> registerDeviceToken(String token);
  Future<void> removeDeviceToken(String token);
}
