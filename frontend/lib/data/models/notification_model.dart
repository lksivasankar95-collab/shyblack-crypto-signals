import '../../domain/entities/notification_item.dart';

class NotificationModel {
  const NotificationModel({
    required this.id,
    required this.title,
    required this.body,
    required this.read,
  });

  final String id;
  final String title;
  final String body;
  final bool read;

  factory NotificationModel.fromJson(Map<String, dynamic> json) {
    return NotificationModel(
      id: json['id'] as String,
      title: json['title'] as String,
      body: json['body'] as String,
      read: json['read'] as bool,
    );
  }

  NotificationItem toEntity() =>
      NotificationItem(id: id, title: title, body: body, read: read);
}
