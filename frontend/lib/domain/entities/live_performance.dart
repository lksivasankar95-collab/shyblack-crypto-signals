class LivePerformance {
  const LivePerformance({
    required this.totalOrders,
    required this.filledEntries,
    required this.rejections,
    required this.totalFees,
    required this.totalNotional,
  });

  final int totalOrders;
  final int filledEntries;
  final int rejections;
  final double totalFees;
  final double totalNotional;
}
