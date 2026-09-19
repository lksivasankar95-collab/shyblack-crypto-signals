import '../../domain/entities/live_performance.dart';

class LivePerformanceModel {
  const LivePerformanceModel(this.performance);

  final LivePerformance performance;

  factory LivePerformanceModel.fromJson(Map<String, dynamic> json) {
    return LivePerformanceModel(
      LivePerformance(
        totalOrders: (json['totalOrders'] as num?)?.toInt() ?? 0,
        filledEntries: (json['filledEntries'] as num?)?.toInt() ?? 0,
        rejections: (json['rejections'] as num?)?.toInt() ?? 0,
        totalFees: _num(json['totalFees']),
        totalNotional: _num(json['totalNotional']),
      ),
    );
  }

  static double _num(dynamic v) {
    if (v == null) return 0;
    if (v is num) return v.toDouble();
    return double.tryParse(v.toString()) ?? 0;
  }
}
