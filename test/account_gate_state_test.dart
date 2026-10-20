import 'dart:convert';

import 'package:dinefinder/app/dinefinder_app.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

/// An authenticated fixture only. No credentials or network connection are used.
Future<SupabaseClient> _signedInClient() async {
  final client = SupabaseClient(
    'https://example.invalid',
    'fixture-public-key',
    authOptions: const AuthClientOptions(autoRefreshToken: false),
    httpClient: MockClient((_) async => http.Response('', 204)),
  );
  await client.auth.setInitialSession(
    jsonEncode({
      'access_token': 'fixture-token',
      'refresh_token': 'fixture-refresh',
      'token_type': 'bearer',
      'user': {
        'id': 'fixture-user',
        'app_metadata': <String, Object?>{},
        'user_metadata': {'display_name': 'Mira'},
        'aud': 'authenticated',
        'created_at': '2026-01-01T00:00:00Z',
        'email': 'mira@example.invalid',
      },
    }),
  );
  return client;
}

void main() {
  for (final modal in ['Settings', 'Help']) {
    testWidgets('auth loss dismisses an open $modal before showing sign in', (
      tester,
    ) async {
      await tester.binding.setSurfaceSize(const Size(390, 844));
      // Supabase initializes a JSON worker isolate. Create it in the real
      // async zone so its native startup messages can finish during teardown.
      final client = (await tester.runAsync(_signedInClient))!;
      addTearDown(() async {
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pump();
        await tester.runAsync(client.dispose);
        await tester.binding.setSurfaceSize(null);
      });
      await tester.pumpWidget(DineFinderApp(client: client));
      await tester.pumpAndSettle();
      expect(find.text('Hello, Mira'), findsOneWidget);
      await tester.tap(find.text('Profile'));
      await tester.pumpAndSettle();
      await tester.tap(find.text(modal));
      await tester.pumpAndSettle();
      if (modal == 'Settings') {
        expect(find.text('Use demo mode'), findsOneWidget);
      } else {
        expect(find.text('How DineFinder works'), findsOneWidget);
      }

      // Simulates a session expiring or being revoked outside the Profile UI.
      // Local sign-out still travels through the SDK's real AuthState stream.
      await client.auth.signOut(scope: SignOutScope.local);
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('account-email')), findsOneWidget);
      expect(find.text('Use demo mode'), findsNothing);
      expect(find.text('How DineFinder works'), findsNothing);
      expect(find.text('Profile'), findsNothing);
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('auth stream errors show a message without losing the session', (
    tester,
  ) async {
    final client = (await tester.runAsync(_signedInClient))!;
    addTearDown(() async {
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump();
      await tester.runAsync(client.dispose);
    });
    await tester.pumpWidget(DineFinderApp(client: client));
    await tester.pumpAndSettle();

    // Exercise the actual SDK stream's error channel deterministically. The
    // internal notifier is used only to inject a fixture error in this test.
    // ignore: invalid_use_of_internal_member
    client.auth.notifyException(
      const AuthException('Fixture connection error'),
    );
    await tester.pumpAndSettle();
    expect(
      find.text('Account connection interrupted. Please try again.'),
      findsOneWidget,
    );
    expect(find.text('Hello, Mira'), findsOneWidget);
    expect(find.byKey(const ValueKey('account-email')), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
