import 'dart:convert';

import '../../domain/entities/market_ticker.dart';
import 'market_ticker_model.dart';

class MarketsWsPayload {
  const MarketsWsPayload({
    required this.type,
    required this.mode,
    required this.tickers,
    this.message,
  });

  final String type;
  final String mode;
  final String? message;
  final List<MarketTicker> tickers;

  bool get isSnapshot => type == 'snapshot';
  bool get isTickers => type == 'tickers';

  static MarketsWsPayload? tryParse(dynamic raw) {
    final json = _asMap(raw);
    if (json == null) {
      return null;
    }
    final type = json['type']?.toString().toLowerCase();
    if (type != 'snapshot' && type != 'tickers') {
      return null;
    }
    final rawTickers = json['tickers'];
    return MarketsWsPayload(
      type: type!,
      mode: json['mode']?.toString() ?? '',
      message: json['message']?.toString(),
      tickers: rawTickers is List
          ? rawTickers
              .whereType<Map>()
              .map((item) => MarketTickerModel.fromJson(Map<String, dynamic>.from(item)).toEntity())
              .toList()
          : const [],
    );
  }

  static Map<String, dynamic>? _asMap(dynamic raw) {
    if (raw is Map<String, dynamic>) {
      return raw;
    }
    if (raw is Map) {
      return Map<String, dynamic>.from(raw);
    }
    if (raw is String && raw.isNotEmpty) {
      try {
        final decoded = jsonDecode(raw);
        if (decoded is Map) {
          return Map<String, dynamic>.from(decoded);
        }
      } catch (_) {
        return null;
      }
    }
    return null;
  }
}
