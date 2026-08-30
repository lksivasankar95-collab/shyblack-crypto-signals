import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../providers/login_controller.dart';
import '../../shell/main_shell.dart';
import '../../widgets/auth_text_field.dart';
import '../../widgets/brand_mark.dart';
import 'sign_up_screen.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key, this.successMessage});

  final String? successMessage;

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _obscurePassword = true;

  @override
  void initState() {
    super.initState();
    final message = widget.successMessage;
    if (message != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) {
          return;
        }
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(message), backgroundColor: AppColors.card),
        );
      });
    }
  }

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    if (!_formKey.currentState!.validate()) {
      return;
    }
    final ok = await ref.read(loginControllerProvider.notifier).submit(
          email: _emailController.text.trim(),
          password: _passwordController.text,
        );
    if (!mounted || !ok) {
      return;
    }
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute<void>(builder: (_) => const MainShell()),
      (route) => false,
    );
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(loginControllerProvider);

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 420),
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const BrandMark(compact: true),
                    const SizedBox(height: 8),
                    Text(
                      'Welcome back',
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: AppColors.muted),
                    ),
                    const SizedBox(height: 32),
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
                        if (value == null || value.isEmpty) {
                          return 'Password is required';
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
                          : const Text('Login'),
                    ),
                    const SizedBox(height: 12),
                    OutlinedButton.icon(
                      onPressed: state.loading
                          ? null
                          : () {
                              ScaffoldMessenger.of(context).showSnackBar(
                                const SnackBar(content: Text('Google Sign-In will be connected later')),
                              );
                            },
                      icon: const Icon(Icons.g_mobiledata, size: 28),
                      label: const Text('Continue with Google'),
                    ),
                    const SizedBox(height: 20),
                    Wrap(
                      alignment: WrapAlignment.center,
                      children: [
                        Text(
                          "Don't have an account? ",
                          style: Theme.of(context).textTheme.bodyMedium,
                        ),
                        GestureDetector(
                          onTap: () {
                            Navigator.of(context).push(
                              MaterialPageRoute<void>(builder: (_) => const SignUpScreen()),
                            );
                          },
                          child: Text(
                            'Sign Up',
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
