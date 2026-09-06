import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../domain/entities/notification_item.dart';
import '../../providers/notifications_controller.dart';

class NotificationsScreen extends ConsumerWidget {
  const NotificationsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final notifications = ref.watch(notificationsControllerProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Notifications')),
      body: notifications.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _NotificationError(
          message: error.toString(),
          onRetry: () => ref.read(notificationsControllerProvider.notifier).refresh(),
        ),
        data: (items) => _NotificationHistory(
          items: items,
          onRefresh: () => ref.read(notificationsControllerProvider.notifier).refresh(),
        ),
      ),
    );
  }
}

class _NotificationHistory extends StatelessWidget {
  const _NotificationHistory({required this.items, required this.onRefresh});

  final List<NotificationItem> items;
  final Future<void> Function() onRefresh;

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) {
      return RefreshIndicator(
        color: AppColors.accent,
        backgroundColor: AppColors.card,
        onRefresh: onRefresh,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: const [
            SizedBox(
              height: 300,
              child: Center(
                child: Text(
                  'No notifications yet',
                  style: TextStyle(color: AppColors.muted, fontSize: 16),
                ),
              ),
            ),
          ],
        ),
      );
    }

    return RefreshIndicator(
      color: AppColors.accent,
      backgroundColor: AppColors.card,
      onRefresh: onRefresh,
      child: ListView.separated(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
        itemCount: items.length,
        separatorBuilder: (_, _) => const SizedBox(height: 8),
        itemBuilder: (context, index) => _NotificationTile(item: items[index]),
      ),
    );
  }
}

class _NotificationTile extends StatelessWidget {
  const _NotificationTile({required this.item});

  final NotificationItem item;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: item.read ? AppColors.card : const Color(0xFF202A23),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: item.read ? Colors.transparent : AppColors.accent.withValues(alpha: 0.6),
        ),
      ),
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 13),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(top: 4),
            child: Icon(
              item.read ? Icons.notifications_none : Icons.notifications_active,
              color: item.read ? AppColors.muted : AppColors.accent,
              size: 21,
            ),
          ),
          const SizedBox(width: 11),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: Text(
                        item.title,
                        style: TextStyle(
                          color: AppColors.onCard,
                          fontSize: 15,
                          fontWeight: item.read ? FontWeight.w600 : FontWeight.w800,
                        ),
                      ),
                    ),
                    if (!item.read)
                      Container(
                        margin: const EdgeInsets.only(left: 8, top: 4),
                        width: 8,
                        height: 8,
                        decoration: const BoxDecoration(
                          color: AppColors.accent,
                          shape: BoxShape.circle,
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 5),
                Text(
                  item.body,
                  style: const TextStyle(color: AppColors.muted, height: 1.35),
                ),
                if (item.category != null || item.createdAt != null) ...[
                  const SizedBox(height: 9),
                  Row(
                    children: [
                      if (item.category != null)
                        Text(
                          item.category!,
                          style: const TextStyle(
                            color: AppColors.accent,
                            fontSize: 11,
                            fontWeight: FontWeight.w800,
                            letterSpacing: 0.5,
                          ),
                        ),
                      if (item.category != null && item.createdAt != null)
                        const Padding(
                          padding: EdgeInsets.symmetric(horizontal: 7),
                          child: Text('·', style: TextStyle(color: AppColors.muted)),
                        ),
                      if (item.createdAt != null)
                        Text(
                          _formatDate(item.createdAt!),
                          style: const TextStyle(color: AppColors.muted, fontSize: 11),
                        ),
                    ],
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }

  String _formatDate(DateTime value) {
    final local = value.toLocal();
    final month = local.month.toString().padLeft(2, '0');
    final day = local.day.toString().padLeft(2, '0');
    final hour = local.hour.toString().padLeft(2, '0');
    final minute = local.minute.toString().padLeft(2, '0');
    return '${local.year}-$month-$day $hour:$minute';
  }
}

class _NotificationError extends StatelessWidget {
  const _NotificationError({required this.message, required this.onRetry});

  final String message;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.cloud_off, color: AppColors.muted, size: 34),
            const SizedBox(height: 12),
            const Text(
              'Could not load notifications',
              style: TextStyle(color: AppColors.onCard, fontSize: 16, fontWeight: FontWeight.w700),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 6),
            Text(
              message,
              style: const TextStyle(color: AppColors.muted),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: onRetry,
              icon: const Icon(Icons.refresh),
              label: const Text('Retry'),
            ),
          ],
        ),
      ),
    );
  }
}
