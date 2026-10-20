import 'dart:async';

import 'package:dinefinder/app/widgets/account_screens.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('sign in validates fields and submits credentials once', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(390, 844));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final pending = Completer<void>();
    final submissions = <(String, String, bool)>[];
    await tester.pumpWidget(
      MaterialApp(
        home: WelcomeScreen(
          onAuthenticate: (email, password, createAccount) {
            submissions.add((email, password, createAccount));
            return pending.future;
          },
          onGuest: () {},
        ),
      ),
    );
    await tester.tap(find.byKey(const ValueKey('account-continue')));
    await tester.pump();
    expect(find.text('Enter a valid email address.'), findsOneWidget);
    expect(find.text('Enter your password.'), findsOneWidget);
    expect(submissions, isEmpty);
    await tester.enterText(
      find.byKey(const ValueKey('account-email')),
      ' hello@example.com ',
    );
    await tester.enterText(
      find.byKey(const ValueKey('account-password')),
      ' correct-password ',
    );
    await tester.tap(find.byKey(const ValueKey('account-continue')));
    await tester.pump();
    expect(submissions, [('hello@example.com', ' correct-password ', false)]);
    expect(
      tester
          .widget<OutlinedButton>(find.byKey(const ValueKey('account-guest')))
          .onPressed,
      isNull,
    );
    expect(
      tester
          .widget<FilledButton>(find.byKey(const ValueKey('account-continue')))
          .onPressed,
      isNull,
    );
    pending.complete();
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  testWidgets('create account is explicit and checks password length', (
    tester,
  ) async {
    final submissions = <bool>[];
    await tester.pumpWidget(
      MaterialApp(
        home: WelcomeScreen(
          onAuthenticate: (_, _, create) async => submissions.add(create),
          onGuest: () {},
        ),
      ),
    );
    await tester.ensureVisible(find.text('New here? Create an account'));
    await tester.tap(find.text('New here? Create an account'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('account-email')),
      'hello@example.com',
    );
    await tester.enterText(
      find.byKey(const ValueKey('account-password')),
      'short',
    );
    await tester.ensureVisible(find.byKey(const ValueKey('account-continue')));
    await tester.tap(find.byKey(const ValueKey('account-continue')));
    await tester.pump();
    expect(find.text('Use at least 8 characters.'), findsOneWidget);
    expect(submissions, isEmpty);
    await tester.enterText(
      find.byKey(const ValueKey('account-password')),
      'longer-password',
    );
    await tester.tap(find.byKey(const ValueKey('account-continue')));
    await tester.pumpAndSettle();
    expect(submissions, [true]);
  });

  testWidgets('small screen supports guest entry and privacy dialog', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    var guests = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: WelcomeScreen(
          onAuthenticate: (_, _, _) async {},
          onGuest: () => guests++,
        ),
      ),
    );
    await tester.ensureVisible(find.byKey(const ValueKey('account-guest')));
    await tester.tap(find.byKey(const ValueKey('account-guest')));
    expect(guests, 1);
    await tester.ensureVisible(find.text('Privacy & data'));
    await tester.tap(find.text('Privacy & data'));
    await tester.pumpAndSettle();
    expect(
      find.textContaining('Camera images stay on your device.'),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('profile displays identity and wires all actions', (
    tester,
  ) async {
    final actions = <String>[];
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ProfileScreen(
            displayName: 'Aaaaa',
            isGuest: true,
            onSettings: () => actions.add('settings'),
            onHelp: () => actions.add('help'),
            onLogout: () => actions.add('logout'),
          ),
        ),
      ),
    );
    expect(find.text('Aaaaa'), findsOneWidget);
    expect(find.text('Guest profile'), findsOneWidget);
    await tester.tap(find.text('Settings'));
    await tester.tap(find.text('Help'));
    await tester.ensureVisible(find.text('Logout'));
    await tester.tap(find.text('Logout'));
    expect(actions, ['settings', 'help', 'logout']);
    expect(tester.takeException(), isNull);
  });
}
