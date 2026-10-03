import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';

class LoginBrandHeader extends StatelessWidget {
  const LoginBrandHeader({super.key});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        CustomPaint(
          size: const Size(18, 12),
          painter: _CrownPainter(),
        ),
        const SizedBox(height: 2),
        CustomPaint(
          size: const Size(36, 40),
          painter: _ShieldLogoPainter(),
        ),
        const SizedBox(height: 4),
        RichText(
          text: const TextSpan(
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w900,
              letterSpacing: 1.6,
              height: 1.05,
            ),
            children: [
              TextSpan(text: 'SHY', style: TextStyle(color: AppColors.onBackground)),
              TextSpan(text: 'BLACK', style: TextStyle(color: AppColors.accent)),
            ],
          ),
        ),
        const SizedBox(height: 4),
        Row(
          children: [
            const Expanded(child: Divider(color: AppColors.accent, thickness: 1)),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 10),
              child: Text(
                '— CRYPTO SIGNALS —',
                style: TextStyle(
                  color: AppColors.accent,
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 1.0,
                ),
              ),
            ),
            const Expanded(child: Divider(color: AppColors.accent, thickness: 1)),
          ],
        ),
      ],
    );
  }
}

class _ShieldLogoPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final path = Path()
      ..moveTo(size.width * 0.5, 0)
      ..lineTo(size.width * 0.94, size.height * 0.18)
      ..lineTo(size.width * 0.88, size.height * 0.62)
      ..quadraticBezierTo(size.width * 0.5, size.height * 1.08, size.width * 0.12, size.height * 0.62)
      ..lineTo(size.width * 0.06, size.height * 0.18)
      ..close();

    canvas.drawPath(
      path,
      Paint()
        ..color = const Color(0xFF000000)
        ..style = PaintingStyle.fill,
    );
    canvas.drawPath(
      path,
      Paint()
        ..color = AppColors.accent
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.8,
    );

    final text = TextPainter(
      text: const TextSpan(
        text: 'SB',
        style: TextStyle(
          color: AppColors.accent,
          fontSize: 14,
          fontWeight: FontWeight.w900,
          letterSpacing: 0.6,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    text.paint(
      canvas,
      Offset((size.width - text.width) / 2, size.height * 0.28),
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

class _CrownPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final fill = Paint()..color = AppColors.accent;
    final path = Path()
      ..moveTo(size.width * 0.08, size.height * 0.72)
      ..lineTo(size.width * 0.08, size.height * 0.38)
      ..lineTo(size.width * 0.26, size.height * 0.55)
      ..lineTo(size.width * 0.5, size.height * 0.08)
      ..lineTo(size.width * 0.74, size.height * 0.55)
      ..lineTo(size.width * 0.92, size.height * 0.38)
      ..lineTo(size.width * 0.92, size.height * 0.72)
      ..close();
    canvas.drawPath(path, fill);
    canvas.drawRRect(
      RRect.fromLTRBR(size.width * 0.06, size.height * 0.78, size.width * 0.94, size.height * 0.96, const Radius.circular(2)),
      fill,
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
