class NotificationItem {
  const NotificationItem({
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
}
