import 'package:flutter/material.dart';

import '../../core/constants/app_constants.dart';
import '../../core/theme/app_colors.dart';

class BrandMark extends StatelessWidget {
  const BrandMark({super.key, this.compact = false});

  final bool compact;

  @override
  Widget build(BuildContext context) {
    final size = compact ? 56.0 : 88.0;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: size,
          height: size,
          decoration: BoxDecoration(
            color: AppColors.card,
            borderRadius: BorderRadius.circular(22),
            border: Border.all(color: AppColors.accent, width: 1.6),
          ),
          child: Icon(Icons.bolt_rounded, color: AppColors.accent, size: compact ? 32 : 48),
        ),
        SizedBox(height: compact ? 12 : 20),
        Text(
          AppConstants.appName,
          style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                color: AppColors.accent,
                fontWeight: FontWeight.w800,
                letterSpacing: 0.6,
              ),
        ),
      ],
    );
  }
}
