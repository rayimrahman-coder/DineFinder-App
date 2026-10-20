import 'dart:async';

import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import 'widgets/account_screens.dart';

class AccountSession {
  const AccountSession({
    required this.displayName,
    required this.isGuest,
    required this.onLogout,
    this.email,
    this.onRename,
  });
  final String displayName;
  final String? email;
  final bool isGuest;
  final VoidCallback onLogout;
  final ValueChanged<String>? onRename;
}

/// Real Supabase accounts are optional. Guests never submit a fake password.
class AccountGate extends StatefulWidget {
  const AccountGate({
    super.key,
    required this.client,
    required this.builder,
    this.initialGuest = false,
  });
  final SupabaseClient? client;
  final Widget Function(AccountSession session) builder;
  final bool initialGuest;

  @override
  State<AccountGate> createState() => _AccountGateState();
}

class _AccountGateState extends State<AccountGate> {
  StreamSubscription<AuthState>? _subscription;
  User? _user;
  late bool _guest;
  var _guestName = 'Guest';
  var _busy = false;
  String? _message;
  var _messageIsError = true;
  var _routeCleanupScheduled = false;

  @override
  void initState() {
    super.initState();
    _guest = widget.initialGuest;
    _user = widget.client?.auth.currentUser;
    _subscription = widget.client?.auth.onAuthStateChange.listen(
      (event) {
        if (!mounted) return;
        final nextUser = event.session?.user;
        if (_user?.id != nextUser?.id) _closeTransientRoutes();
        setState(() => _user = nextUser);
      },
      onError: (Object error) {
        if (!mounted) return;
        const message = 'Account connection interrupted. Please try again.';
        setState(() {
          _message = message;
          _messageIsError = true;
        });
        if (_user != null || _guest) {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(const SnackBar(content: Text(message)));
        }
      },
    );
  }

  void _closeTransientRoutes() {
    if (!mounted || _routeCleanupScheduled) return;
    _routeCleanupScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _routeCleanupScheduled = false;
      if (mounted) Navigator.of(context).popUntil((route) => route.isFirst);
    });
  }

  @override
  void dispose() {
    unawaited(_subscription?.cancel());
    super.dispose();
  }

  Future<void> _authenticate(
    String email,
    String password,
    bool createAccount,
  ) async {
    final client = widget.client;
    if (client == null) {
      setState(() {
        _message =
            'Account sign-in is not connected in this build. You can continue as a guest.';
        _messageIsError = true;
      });
      return;
    }
    setState(() {
      _busy = true;
      _message = null;
    });
    try {
      if (createAccount) {
        final response = await client.auth.signUp(
          email: email.trim(),
          password: password,
          data: {'display_name': email.trim().split('@').first},
        );
        if (!mounted) return;
        if (response.session == null) {
          setState(() {
            _message =
                'Check your email to confirm your account, then sign in.';
            _messageIsError = false;
          });
        } else {
          _closeTransientRoutes();
          setState(() {
            _user = response.user;
            _guest = false;
          });
        }
      } else {
        final response = await client.auth.signInWithPassword(
          email: email.trim(),
          password: password,
        );
        if (!mounted) return;
        _closeTransientRoutes();
        setState(() {
          _user = response.user;
          _guest = false;
        });
      }
    } on AuthException catch (error) {
      if (!mounted) return;
      setState(() {
        _message = error.message;
        _messageIsError = true;
      });
    } on Object {
      if (!mounted) return;
      setState(() {
        _message =
            'Couldn’t connect to your account. Check your internet connection and try again.';
        _messageIsError = true;
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _logout() async {
    try {
      if (_user != null) await widget.client?.auth.signOut();
      if (!mounted) return;
      _closeTransientRoutes();
      setState(() {
        _guest = false;
        _user = null;
        _message = null;
        _guestName = 'Guest';
      });
    } on Object {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Couldn’t sign out. Please try again.')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final user = _user;
    if (!_guest && user == null) {
      return WelcomeScreen(
        onAuthenticate: _authenticate,
        busy: _busy,
        message: _message,
        messageIsError: _messageIsError,
        onGuest: () => setState(() {
          _guest = true;
          _message = null;
        }),
      );
    }
    final metadataName = user?.userMetadata?['display_name'];
    final name = user == null
        ? _guestName
        : metadataName is String && metadataName.trim().isNotEmpty
        ? metadataName.trim()
        : (user.email?.split('@').first ?? 'Diner');
    return widget.builder(
      AccountSession(
        displayName: name,
        email: user?.email,
        isGuest: user == null,
        onLogout: () => unawaited(_logout()),
        onRename: user == null
            ? (name) {
                final trimmed = name.trim();
                if (trimmed.isNotEmpty) setState(() => _guestName = trimmed);
              }
            : null,
      ),
    );
  }
}
