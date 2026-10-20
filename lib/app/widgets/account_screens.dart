import 'dart:math' as math;

import 'package:flutter/material.dart';

const _accountInk = Color(0xFF1E2A25);
const _accountGreen = Color(0xFF245B50);
const _accountMint = Color(0xFFDEEBE3);
const _accountMuted = Color(0xFF768079);
const _accountPaper = Color(0xFFF7F8F2);

/// Presents account credentials without persisting them or simulating a login.
/// Authentication and guest-session state belong to the caller.
class WelcomeScreen extends StatefulWidget {
  const WelcomeScreen({
    super.key,
    required this.onAuthenticate,
    required this.onGuest,
    this.busy = false,
    this.message,
    this.messageIsError = true,
  });

  final Future<void> Function(String email, String password, bool createAccount)
  onAuthenticate;
  final VoidCallback onGuest;
  final bool busy;
  final String? message;
  final bool messageIsError;

  @override
  State<WelcomeScreen> createState() => _WelcomeScreenState();
}

class _WelcomeScreenState extends State<WelcomeScreen> {
  final _form = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  var _createAccount = false;
  var _obscurePassword = true;
  var _submitting = false;
  String? _submissionError;

  bool get _busy => widget.busy || _submitting;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy || !(_form.currentState?.validate() ?? false)) return;
    FocusScope.of(context).unfocus();
    setState(() {
      _submitting = true;
      _submissionError = null;
    });
    try {
      await widget.onAuthenticate(
        _email.text.trim(),
        _password.text,
        _createAccount,
      );
    } on Object {
      if (mounted) {
        setState(
          () => _submissionError = 'Unable to continue. Please try again.',
        );
      }
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  void _showPrivacy() {
    showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Privacy & data'),
        content: const Text(
          'Camera images stay on your device. Your location is sent to '
          'Supabase when you search nearby restaurants, and to the walking-route '
          'service when you select Navigate.\n\n'
          'Demo mode uses fictional places and simulated movement. You can '
          'revoke location and camera permissions in your device settings.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final message = _submissionError ?? widget.message;
    final isError = _submissionError != null || widget.messageIsError;
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: LayoutBuilder(
          builder: (context, bounds) => SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 26, vertical: 22),
            child: ConstrainedBox(
              constraints: BoxConstraints(
                minHeight: math.max(0, bounds.maxHeight - 44),
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const SizedBox(height: 20),
                  Center(
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 440),
                      child: Form(
                        key: _form,
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Container(
                              width: 68,
                              height: 68,
                              decoration: BoxDecoration(
                                color: _accountMint,
                                borderRadius: BorderRadius.circular(23),
                              ),
                              child: const Icon(
                                Icons.restaurant,
                                size: 34,
                                color: _accountGreen,
                              ),
                            ),
                            const SizedBox(height: 20),
                            const Text(
                              'DineFinder',
                              style: TextStyle(
                                color: _accountInk,
                                fontSize: 29,
                                fontWeight: FontWeight.w800,
                                letterSpacing: -.9,
                              ),
                            ),
                            const SizedBox(height: 12),
                            Text(
                              _createAccount
                                  ? 'Create your account to get started.'
                                  : 'Create an account or sign in to continue.',
                              textAlign: TextAlign.center,
                              style: const TextStyle(
                                color: _accountMuted,
                                fontSize: 14,
                                height: 1.5,
                              ),
                            ),
                            const SizedBox(height: 30),
                            TextFormField(
                              key: const ValueKey('account-email'),
                              controller: _email,
                              enabled: !_busy,
                              keyboardType: TextInputType.emailAddress,
                              textInputAction: TextInputAction.next,
                              autofillHints: const [AutofillHints.email],
                              autocorrect: false,
                              decoration: _fieldDecoration(
                                'Email address',
                                Icons.mail_outline_rounded,
                              ),
                              validator: (value) {
                                final email = value?.trim() ?? '';
                                if (!RegExp(
                                  r'^[^\s@]+@[^\s@]+\.[^\s@]+$',
                                ).hasMatch(email)) {
                                  return 'Enter a valid email address.';
                                }
                                return null;
                              },
                            ),
                            const SizedBox(height: 12),
                            TextFormField(
                              key: const ValueKey('account-password'),
                              controller: _password,
                              enabled: !_busy,
                              obscureText: _obscurePassword,
                              keyboardType: TextInputType.visiblePassword,
                              textInputAction: TextInputAction.done,
                              enableSuggestions: false,
                              autocorrect: false,
                              autofillHints: [
                                _createAccount
                                    ? AutofillHints.newPassword
                                    : AutofillHints.password,
                              ],
                              decoration:
                                  _fieldDecoration(
                                    'Password',
                                    Icons.lock_rounded,
                                  ).copyWith(
                                    suffixIcon: IconButton(
                                      tooltip: _obscurePassword
                                          ? 'Show password'
                                          : 'Hide password',
                                      onPressed: _busy
                                          ? null
                                          : () => setState(
                                              () => _obscurePassword =
                                                  !_obscurePassword,
                                            ),
                                      icon: Icon(
                                        _obscurePassword
                                            ? Icons.visibility_outlined
                                            : Icons.visibility_off_outlined,
                                        size: 21,
                                        color: _accountMuted,
                                      ),
                                    ),
                                  ),
                              validator: (value) {
                                if (value == null || value.isEmpty) {
                                  return 'Enter your password.';
                                }
                                if (_createAccount && value.length < 8) {
                                  return 'Use at least 8 characters.';
                                }
                                return null;
                              },
                              onFieldSubmitted: (_) => _submit(),
                            ),
                            if (message != null) ...[
                              const SizedBox(height: 14),
                              Semantics(
                                liveRegion: true,
                                child: Text(
                                  message,
                                  textAlign: TextAlign.center,
                                  style: TextStyle(
                                    color: isError
                                        ? const Color(0xFFA63E35)
                                        : _accountGreen,
                                    height: 1.5,
                                    fontSize: 12,
                                  ),
                                ),
                              ),
                            ],
                            const SizedBox(height: 18),
                            SizedBox(
                              width: double.infinity,
                              child: FilledButton(
                                key: const ValueKey('account-continue'),
                                onPressed: _busy ? null : _submit,
                                style: FilledButton.styleFrom(
                                  backgroundColor: _accountGreen,
                                  foregroundColor: Colors.white,
                                  minimumSize: const Size(0, 54),
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(16),
                                  ),
                                ),
                                child: _busy
                                    ? const SizedBox.square(
                                        dimension: 20,
                                        child: CircularProgressIndicator(
                                          strokeWidth: 2,
                                          color: Colors.white,
                                        ),
                                      )
                                    : Text(
                                        _createAccount
                                            ? 'Create account'
                                            : 'Continue',
                                        style: const TextStyle(
                                          fontWeight: FontWeight.w700,
                                        ),
                                      ),
                              ),
                            ),
                            const Padding(
                              padding: EdgeInsets.symmetric(vertical: 18),
                              child: Row(
                                children: [
                                  Expanded(child: Divider()),
                                  Padding(
                                    padding: EdgeInsets.symmetric(
                                      horizontal: 10,
                                    ),
                                    child: Text(
                                      'OR',
                                      style: TextStyle(
                                        color: _accountMuted,
                                        fontSize: 11,
                                        letterSpacing: .8,
                                      ),
                                    ),
                                  ),
                                  Expanded(child: Divider()),
                                ],
                              ),
                            ),
                            SizedBox(
                              width: double.infinity,
                              child: OutlinedButton(
                                key: const ValueKey('account-guest'),
                                onPressed: _busy ? null : widget.onGuest,
                                style: OutlinedButton.styleFrom(
                                  foregroundColor: _accountMuted,
                                  minimumSize: const Size(0, 54),
                                  side: const BorderSide(
                                    color: Color(0xFFCCD0CB),
                                  ),
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(16),
                                  ),
                                ),
                                child: const Text(
                                  'Continue as guest',
                                  style: TextStyle(fontWeight: FontWeight.w700),
                                ),
                              ),
                            ),
                            const SizedBox(height: 8),
                            TextButton(
                              onPressed: _busy
                                  ? null
                                  : () => setState(() {
                                      _createAccount = !_createAccount;
                                      _submissionError = null;
                                      _form.currentState?.reset();
                                    }),
                              child: Text(
                                _createAccount
                                    ? 'Already have an account? Sign in'
                                    : 'New here? Create an account',
                                textAlign: TextAlign.center,
                                style: const TextStyle(
                                  color: _accountGreen,
                                  fontSize: 12,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.only(top: 22),
                    child: TextButton(
                      onPressed: _showPrivacy,
                      child: const Text(
                        'Privacy & data',
                        style: TextStyle(color: _accountMuted, fontSize: 12),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  InputDecoration _fieldDecoration(String hint, IconData icon) =>
      InputDecoration(
        hintText: hint,
        hintStyle: const TextStyle(color: _accountMuted, fontSize: 16),
        filled: true,
        fillColor: const Color(0xFFEFF1EB),
        prefixIcon: Icon(icon, color: _accountMuted, size: 24),
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 20,
        ),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: Color(0xFFCCD0CB)),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: Color(0xFFCCD0CB)),
        ),
        errorMaxLines: 2,
      );
}

class ProfileScreen extends StatelessWidget {
  const ProfileScreen({
    super.key,
    required this.displayName,
    this.email,
    required this.isGuest,
    required this.onSettings,
    required this.onHelp,
    required this.onLogout,
  });

  final String displayName;
  final String? email;
  final bool isGuest;
  final VoidCallback onSettings;
  final VoidCallback onHelp;
  final VoidCallback onLogout;

  @override
  Widget build(BuildContext context) => ColoredBox(
    color: _accountPaper,
    child: ListView(
      padding: const EdgeInsets.fromLTRB(18, 28, 18, 28),
      children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 26),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(24),
          ),
          child: Column(
            children: [
              const CircleAvatar(
                radius: 35,
                backgroundColor: _accountMint,
                child: Icon(Icons.person, color: _accountGreen, size: 36),
              ),
              const SizedBox(height: 14),
              Text(
                displayName.trim().isEmpty ? 'Guest' : displayName,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  color: _accountInk,
                  fontSize: 23,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                isGuest ? 'Guest profile' : (email ?? 'DineFinder member'),
                textAlign: TextAlign.center,
                style: const TextStyle(
                  color: _accountMuted,
                  fontSize: 15,
                  height: 1.4,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        _ProfileAction(
          icon: Icons.settings,
          label: 'Settings',
          onTap: onSettings,
        ),
        const SizedBox(height: 12),
        _ProfileAction(
          icon: Icons.help_outline_rounded,
          label: 'Help',
          onTap: onHelp,
        ),
        const SizedBox(height: 12),
        _ProfileAction(
          icon: Icons.logout,
          label: 'Logout',
          onTap: onLogout,
          destructive: true,
        ),
      ],
    ),
  );
}

class _ProfileAction extends StatelessWidget {
  const _ProfileAction({
    required this.icon,
    required this.label,
    required this.onTap,
    this.destructive = false,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool destructive;

  @override
  Widget build(BuildContext context) {
    final color = destructive ? const Color(0xFFB03B31) : _accountInk;
    return Material(
      color: Colors.white,
      shape: RoundedRectangleBorder(
        side: const BorderSide(color: Color(0xFFE5E8E1)),
        borderRadius: BorderRadius.circular(18),
      ),
      clipBehavior: Clip.antiAlias,
      child: ListTile(
        onTap: onTap,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 12,
        ),
        leading: CircleAvatar(
          radius: 21,
          backgroundColor: destructive
              ? const Color(0xFFF9DEDB)
              : const Color(0xFFEEF1EA),
          child: Icon(
            icon,
            color: destructive ? color : _accountMuted,
            size: 23,
          ),
        ),
        title: Text(
          label,
          style: TextStyle(
            color: color,
            fontSize: 17,
            fontWeight: FontWeight.w700,
          ),
        ),
        trailing: const Icon(
          Icons.chevron_right,
          size: 25,
          color: Color(0xFFA1AAA3),
        ),
      ),
    );
  }
}
