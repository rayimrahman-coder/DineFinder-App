import 'dart:async';

import 'package:dinefinder/app/dinefinder_app.dart';
import 'package:dinefinder/app/widgets/discovery_widgets.dart';
import 'package:dinefinder/core/dinefinder_core.dart';
import 'package:dinefinder/device/device_session.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

const _scanKey = ValueKey('scan-restaurants');
const _guestKey = ValueKey('account-guest');

Future<void> _surface(WidgetTester tester, Size size) async {
  await tester.binding.setSurfaceSize(size);
  addTearDown(() => tester.binding.setSurfaceSize(null));
}

Future<void> _guest(WidgetTester tester) async {
  await tester.ensureVisible(find.byKey(_guestKey));
  await tester.tap(find.byKey(_guestKey));
  await tester.pumpAndSettle();
}

Future<void> _scanDemo(WidgetTester tester) async {
  await tester.ensureVisible(find.byKey(_scanKey));
  await tester.tap(find.byKey(_scanKey));
  await tester.pumpAndSettle();
}

Future<void> _scanLive(WidgetTester tester) async {
  await tester.ensureVisible(find.byKey(_scanKey));
  await tester.tap(find.byKey(_scanKey));
  await tester.pumpAndSettle();
  expect(find.text('Find restaurants around you'), findsOneWidget);
  await tester.tap(find.widgetWithText(FilledButton, 'Continue'));
  await tester.pumpAndSettle();
}

class _Device extends DeviceSession {
  _Device({this.fix = demoOrigin, this.bearing = 45});

  final GeoPoint fix;
  final double bearing;
  var starts = 0;
  var stops = 0;
  var refreshes = 0;
  var disposed = false;

  void publishFix() {
    position = fix;
    heading = bearing;
    accuracyMeters = 4;
    lastFixAt = DateTime.now();
    cameraError = 'No camera is attached to this test device.';
    notifyListeners();
  }

  @override
  Future<void> start() async {
    starts++;
    publishFix();
  }

  @override
  Future<void> refreshPosition() async {
    refreshes++;
    publishFix();
  }

  @override
  Future<void> stop() async {
    stops++;
    position = null;
    heading = null;
    lastFixAt = null;
    notifyListeners();
  }

  @override
  void dispose() {
    disposed = true;
    super.dispose();
  }
}

class _Restaurants implements RestaurantRepository {
  _Restaurants({this.pending});

  final Completer<List<Restaurant>>? pending;
  final centers = <GeoPoint>[];
  final radii = <double>[];

  @override
  Future<List<Restaurant>> nearby(GeoPoint center, double radiusMeters) async {
    centers.add(center);
    radii.add(radiusMeters);
    if (pending != null) return pending!.future;
    return [demoRestaurants.first];
  }
}

class _Routes implements RouteService {
  _Routes({this.pending});

  final Completer<RoutePlan>? pending;
  final starts = <GeoPoint>[];
  final destinations = <GeoPoint>[];

  @override
  Future<RoutePlan> route(GeoPoint from, GeoPoint to) async {
    starts.add(from);
    destinations.add(to);
    if (pending != null) return pending!.future;
    return _walkingRoute(from, to);
  }
}

RoutePlan _walkingRoute(GeoPoint from, GeoPoint to) {
  final bend = GeoPoint(to.latitude, from.longitude);
  final distance = distanceMeters(from, bend) + distanceMeters(bend, to);
  return RoutePlan(
    points: [from, bend, to],
    instructions: [
      RouteInstruction(
        point: from,
        text: 'Head north along the walking route',
        distanceMeters: distanceMeters(from, bend),
      ),
      RouteInstruction(
        point: to,
        text: 'Arrive at the restaurant',
        distanceMeters: 0,
      ),
    ],
    distanceMeters: distance,
    durationSeconds: distance / 1.25,
  );
}

void main() {
  testWidgets('welcome offers a real guest path without pretending to sign in', (
    tester,
  ) async {
    await _surface(tester, const Size(390, 844));
    await tester.pumpWidget(const DineFinderApp());
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('account-email')), findsOneWidget);
    expect(find.byKey(_scanKey), findsNothing);

    await tester.enterText(
      find.byKey(const ValueKey('account-email')),
      'diner@example.com',
    );
    await tester.enterText(
      find.byKey(const ValueKey('account-password')),
      'sample-password',
    );
    await tester.ensureVisible(find.byKey(const ValueKey('account-continue')));
    await tester.tap(find.byKey(const ValueKey('account-continue')));
    await tester.pumpAndSettle();
    expect(
      find.text(
        'Account sign-in is not connected in this build. You can continue as a guest.',
      ),
      findsOneWidget,
    );
    expect(find.byKey(_scanKey), findsNothing);

    await _guest(tester);
    expect(find.text('Hello, Guest'), findsOneWidget);
    expect(find.byKey(_scanKey), findsOneWidget);
    expect(find.text('Home'), findsOneWidget);
    expect(find.text('Profile'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('profile settings rename the guest and Help and Logout work', (
    tester,
  ) async {
    await _surface(tester, const Size(390, 844));
    await tester.pumpWidget(const DineFinderApp(startAsGuest: true));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Profile'));
    await tester.pumpAndSettle();
    expect(find.text('Guest profile'), findsOneWidget);

    await tester.tap(find.text('Settings'));
    await tester.pumpAndSettle();
    expect(find.text('1 km around your GPS location'), findsOneWidget);
    await tester.tap(find.text('Change guest name'));
    await tester.pumpAndSettle();
    expect(find.text('Your guest name'), findsOneWidget);
    await tester.enterText(find.byType(TextField), 'Mira');
    await tester.tap(find.text('Save'));
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pumpAndSettle();
    expect(find.text('Mira'), findsOneWidget);

    await tester.tap(find.text('Help'));
    await tester.pumpAndSettle();
    expect(find.text('How DineFinder works'), findsOneWidget);
    await tester.tap(find.text('Got it'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Home'));
    await tester.pumpAndSettle();
    expect(find.text('Hello, Mira'), findsOneWidget);
    await tester.tap(find.text('Profile'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Logout'));
    await tester.pumpAndSettle();
    expect(find.byKey(_guestKey), findsOneWidget);
    expect(find.byKey(_scanKey), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'Scan opens demo camera and fixed-radius restaurant search works',
    (tester) async {
      await _surface(tester, const Size(1280, 1000));
      await tester.pumpWidget(const DineFinderApp(startAsGuest: true));
      await tester.pumpAndSettle();
      expect(find.byType(RestaurantCard), findsNothing);
      await _scanDemo(tester);
      expect(find.text('SIMULATED VIEW'), findsOneWidget);
      expect(find.text('Restaurants within 1 km'), findsOneWidget);

      await tester.tap(find.byTooltip('Restaurant list'));
      await tester.pumpAndSettle();
      expect(find.byType(RestaurantCard), findsNWidgets(6));
      expect(find.text('1 km'), findsWidgets);
      expect(find.byType(DropdownButton<double>), findsNothing);
      await tester.enterText(find.byType(TextField), 'not-a-real-place');
      await tester.pumpAndSettle();
      expect(find.byType(RestaurantCard), findsNothing);
      await tester.tap(find.text('Reset filters'));
      await tester.pumpAndSettle();
      expect(find.byType(RestaurantCard), findsNWidgets(6));
      expect(find.text('Restaurants within 1 km'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'a demo restaurant profile navigates through arrival back to Home',
    (tester) async {
      await _surface(tester, const Size(1280, 1000));
      await tester.pumpWidget(const DineFinderApp(startAsGuest: true));
      await tester.pumpAndSettle();
      await _scanDemo(tester);
      await tester.tap(find.byTooltip('Restaurant list'));
      await tester.pumpAndSettle();
      await tester.tap(find.byType(RestaurantCard).first);
      await tester.pumpAndSettle();
      expect(
        find.text('Sample restaurant · fictional listing for the demo'),
        findsOneWidget,
      );
      await tester.ensureVisible(find.text('Navigate'));
      await tester.tap(find.text('Navigate'));
      await tester.pumpAndSettle();
      expect(find.text('YOUR WALKING DIRECTIONS'), findsOneWidget);
      expect(find.text('SIMULATED VIEW'), findsOneWidget);
      final walk = tester.widget<Slider>(
        find.byKey(const ValueKey('demo-walk')),
      );
      walk.onChanged!(1);
      await tester.pumpAndSettle();
      expect(find.text('YOU’VE ARRIVED'), findsOneWidget);
      await tester.ensureVisible(find.text('Finish journey'));
      await tester.tap(find.text('Finish journey'));
      await tester.pumpAndSettle();
      expect(find.text('Hello, Guest'), findsOneWidget);
      expect(find.byKey(_scanKey), findsOneWidget);
      expect(find.text('YOUR WALKING DIRECTIONS'), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'live scanning waits for consent and queries exact GPS within 1 km',
    (tester) async {
      await _surface(tester, const Size(1280, 1000));
      final device = _Device(
        fix: const GeoPoint(3.1480, 101.7102),
        bearing: 46,
      );
      final restaurants = _Restaurants();
      final routes = _Routes();
      await tester.pumpWidget(
        DineFinderApp(
          startAsGuest: true,
          deviceFactory: () => device,
          liveRepository: restaurants,
          liveRouteService: routes,
        ),
      );
      await tester.pumpAndSettle();
      expect(device.starts, 0);
      expect(restaurants.centers, isEmpty);
      await tester.tap(find.byKey(_scanKey));
      await tester.pumpAndSettle();
      expect(device.starts, 0);
      await tester.tap(find.text('Later'));
      await tester.pumpAndSettle();
      expect(restaurants.centers, isEmpty);
      expect(find.byKey(_scanKey), findsOneWidget);

      await _scanLive(tester);
      expect(device.starts, 1);
      expect(restaurants.centers, [device.fix]);
      expect(restaurants.radii, [1000.0]);
      expect(find.text('SIMULATED VIEW'), findsNothing);
      expect(find.text('Bamboo & Bowl'), findsOneWidget);
      await tester.tap(find.text('Bamboo & Bowl'));
      await tester.pumpAndSettle();
      expect(find.text(demoRestaurants.first.description), findsOneWidget);
      expect(find.text(demoRestaurants.first.address), findsOneWidget);
      await tester.ensureVisible(find.text('Navigate'));
      await tester.tap(find.text('Navigate'));
      await tester.pumpAndSettle();
      expect(routes.starts, [device.fix]);
      expect(routes.destinations, [demoRestaurants.first.location]);
      expect(find.text('YOUR WALKING DIRECTIONS'), findsOneWidget);
      expect(find.byKey(const ValueKey('demo-walk')), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('closing Scan discards a pending route and releases sensors', (
    tester,
  ) async {
    await _surface(tester, const Size(1280, 1000));
    final device = _Device();
    final pending = Completer<RoutePlan>();
    final routes = _Routes(pending: pending);
    await tester.pumpWidget(
      DineFinderApp(
        startAsGuest: true,
        deviceFactory: () => device,
        liveRepository: _Restaurants(),
        liveRouteService: routes,
      ),
    );
    await tester.pumpAndSettle();
    await _scanLive(tester);
    await tester.tap(find.text('Bamboo & Bowl'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Navigate'));
    await tester.tap(find.text('Navigate'));
    await tester.pump(const Duration(milliseconds: 500));
    expect(routes.starts, hasLength(1));
    await tester.tap(find.byTooltip('Back to Home'));
    await tester.pumpAndSettle();
    expect(device.stops, 1);
    pending.complete(_walkingRoute(demoOrigin, demoRestaurants.first.location));
    await tester.pumpAndSettle();
    expect(find.byKey(_scanKey), findsOneWidget);
    expect(find.text('YOUR WALKING DIRECTIONS'), findsNothing);
    expect(
      tester.widget<FilledButton>(find.byKey(_scanKey)).onPressed,
      isNotNull,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'a nearby query completing after logout cannot revive discovery',
    (tester) async {
      await _surface(tester, const Size(1280, 1000));
      final device = _Device();
      final pending = Completer<List<Restaurant>>();
      final restaurants = _Restaurants(pending: pending);
      await tester.pumpWidget(
        DineFinderApp(
          startAsGuest: true,
          deviceFactory: () => device,
          liveRepository: restaurants,
          liveRouteService: _Routes(),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(_scanKey));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Continue'));
      await tester.pump(const Duration(milliseconds: 300));
      expect(restaurants.centers, [demoOrigin]);
      await tester.tap(find.byTooltip('Back to Home'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Profile'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Logout'));
      await tester.pumpAndSettle();
      expect(device.disposed, isTrue);
      pending.complete([demoRestaurants.first]);
      await tester.pumpAndSettle();
      expect(find.byKey(_guestKey), findsOneWidget);
      expect(find.byType(RestaurantCard), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  for (final size in [const Size(390, 844), const Size(320, 700)]) {
    testWidgets(
      'welcome, Home, scan and Profile fit a ${size.width.toInt()} px phone',
      (tester) async {
        await _surface(tester, size);
        await tester.pumpWidget(const DineFinderApp());
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        await _guest(tester);
        expect(tester.takeException(), isNull);
        await _scanDemo(tester);
        expect(find.text('SIMULATED VIEW'), findsOneWidget);
        expect(tester.takeException(), isNull);
        await tester.tap(find.byTooltip('Restaurant list'));
        await tester.pumpAndSettle();
        expect(find.byType(RestaurantCard), findsNWidgets(6));
        expect(tester.takeException(), isNull);
        await tester.tap(find.byTooltip('Back to Home'));
        await tester.pumpAndSettle();
        await tester.tap(find.text('Profile'));
        await tester.pumpAndSettle();
        expect(find.text('Settings'), findsOneWidget);
        expect(find.text('Help'), findsOneWidget);
        expect(find.text('Logout'), findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );
  }
}
