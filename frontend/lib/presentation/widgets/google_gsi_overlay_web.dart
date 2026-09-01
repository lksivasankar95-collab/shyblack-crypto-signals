import 'package:flutter/widgets.dart';
import 'package:google_sign_in_web/web_only.dart' as google_web;

Widget? googleSignInWebHitTarget({required double minWidth}) {
  final width = minWidth.clamp(200.0, 400.0).toDouble();
  return Opacity(
    opacity: 0.02,
    child: SizedBox(
      width: double.infinity,
      height: 40,
      child: google_web.renderButton(
        configuration: google_web.GSIButtonConfiguration(
          type: google_web.GSIButtonType.standard,
          theme: google_web.GSIButtonTheme.outline,
          size: google_web.GSIButtonSize.large,
          text: google_web.GSIButtonText.continueWith,
          shape: google_web.GSIButtonShape.rectangular,
          minimumWidth: width,
        ),
      ),
    ),
  );
}
