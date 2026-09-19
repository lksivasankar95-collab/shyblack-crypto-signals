import 'package:flutter/material.dart';
import 'package:google_sign_in_web/web_only.dart' as google_web;

Widget googleSignInButton({required double minWidth, bool loading = false}) {
  final width = minWidth.clamp(200.0, 400.0).toDouble();
  if (loading) {
    return SizedBox(
      width: double.infinity,
      height: 40,
      child: Center(
        child: SizedBox(
          height: 22,
          width: 22,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
      ),
    );
  }
  return SizedBox(
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
  );
}
