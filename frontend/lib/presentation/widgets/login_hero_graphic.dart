import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';

/// Hero graphic on the login screen.
///
/// **DROP FINAL BULL ART HERE:** replace `frontend/assets/images/login_bull.png`
/// (already listed in `pubspec.yaml`). Keep [useBundledBullAsset] `true`.
/// Set it `false` to use the geometric [LoginBullPlaceholderPainter] instead.
class LoginHeroGraphic extends StatelessWidget {
  const LoginHeroGraphic({super.key});

  static const String assetPath = 'assets/images/login_bull.png';
  static const bool useBundledBullAsset = true;

  static const double height = 120;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: height,
      width: double.infinity,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: AppColors.background,
          borderRadius: BorderRadius.circular(12),
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(12),
          child: useBundledBullAsset
              ? Image.asset(
                  assetPath,
                  fit: BoxFit.contain,
                  errorBuilder: (_, error, stack) => const CustomPaint(
                    painter: LoginBullPlaceholderPainter(),
                    child: SizedBox.expand(),
                  ),
                )
              : const CustomPaint(painter: LoginBullPlaceholderPainter(), child: SizedBox.expand()),
        ),
      ),
    );
  }
}

/// Low-poly placeholder: geometric bull + rising green candles.
/// Replace by setting [LoginHeroGraphic.useBundledBullAsset] to true.
class LoginBullPlaceholderPainter extends CustomPainter {
  const LoginBullPlaceholderPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final w = size.width;
    final h = size.height;
    canvas.drawRect(Offset.zero & size, Paint()..color = AppColors.background);

    final candlePaint = Paint()..color = AppColors.accent.withValues(alpha: 0.55);
    final wickPaint = Paint()
      ..color = AppColors.accent.withValues(alpha: 0.7)
      ..strokeWidth = 1.5;
    final candles = <(double x, double bodyTop, double bodyH, double wickTop, double wickBot)>[
      (0.08, 0.62, 0.22, 0.52, 0.88),
      (0.16, 0.54, 0.24, 0.44, 0.84),
      (0.24, 0.48, 0.22, 0.36, 0.78),
      (0.32, 0.40, 0.26, 0.28, 0.74),
      (0.40, 0.34, 0.24, 0.22, 0.66),
      (0.70, 0.30, 0.22, 0.18, 0.58),
      (0.78, 0.24, 0.20, 0.12, 0.52),
      (0.86, 0.18, 0.22, 0.08, 0.48),
    ];
    for (final c in candles) {
      final x = c.$1 * w;
      canvas.drawLine(Offset(x + 8, c.$4 * h), Offset(x + 8, c.$5 * h), wickPaint);
      canvas.drawRRect(
        RRect.fromRectAndRadius(
          Rect.fromLTWH(x, c.$2 * h, 16, c.$3 * h),
          const Radius.circular(2),
        ),
        candlePaint,
      );
    }

    final fill = Paint()..color = const Color(0xFF141414);
    final outline = Paint()
      ..color = AppColors.accent
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.2
      ..strokeJoin = StrokeJoin.round;

    Path poly(List<Offset> points) {
      final path = Path()..addPolygon(points, true);
      canvas.drawPath(path, fill);
      canvas.drawPath(path, outline);
      return path;
    }

    poly([
      Offset(w * 0.22, h * 0.78),
      Offset(w * 0.48, h * 0.42),
      Offset(w * 0.78, h * 0.72),
      Offset(w * 0.70, h * 0.92),
      Offset(w * 0.30, h * 0.92),
    ]);
    poly([
      Offset(w * 0.46, h * 0.44),
      Offset(w * 0.58, h * 0.22),
      Offset(w * 0.72, h * 0.36),
      Offset(w * 0.62, h * 0.52),
    ]);
    poly([
      Offset(w * 0.56, h * 0.24),
      Offset(w * 0.52, h * 0.08),
      Offset(w * 0.62, h * 0.18),
    ]);
    poly([
      Offset(w * 0.64, h * 0.26),
      Offset(w * 0.74, h * 0.10),
      Offset(w * 0.78, h * 0.28),
    ]);
    poly([
      Offset(w * 0.58, h * 0.38),
      Offset(w * 0.78, h * 0.40),
      Offset(w * 0.70, h * 0.50),
    ]);

    final eye = Paint()
      ..color = AppColors.accent
      ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 3);
    canvas.drawCircle(Offset(w * 0.68, h * 0.34), 4.5, eye);
    canvas.drawCircle(Offset(w * 0.68, h * 0.34), 3.2, Paint()..color = AppColors.accent);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
