import 'package:flutter/material.dart';

Widget googleSignInButton({required double minWidth, bool loading = false}) {
  return SizedBox(
    width: double.infinity,
    height: 40,
    child: Center(
      child: loading
          ? const SizedBox(
              height: 22,
              width: 22,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : const Text('Continue with Google'),
    ),
  );
}
