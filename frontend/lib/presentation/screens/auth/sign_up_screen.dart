import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../providers/signup_controller.dart';
import '../../widgets/auth_text_field.dart';
import '../../widgets/brand_mark.dart';
import 'login_screen.dart';

class SignUpScreen extends ConsumerStatefulWidget {
  const SignUpScreen({super.key});

  @override
  ConsumerState<SignUpScreen> createState() => _SignUpScreenState();
}

class _SignUpScreenState extends ConsumerState<SignUpScreen> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _obscurePassword = true;

  @override
  void dispose() {
    _nameController.dispose();
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    if (!_formKey.currentState!.validate()) {
      return;
    }
    final ok = await ref.read(signupControllerProvider.notifier).submit(
          fullName: _nameController.text.trim(),
          email: _emailController.text.trim(),
          password: _passwordController.text,
        );
    if (!mounted || !ok) {
      return;
    }
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute<void>(
        builder: (_) => const LoginScreen(successMessage: 'Account created. Please log in.'),
      ),
      (route) => false,
    );
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(signupControllerProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Sign Up')),
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 420),
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const BrandMark(compact: true),
                    const SizedBox(height: 28),
                    AuthTextField(
                      controller: _nameController,
                      label: 'Full Name',
                      textInputAction: TextInputAction.next,
                      validator: (value) {
                        if (value == null || value.trim().isEmpty) {
                          return 'Full name is required';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 16),
                    AuthTextField(
                      controller: _emailController,
                      label: 'Email',
                      keyboardType: TextInputType.emailAddress,
                      textInputAction: TextInputAction.next,
                      validator: (value) {
                        final email = value?.trim() ?? '';
                        if (email.isEmpty) {
                          return 'Email is required';
                        }
                        final valid = RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(email);
                        if (!valid) {
                          return 'Enter a valid email';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _passwordController,
                      obscureText: _obscurePassword,
                      textInputAction: TextInputAction.done,
                      onFieldSubmitted: (_) => _submit(),
                      autovalidateMode: AutovalidateMode.onUserInteraction,
                      validator: (value) {
                        final password = value ?? '';
                        if (password.length < 8) {
                          return 'Password must be at least 8 characters';
                        }
                        if (!RegExp(r'\d').hasMatch(password)) {
                          return 'Password must contain at least one number';
                        }
                        return null;
                      },
                      decoration: InputDecoration(
                        labelText: 'Password',
                        suffixIcon: IconButton(
                          onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                          icon: Icon(
                            _obscurePassword ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                            color: AppColors.muted,
                          ),
                        ),
                      ),
                    ),
                    if (state.error != null) ...[
                      const SizedBox(height: 16),
                      Text(
                        state.error!,
                        textAlign: TextAlign.center,
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: AppColors.loss),
                      ),
                    ],
                    const SizedBox(height: 24),
                    ElevatedButton(
                      onPressed: state.loading ? null : _submit,
                      child: state.loading
                          ? const SizedBox(
                              height: 22,
                              width: 22,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Text('Sign Up'),
                    ),
                    const SizedBox(height: 16),
                    Wrap(
                      alignment: WrapAlignment.center,
                      children: [
                        Text('Already have an account? ', style: Theme.of(context).textTheme.bodyMedium),
                        GestureDetector(
                          onTap: () => Navigator.of(context).pop(),
                          child: Text(
                            'Login',
                            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                                  color: AppColors.accent,
                                  fontWeight: FontWeight.w700,
                                ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
