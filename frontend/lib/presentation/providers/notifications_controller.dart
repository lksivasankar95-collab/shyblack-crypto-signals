import 'dart:async';
import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/di/providers.dart';
import '../../data/datasources/markets_websocket_client.dart';
import '../../domain/entities/notification_item.dart';

class NotificationsController extends AsyncNotifier<List<NotificationItem>> {
  MarketsSocketSession? _session;
  StreamSubscription<dynamic>? _sub;

  @override
  Future<List<NotificationItem>> build() async {
    ref.onDispose(_disposeSocket);
    // Start websocket session (private alerts)
    _startSocket();
    final list = await ref.read(getNotificationsProvider).call();
    return list;
  }

  Future<void> refresh() async {
    try {
      state = AsyncData(await ref.read(getNotificationsProvider).call());
    } catch (e, st) {
      state = AsyncError(e, st);
    }
  }

  void _startSocket() {
    if (_session != null) return;
    try {
      final connector = ref.read(marketsSocketConnectorProvider);
      final uri = Uri.parse('ws://localhost:8080/ws/private');
      final session = connector.connect(uri);
      _session = session;
      _sub = session.stream.listen((raw) {
        try {
          final Map<String, dynamic> payload = jsonDecode(raw as String) as Map<String, dynamic>;
          final type = (payload['type'] as String?)?.toLowerCase();
          if (type == 'alert') {
            // backend persists notification history; refresh list
            unawaited(refresh());
          }
        } catch (_) {}
      }, onError: (_) {}, onDone: () {});
    } catch (_) {}
  }

  void _disposeSocket() {
    unawaited(_sub?.cancel());
    _sub = null;
    final session = _session;
    _session = null;
    if (session != null) unawaited(session.close());
  }
}

final notificationsControllerProvider = AsyncNotifierProvider<NotificationsController, List<NotificationItem>>(
  NotificationsController.new,
);
