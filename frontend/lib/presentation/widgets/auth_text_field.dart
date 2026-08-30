import 'package:flutter/material.dart';

class AuthTextField extends StatelessWidget {
  const AuthTextField({
    super.key,
    required this.controller,
    required this.label,
    required this.validator,
    this.keyboardType,
    this.obscureText = false,
    this.textInputAction,
    this.onSubmitted,
  });

  final TextEditingController controller;
  final String label;
  final String? Function(String?) validator;
  final TextInputType? keyboardType;
  final bool obscureText;
  final TextInputAction? textInputAction;
  final ValueChanged<String>? onSubmitted;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: controller,
      validator: validator,
      keyboardType: keyboardType,
      obscureText: obscureText,
      textInputAction: textInputAction,
      onFieldSubmitted: onSubmitted,
      autovalidateMode: AutovalidateMode.onUserInteraction,
      decoration: InputDecoration(labelText: label),
    );
  }
}
