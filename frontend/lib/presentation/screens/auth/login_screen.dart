import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../providers/login_controller.dart';
import '../../widgets/continue_with_google_button.dart';
import '../../widgets/login_brand_header.dart';
import '../../widgets/login_hero_graphic.dart';
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
  bool _rememberMe = true;

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
  }

  InputDecoration _fieldDecoration({
    required String hint,
    required IconData prefix,
    Widget? suffix,
  }) {
    return InputDecoration(
      hintText: hint,
      isDense: true,
      contentPadding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      prefixIcon: Icon(prefix, color: AppColors.muted, size: 18),
      prefixIconConstraints: const BoxConstraints(minWidth: 36, minHeight: 36),
      suffixIcon: suffix,
      suffixIconConstraints: const BoxConstraints(minWidth: 36, minHeight: 36),
      floatingLabelBehavior: FloatingLabelBehavior.never,
    );
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(loginControllerProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 440),
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(16, 6, 16, 8),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const LoginBrandHeader(),
                    const SizedBox(height: 4),
                    const LoginHeroGraphic(),
                    const SizedBox(height: 4),
                    const Text(
                      'Welcome Back!',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: AppColors.onBackground,
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                        height: 1.1,
                      ),
                    ),
                    const SizedBox(height: 2),
                    const Text(
                      'Login to continue to your account',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: AppColors.muted, fontSize: 12, height: 1.15),
                    ),
                    const SizedBox(height: 6),
                    Container(
                      padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
                      decoration: BoxDecoration(
                        color: const Color(0xFF000000),
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(color: AppColors.accent, width: 1.4),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          const Text(
                            'Email / Username',
                            style: TextStyle(
                              color: AppColors.onCard,
                              fontWeight: FontWeight.w600,
                              fontSize: 13,
                            ),
                          ),
                          const SizedBox(height: 4),
                          TextFormField(
                            controller: _emailController,
                            keyboardType: TextInputType.emailAddress,
                            textInputAction: TextInputAction.next,
                            autovalidateMode: AutovalidateMode.onUserInteraction,
                            style: const TextStyle(fontSize: 14),
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
                            decoration: _fieldDecoration(
                              hint: 'Enter your email or username',
                              prefix: Icons.person_outline,
                            ),
                          ),
                          const SizedBox(height: 8),
                          const Text(
                            'Password',
                            style: TextStyle(
                              color: AppColors.onCard,
                              fontWeight: FontWeight.w600,
                              fontSize: 13,
                            ),
                          ),
                          const SizedBox(height: 4),
                          TextFormField(
                            controller: _passwordController,
                            obscureText: _obscurePassword,
                            textInputAction: TextInputAction.done,
                            onFieldSubmitted: (_) => _submit(),
                            autovalidateMode: AutovalidateMode.onUserInteraction,
                            style: const TextStyle(fontSize: 14),
                            validator: (value) {
                              if (value == null || value.isEmpty) {
                                return 'Password is required';
                              }
                              return null;
                            },
                            decoration: _fieldDecoration(
                              hint: 'Enter your password',
                              prefix: Icons.lock_outline,
                              suffix: IconButton(
                                onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                                visualDensity: VisualDensity.compact,
                                padding: EdgeInsets.zero,
                                icon: Icon(
                                  _obscurePassword ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                                  color: AppColors.muted,
                                  size: 20,
                                ),
                              ),
                            ),
                          ),
                          const SizedBox(height: 4),
                          Row(
                            children: [
                              SizedBox(
                                width: 24,
                                height: 24,
                                child: Checkbox(
                                  value: _rememberMe,
                                  onChanged: (value) => setState(() => _rememberMe = value ?? false),
                                  fillColor: WidgetStateProperty.resolveWith((states) {
                                    if (states.contains(WidgetState.selected)) {
                                      return AppColors.accent;
                                    }
                                    return Colors.transparent;
                                  }),
                                  checkColor: Colors.black,
                                  side: const BorderSide(color: AppColors.accent, width: 1.4),
                                ),
                              ),
                              const SizedBox(width: 8),
                              const Expanded(
                                child: Text(
                                  'Remember Me',
                                  style: TextStyle(color: AppColors.onCard, fontSize: 13),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                              TextButton(
                                onPressed: () {
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    const SnackBar(content: Text('Forgot password will be added next')),
                                  );
                                },
                                style: TextButton.styleFrom(
                                  padding: const EdgeInsets.symmetric(horizontal: 4),
                                  minimumSize: Size.zero,
                                  tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                                  visualDensity: VisualDensity.compact,
                                ),
                                child: const Text(
                                  'Forgot Password?',
                                  style: TextStyle(color: AppColors.accent, fontWeight: FontWeight.w700, fontSize: 13),
                                ),
                              ),
                            ],
                          ),
                          if (state.error != null) ...[
                            const SizedBox(height: 4),
                            Text(
                              state.error!,
                              textAlign: TextAlign.center,
                              style: const TextStyle(color: AppColors.loss, fontSize: 13),
                            ),
                          ],
                          const SizedBox(height: 6),
                          ElevatedButton(
                            onPressed: state.loading ? null : _submit,
                            style: ElevatedButton.styleFrom(
                              backgroundColor: AppColors.accent,
                              foregroundColor: Colors.black,
                              disabledBackgroundColor: AppColors.accent.withValues(alpha: 0.5),
                              disabledForegroundColor: Colors.black,
                              minimumSize: const Size.fromHeight(40),
                              padding: const EdgeInsets.symmetric(horizontal: 12),
                            ),
                            child: state.loading
                                ? const SizedBox(
                                    height: 22,
                                    width: 22,
                                    child: CircularProgressIndicator(strokeWidth: 2, color: Colors.black),
                                  )
                                : const SizedBox(
                                    height: 24,
                                    child: Stack(
                                      alignment: Alignment.center,
                                      children: [
                                        Text('LOGIN', style: TextStyle(color: Colors.black, fontWeight: FontWeight.w800, letterSpacing: 1.2)),
                                        Align(
                                          alignment: Alignment.centerRight,
                                          child: Icon(Icons.arrow_forward, size: 18, color: Colors.black),
                                        ),
                                      ],
                                    ),
                                  ),
                          ),
                          const SizedBox(height: 6),
                          const Row(
                            children: [
                              Expanded(child: Divider(color: AppColors.muted)),
                              Padding(
                                padding: EdgeInsets.symmetric(horizontal: 12),
                                child: Text('OR', style: TextStyle(color: AppColors.muted, fontWeight: FontWeight.w700)),
                              ),
                              Expanded(child: Divider(color: AppColors.muted)),
                            ],
                          ),
                          const SizedBox(height: 6),
                          ContinueWithGoogleButton(
                            loading: state.loading,
                            onPressed: () {
                              ref.read(loginControllerProvider.notifier).signInWithGoogle();
                            },
                            onWebIdToken: (idToken) {
                              ref.read(loginControllerProvider.notifier).signInWithGoogle(idToken: idToken);
                            },
                          ),
                          const SizedBox(height: 6),
                          Wrap(
                            alignment: WrapAlignment.center,
                            children: [
                              const Text(
                                "Don't have an account? ",
                                style: TextStyle(color: AppColors.onCard, fontSize: 13),
                              ),
                              GestureDetector(
                                onTap: () {
                                  Navigator.of(context).push(
                                    MaterialPageRoute<void>(builder: (_) => const SignUpScreen()),
                                  );
                                },
                                child: const Text(
                                  'SIGN UP',
                                  style: TextStyle(
                                    color: AppColors.accent,
                                    fontWeight: FontWeight.w800,
                                    letterSpacing: 0.6,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
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
