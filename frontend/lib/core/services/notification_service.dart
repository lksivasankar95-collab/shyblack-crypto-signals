import 'dart:async';

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../di/providers.dart';
import '../../presentation/providers/news_providers.dart';
import '../../presentation/providers/notifications_controller.dart';

final notificationServiceProvider = Provider<NotificationService>((ref) {
  return NotificationService(ref);
});

class NotificationService {
  NotificationService(this._ref);
  final Ref _ref;
  bool _initialized = false;
  final FlutterLocalNotificationsPlugin _local =
      FlutterLocalNotificationsPlugin();
  final Set<String> _displayed = <String>{};

  Future<void> init() async {
    if (_initialized) return;
    await Firebase.initializeApp();

    // Setup local notification channel for foreground presentation
    const AndroidInitializationSettings initSettingsAndroid =
        AndroidInitializationSettings('launcher_icon');
    const InitializationSettings initSettings = InitializationSettings(
      android: initSettingsAndroid,
    );
    await _local.initialize(initSettings);

    const AndroidNotificationChannel channel = AndroidNotificationChannel(
      'shyblack_signals',
      'ShyBlack Signals',
      importance: Importance.high,
      description: 'Signal notifications',
    );
    await _local
        .resolvePlatformSpecificImplementation<
          AndroidFlutterLocalNotificationsPlugin
        >()
        ?.createNotificationChannel(channel);

    // Foreground message presentation
    FirebaseMessaging.onMessage.listen((RemoteMessage message) async {
      _handleIncoming(message, foreground: true);
    });

    // Background/terminated tap
    FirebaseMessaging.onMessageOpenedApp.listen((RemoteMessage message) async {
      _handleTap(message);
    });

    // Handle terminated -> opened from notification
    final initial = await FirebaseMessaging.instance.getInitialMessage();
    if (initial != null) {
      _handleTap(initial);
    }

    // token refresh
    FirebaseMessaging.instance.onTokenRefresh.listen((token) async {
      await _registerToken(token);
    });

    _initialized = true;
  }

  Future<void> ensureInitialized() async {
    if (!_initialized) await init();
  }

  Future<void> registerDeviceTokenForCurrentUser() async {
    final token = await FirebaseMessaging.instance.getToken();
    if (token != null) await _registerToken(token);
  }

  Future<void> _registerToken(String token) async {
    try {
      final repo = _ref.read(notificationRepositoryProvider);
      await repo.registerDeviceToken(token);
    } catch (_) {}
  }

  Future<void> unregisterToken(String token) async {
    try {
      final repo = _ref.read(notificationRepositoryProvider);
      await repo.removeDeviceToken(token);
    } catch (_) {}
  }

  void _handleIncoming(RemoteMessage message, {bool foreground = false}) async {
    final data = message.data;
    final title = message.notification?.title ?? data['title'] ?? '';
    final body = message.notification?.body ?? data['body'] ?? '';
    final type = data['type'] as String?;
    final newsId = data['newsId'] as String?;
    final nid =
        newsId ??
        data['notificationId'] as String? ??
        data['signalId'] as String?;

    // prevent duplicate local presentation
    if (nid != null && _displayed.contains(nid)) return;

    // Update notification repository/provider (refresh list)
    try {
      await _ref.read(notificationsControllerProvider.notifier).refresh();
    } catch (_) {}

    // News: insert the exact article at the top of the feed (dedup inside).
    if (type == 'news' && newsId != null) {
      try {
        await _ref
            .read(newsFeedControllerProvider.notifier)
            .prependById(newsId);
      } catch (_) {}
    }

    // Show local notification for foreground
    if (foreground) {
      final id = nid?.hashCode ?? DateTime.now().millisecondsSinceEpoch;
      const AndroidNotificationDetails details = AndroidNotificationDetails(
        'shyblack_signals',
        'ShyBlack Signals',
        importance: Importance.high,
        priority: Priority.high,
      );
      const NotificationDetails platform = NotificationDetails(
        android: details,
      );
      await _local.show(id, title, body, platform, payload: nid);
      if (nid != null) _displayed.add(nid);
    }
  }

  void _handleTap(RemoteMessage message) {
    final data = message.data;
    if ((data['type'] as String?) == 'news') {
      final newsId = data['newsId'] as String?;
      if (newsId != null) {
        try {
          _ref.read(pendingNewsProvider.notifier).state = newsId;
        } catch (_) {}
      }
      return;
    }
    final signalId = data['signalId'] as String?;
    if (signalId != null) {
      try {
        _ref.read(pendingSignalProvider.notifier).state = signalId;
      } catch (_) {}
    }
  }
}

// Background message handler must be a top-level function
Future<void> firebaseBackgroundHandler(RemoteMessage message) async {
  await Firebase.initializeApp();
}
