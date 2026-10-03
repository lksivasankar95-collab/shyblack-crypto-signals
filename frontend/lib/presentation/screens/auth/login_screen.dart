import 'dart:ui' show ImageFilter;

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../providers/auth_form_state.dart';
import '../../providers/login_controller.dart';
import '../../widgets/continue_with_google_button.dart';
import '../../widgets/login_hero_graphic.dart';
import 'sign_up_screen.dart';

/// Login screen — premium dark crypto-finance design.
///
/// UI only: authentication, validation, loading/error state, Remember Me,
/// Forgot Password, Create Account, Google sign-in and post-login redirect are
/// all unchanged (they live in [loginControllerProvider] / [SignUpScreen]).
class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key, this.successMessage});

  final String? successMessage;

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  static const String _logoAsset = 'assets/images/logo.png';

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
    final ok = await ref
        .read(loginControllerProvider.notifier)
        .submit(
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
        child: LayoutBuilder(
          builder: (context, constraints) {
            final isDesktop = constraints.maxWidth >= 1000;
            if (isDesktop) {
              return Row(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Expanded(child: _BrandPanel()),
                  SizedBox(
                    width: 500,
                    child: Center(
                      child: SingleChildScrollView(
                        padding: const EdgeInsets.fromLTRB(24, 24, 24, 24),
                        child: ConstrainedBox(
                          constraints: const BoxConstraints(maxWidth: 440),
                          child: Form(
                            key: _formKey,
                            child: _buildForm(state, compact: false),
                          ),
                        ),
                      ),
                    ),
                  ),
                ],
              );
            }
            // Single-column (mobile / tablet): compact branding, no oversized art.
            return Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 460),
                child: SingleChildScrollView(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
                  child: Form(
                    key: _formKey,
                    child: _buildForm(state, compact: true),
                  ),
                ),
              ),
            );
          },
        ),
      ),
    );
  }

  Widget _buildForm(AuthFormState state, {required bool compact}) {
    return _GlassCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
            Center(child: _Logo(assetPath: _logoAsset, height: compact ? 48 : 56)),
            SizedBox(height: compact ? 8 : 12),
            const Text(
              'Welcome Back',
              textAlign: TextAlign.center,
              style: TextStyle(
                color: AppColors.onBackground,
                fontSize: 22,
                fontWeight: FontWeight.w800,
                height: 1.1,
              ),
            ),
            const SizedBox(height: 4),
            const Text(
              'Sign in to continue to ShyBlack Crypto Signals',
              textAlign: TextAlign.center,
              style: TextStyle(color: AppColors.muted, fontSize: 12, height: 1.25),
            ),
            SizedBox(height: compact ? 12 : 16),

            // Email
            const _FieldLabel('Email Address'),
            const SizedBox(height: 6),
            Semantics(
              textField: true,
              label: 'Email Address',
              child: TextFormField(
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
                  final valid = RegExp(
                    r'^[^@\s]+@[^@\s]+\.[^@\s]+$',
                  ).hasMatch(email);
                  if (!valid) {
                    return 'Enter a valid email';
                  }
                  return null;
                },
                decoration: _fieldDecoration(
                  hint: 'Enter your email address',
                  prefix: Icons.mail_outline,
                ),
              ),
            ),
            const SizedBox(height: 12),

            // Password
            const _FieldLabel('Password'),
            const SizedBox(height: 6),
            Semantics(
              textField: true,
              label: 'Password',
              child: TextFormField(
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
                    tooltip: _obscurePassword ? 'Show password' : 'Hide password',
                    onPressed: () => setState(
                      () => _obscurePassword = !_obscurePassword,
                    ),
                    visualDensity: VisualDensity.compact,
                    padding: EdgeInsets.zero,
                    icon: Icon(
                      _obscurePassword
                          ? Icons.visibility_outlined
                          : Icons.visibility_off_outlined,
                      color: AppColors.muted,
                      size: 20,
                    ),
                  ),
                ),
              ),
            ),
            const SizedBox(height: 8),

            // Remember Me / Forgot Password
            Row(
              children: [
                SizedBox(
                  width: 24,
                  height: 24,
                  child: Checkbox(
                    value: _rememberMe,
                    onChanged: (value) =>
                        setState(() => _rememberMe = value ?? false),
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
                      const SnackBar(
                        content: Text('Forgot password will be added next'),
                      ),
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
                    style: TextStyle(
                      color: AppColors.accent,
                      fontWeight: FontWeight.w700,
                      fontSize: 13,
                    ),
                  ),
                ),
              ],
            ),

            if (state.error != null) ...[
              const SizedBox(height: 8),
              _ErrorAlert(message: state.error!),
            ],
            const SizedBox(height: 8),

            // Sign In
            ElevatedButton(
              onPressed: state.loading ? null : _submit,
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.accent,
                foregroundColor: Colors.black,
                disabledBackgroundColor: AppColors.accent.withValues(alpha: 0.5),
                disabledForegroundColor: Colors.black,
                minimumSize: const Size.fromHeight(48),
                padding: const EdgeInsets.symmetric(horizontal: 12),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              child: state.loading
                  ? const SizedBox(
                      height: 22,
                      width: 22,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: Colors.black,
                      ),
                    )
                  : const Text(
                      'Sign In',
                      style: TextStyle(
                        color: Colors.black,
                        fontWeight: FontWeight.w800,
                        letterSpacing: 0.4,
                      ),
                    ),
            ),
            const SizedBox(height: 12),

            const Row(
              children: [
                Expanded(child: Divider(color: Color(0xFF2A2A2A))),
                Padding(
                  padding: EdgeInsets.symmetric(horizontal: 12),
                  child: Text(
                    'OR',
                    style: TextStyle(
                      color: AppColors.muted,
                      fontWeight: FontWeight.w700,
                      fontSize: 12,
                    ),
                  ),
                ),
                Expanded(child: Divider(color: Color(0xFF2A2A2A))),
              ],
            ),
            const SizedBox(height: 12),

            ContinueWithGoogleButton(
              loading: state.loading,
              onPressed: () {
                ref.read(loginControllerProvider.notifier).signInWithGoogle();
              },
              onWebIdToken: (idToken) {
                ref
                    .read(loginControllerProvider.notifier)
                    .signInWithGoogle(idToken: idToken);
              },
            ),
            const SizedBox(height: 12),

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
                      MaterialPageRoute<void>(
                        builder: (_) => const SignUpScreen(),
                      ),
                    );
                  },
                  child: const Text(
                    'Create Account',
                    style: TextStyle(
                      color: AppColors.accent,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            const _LegalFooter(),
          ],
        ),
      );
  }
}

// ─────────────────────────────────────────────────────────────────────────
// Presentational pieces (UI only)
// ─────────────────────────────────────────────────────────────────────────

class _FieldLabel extends StatelessWidget {
  const _FieldLabel(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      style: const TextStyle(
        color: AppColors.onCard,
        fontWeight: FontWeight.w600,
        fontSize: 13,
      ),
    );
  }
}

class _Logo extends StatelessWidget {
  const _Logo({required this.assetPath, required this.height});

  final String assetPath;
  final double height;

  @override
  Widget build(BuildContext context) {
    return Image.asset(
      assetPath,
      height: height,
      fit: BoxFit.contain,
      filterQuality: FilterQuality.high,
      errorBuilder: (_, _, _) => _logoFallback(height),
    );
  }

  Widget _logoFallback(double size) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(size * 0.28),
        border: Border.all(color: AppColors.accent, width: 1.4),
      ),
      child: Icon(Icons.bolt_rounded, color: AppColors.accent, size: size * 0.6),
    );
  }
}

class _ErrorAlert extends StatelessWidget {
  const _ErrorAlert({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      liveRegion: true,
      label: message,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          color: AppColors.loss.withValues(alpha: 0.10),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: AppColors.loss.withValues(alpha: 0.5)),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Icon(Icons.error_outline, color: AppColors.loss, size: 18),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                message, // already user-safe: comes from AuthException/AuthFormState
                style: const TextStyle(color: AppColors.loss, fontSize: 13, height: 1.25),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _GlassCard extends StatelessWidget {
  const _GlassCard({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(20);
    return Container(
      decoration: BoxDecoration(
        borderRadius: radius,
        boxShadow: [
          BoxShadow(
            color: AppColors.accent.withValues(alpha: 0.08),
            blurRadius: 32,
            spreadRadius: -6,
          ),
          const BoxShadow(
            color: Color(0x99000000),
            blurRadius: 24,
            offset: Offset(0, 10),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: radius,
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 12, sigmaY: 12),
          child: Container(
            padding: const EdgeInsets.fromLTRB(18, 20, 18, 18),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [
                  AppColors.card.withValues(alpha: 0.92),
                  const Color(0xFF101010).withValues(alpha: 0.92),
                ],
              ),
              borderRadius: radius,
              border: Border.all(
                color: AppColors.accent.withValues(alpha: 0.35),
                width: 1.1,
              ),
            ),
            child: child,
          ),
        ),
      ),
    );
  }
}

class _BrandPanel extends StatelessWidget {
  const _BrandPanel();

  @override
  Widget build(BuildContext context) {
    return Stack(
      fit: StackFit.expand,
      children: [
        // Subtle candlestick backdrop (reuses the existing painter — no new asset).
        const Opacity(
          opacity: 0.10,
          child: CustomPaint(painter: LoginBullPlaceholderPainter()),
        ),
        // Green glow accents.
        Positioned(
          left: -80,
          top: -60,
          child: _Glow(size: 320, color: AppColors.accent.withValues(alpha: 0.10)),
        ),
        Positioned(
          right: -60,
          bottom: -80,
          child: _Glow(size: 280, color: AppColors.accent.withValues(alpha: 0.07)),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(56, 48, 40, 48),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const _Logo(assetPath: 'assets/images/logo.png', height: 72),
              const SizedBox(height: 32),
              Text.rich(
                TextSpan(
                  children: [
                    const TextSpan(text: 'Smarter\n'),
                    TextSpan(
                      text: 'Crypto Trading',
                      style: TextStyle(color: AppColors.accent),
                    ),
                    const TextSpan(text: '\nStarts Here'),
                  ],
                ),
                style: const TextStyle(
                  color: AppColors.onBackground,
                  fontSize: 42,
                  fontWeight: FontWeight.w800,
                  height: 1.08,
                  letterSpacing: -0.5,
                ),
              ),
              const SizedBox(height: 20),
              const Text(
                'Real-time signals • Advanced strategies\nNews intelligence • Trade with confidence',
                style: TextStyle(
                  color: AppColors.muted,
                  fontSize: 15,
                  height: 1.5,
                ),
              ),
              const SizedBox(height: 36),
              const Wrap(
                spacing: 28,
                runSpacing: 24,
                children: [
                  _Feature(
                    icon: Icons.bolt_rounded,
                    title: 'Real-Time Signals',
                    description: 'Live BUY/SELL setups as markets move.',
                  ),
                  _Feature(
                    icon: Icons.candlestick_chart_rounded,
                    title: 'Advanced Strategies',
                    description: 'Trend, pullback & momentum engines.',
                  ),
                  _Feature(
                    icon: Icons.newspaper_rounded,
                    title: 'Market News',
                    description: 'Real-time news intelligence feed.',
                  ),
                  _Feature(
                    icon: Icons.verified_user_rounded,
                    title: 'Secure & Reliable',
                    description: 'Encrypted keys, guarded risk limits.',
                  ),
                ],
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _Glow extends StatelessWidget {
  const _Glow({required this.size, required this.color});

  final double size;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      child: Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          gradient: RadialGradient(colors: [color, color.withValues(alpha: 0)]),
        ),
      ),
    );
  }
}

class _Feature extends StatelessWidget {
  const _Feature({
    required this.icon,
    required this.title,
    required this.description,
  });

  final IconData icon;
  final String title;
  final String description;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 250,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              color: AppColors.accent.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: AppColors.accent.withValues(alpha: 0.35)),
            ),
            child: Icon(icon, color: AppColors.accent, size: 20),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    color: AppColors.onBackground,
                    fontWeight: FontWeight.w700,
                    fontSize: 14,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  description,
                  style: const TextStyle(
                    color: AppColors.muted,
                    fontSize: 12,
                    height: 1.3,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _LegalFooter extends StatelessWidget {
  const _LegalFooter();

  void _show(BuildContext context, String title) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(title)));
  }

  @override
  Widget build(BuildContext context) {
    final accent = AppColors.accent;
    return Text.rich(
      TextSpan(
        text: 'By continuing, you agree to our ',
        style: const TextStyle(
          color: AppColors.muted,
          fontSize: 11,
          height: 1.35,
        ),
        children: [
          TextSpan(
            text: 'Terms of Service',
            style: TextStyle(
              color: accent,
              decoration: TextDecoration.underline,
              decorationColor: accent,
            ),
            recognizer: TapGestureRecognizer()
              ..onTap = () => _show(context, 'Terms of Service'),
          ),
          const TextSpan(text: ' and '),
          TextSpan(
            text: 'Privacy Policy.',
            style: TextStyle(
              color: accent,
              decoration: TextDecoration.underline,
              decorationColor: accent,
            ),
            recognizer: TapGestureRecognizer()
              ..onTap = () => _show(context, 'Privacy Policy'),
          ),
        ],
      ),
      textAlign: TextAlign.center,
    );
  }
}
