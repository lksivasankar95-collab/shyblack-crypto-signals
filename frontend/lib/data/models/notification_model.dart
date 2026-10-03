import '../../domain/entities/notification_item.dart';

class NotificationModel {
  const NotificationModel({
    required this.id,
    required this.title,
    required this.body,
    required this.read,
    this.category,
    this.createdAt,
  });

  final String id;
  final String title;
  final String body;
  final bool read;
  final String? category;
  final DateTime? createdAt;

  factory NotificationModel.fromJson(Map<String, dynamic> json) {
    return NotificationModel(
      id: json['id'] as String,
      title: json['title'] as String,
      body: json['body'] as String,
      read: json['read'] as bool,
      category: json['category']?.toString(),
      createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? ''),
    );
  }

  NotificationItem toEntity() =>
      NotificationItem(
        id: id,
        title: title,
        body: body,
        read: read,
        category: category,
        createdAt: createdAt,
      );
}
