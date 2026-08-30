import 'package:flutter/material.dart';

import '../../widgets/feature_placeholder.dart';

class NotificationsScreen extends StatelessWidget {
  const NotificationsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: FeaturePlaceholder(title: 'Notifications'),
    );
  }
}
