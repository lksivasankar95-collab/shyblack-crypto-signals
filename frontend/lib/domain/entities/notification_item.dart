class NotificationItem {
  const NotificationItem({
    required this.id,
    required this.title,
    required this.body,
    required this.read,
  });

  final String id;
  final String title;
  final String body;
  final bool read;
}
