import 'dart:async';

import 'package:web_socket_channel/web_socket_channel.dart';

abstract class MarketsSocketConnector {
  MarketsSocketSession connect(Uri uri);
}

class MarketsSocketSession {
  const MarketsSocketSession({
    required this.stream,
    required this.ready,
    required this.close,
  });

  final Stream<dynamic> stream;
  final Future<void> ready;
  final Future<void> Function() close;
}

class WebSocketChannelConnector implements MarketsSocketConnector {
  const WebSocketChannelConnector();

  @override
  MarketsSocketSession connect(Uri uri) {
    final channel = WebSocketChannel.connect(uri);
    return MarketsSocketSession(
      stream: channel.stream,
      ready: channel.ready,
      close: () => channel.sink.close(),
    );
  }
}
