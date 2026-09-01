import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/constants/google_auth_config.dart';
import '../../core/di/providers.dart';
import 'google_gsi_overlay.dart';

class ContinueWithGoogleButton extends ConsumerStatefulWidget {
  const ContinueWithGoogleButton({
    super.key,
    required this.loading,
    required this.onPressed,
    required this.onWebIdToken,
  });

  final bool loading;
  final VoidCallback onPressed;
  final ValueChanged<String> onWebIdToken;

  @override
  ConsumerState<ContinueWithGoogleButton> createState() => _ContinueWithGoogleButtonState();
}

class _ContinueWithGoogleButtonState extends ConsumerState<ContinueWithGoogleButton> {
  StreamSubscription<String>? _webTokens;

  @override
  void initState() {
    super.initState();
    if (kIsWeb) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _bindWeb());
    }
  }

  Future<void> _bindWeb() async {
    if (!mounted) {
      return;
    }
    final google = ref.read(googleSignInDataSourceProvider);
    await google.ensureInitialized();
    if (!mounted) {
      return;
    }
    _webTokens = google.idTokens.listen(widget.onWebIdToken);
  }

  @override
  void dispose() {
    _webTokens?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final webHit = kIsWeb && !widget.loading && GoogleAuthConfig.isConfigured
            ? googleSignInWebHitTarget(minWidth: constraints.maxWidth)
            : null;
        return Stack(
          alignment: Alignment.center,
          children: [
            IgnorePointer(
              ignoring: kIsWeb && webHit != null,
              child: OutlinedButton(
                onPressed: widget.loading ? null : widget.onPressed,
                style: OutlinedButton.styleFrom(
                  minimumSize: const Size.fromHeight(40),
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                ),
                child: widget.loading
                    ? const SizedBox(
                        height: 22,
                        width: 22,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const FittedBox(
                        fit: BoxFit.scaleDown,
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            GoogleMark(),
                            SizedBox(width: 8),
                            Text('Continue with Google'),
                          ],
                        ),
                      ),
              ),
            ),
            if (webHit != null)
              Positioned.fill(
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(12),
                  child: webHit,
                ),
              ),
          ],
        );
      },
    );
  }
}

class GoogleMark extends StatelessWidget {
  const GoogleMark({super.key});

  @override
  Widget build(BuildContext context) {
    return const SizedBox(
      width: 22,
      height: 22,
      child: CustomPaint(painter: GoogleGPainter()),
    );
  }
}

class GoogleGPainter extends CustomPainter {
  const GoogleGPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final stroke = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = size.width * 0.18
      ..strokeCap = StrokeCap.round;
    final rect = Rect.fromCircle(center: Offset(size.width / 2, size.height / 2), radius: size.width * 0.36);
    stroke.color = const Color(0xFF4285F4);
    canvas.drawArc(rect, -0.2, 1.6, false, stroke);
    stroke.color = const Color(0xFF34A853);
    canvas.drawArc(rect, 1.4, 1.0, false, stroke);
    stroke.color = const Color(0xFFFBBC05);
    canvas.drawArc(rect, 2.4, 0.8, false, stroke);
    stroke.color = const Color(0xFFEA4335);
    canvas.drawArc(rect, 3.2, 1.1, false, stroke);
    canvas.drawLine(
      Offset(size.width * 0.5, size.height * 0.5),
      Offset(size.width * 0.86, size.height * 0.5),
      Paint()
        ..color = const Color(0xFF4285F4)
        ..strokeWidth = size.width * 0.16
        ..strokeCap = StrokeCap.square,
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
