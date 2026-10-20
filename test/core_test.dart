import 'package:dinefinder/core/dinefinder_core.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('Geographic calculations', () {
    test('distance and bearing agree with known cardinal coordinates', () {
      const origin = GeoPoint(0, 0);
      const north = GeoPoint(0.001, 0);
      const east = GeoPoint(0, 0.001);
      expect(distanceMeters(origin, north), closeTo(111.195, 0.01));
      expect(distanceMeters(origin, east), closeTo(111.195, 0.01));
      expect(bearingDegrees(origin, north), closeTo(0, 0.0001));
      expect(bearingDegrees(origin, east), closeTo(90, 0.0001));
      expect(distanceMeters(origin, origin), 0);
    });

    test('uses the short path across the antimeridian', () {
      const west = GeoPoint(0, 179.999);
      const east = GeoPoint(0, -179.999);
      expect(distanceMeters(west, east), closeTo(222.39, 0.02));
      expect(bearingDegrees(west, east), closeTo(90, 0.001));
    });

    test('heading wraps north and rejects nonfinite values', () {
      expect(headingDelta(5, 355), 10);
      expect(headingDelta(355, 5), -10);
      expect(headingDelta(90, 270), -180);
      expect(normalizeAngle(-45), 315);
      expect(() => normalizeAngle(double.nan), throwsArgumentError);
    });

    test('only projects markers in front of the camera into view', () {
      final middle = projectMarker(2, 358);
      expect(middle.isVisible, isTrue);
      expect(middle.horizontal, greaterThan(0));
      final behind = projectMarker(180, 0);
      expect(behind.isVisible, isFalse);
      expect(behind.horizontal, -1);
      expect(() => projectMarker(0, 0, fieldOfView: 180), throwsArgumentError);
    });
  });

  group('Restaurant search', () {
    const repository = DemoRestaurantRepository();

    test(
      'the demo searches a real radius and orders by nearest distance',
      () async {
        final nearby = await repository.nearby(demoOrigin, 1000);
        expect(nearby, hasLength(6));
        var previous = -1.0;
        for (final restaurant in nearby) {
          final distance = distanceMeters(demoOrigin, restaurant.location);
          expect(distance, lessThanOrEqualTo(1000));
          expect(distance, greaterThanOrEqualTo(previous));
          previous = distance;
        }
        final smaller = await repository.nearby(demoOrigin, 250);
        expect(smaller.map((r) => r.name), ['Bamboo & Bowl']);
        expect(await repository.nearby(const GeoPoint(0, 0), 1000), isEmpty);
      },
    );

    test(
      'rejects an unbounded search instead of showing all restaurants',
      () async {
        await expectLater(
          repository.nearby(demoOrigin, 0),
          throwsArgumentError,
        );
        await expectLater(
          repository.nearby(demoOrigin, 10000),
          throwsArgumentError,
        );
        await expectLater(
          repository.nearby(demoOrigin, double.infinity),
          throwsArgumentError,
        );
      },
    );

    test('parses the RPC row shape and rejects malformed coordinates', () {
      final restaurant = Restaurant.fromJson({
        'id': 'fixture',
        'name': 'A restaurant',
        'latitude': 3.1491,
        'longitude': 101.7113,
        'rating': 4.8,
        'price_level': 2,
        'accent_hex': 'e6edda',
      });
      expect(restaurant.location, const GeoPoint(3.1491, 101.7113));
      expect(restaurant.priceLabel, '\$\$');
      expect(restaurant.accentHex, 0xffe6edda);
      expect(
        () => Restaurant.fromJson({
          'id': 'fixture',
          'name': 'Broken',
          'latitude': 91,
          'longitude': 0,
        }),
        throwsFormatException,
      );
    });
  });

  group('Walking route parsing', () {
    test('keeps GeoJSON longitude and latitude in the correct order', () {
      final route = RoutePlan.fromJson({
        'coordinates': [
          [101.7100, 3.1478],
          [101.7113, 3.1491],
        ],
        'distance_meters': 230,
        'duration_seconds': 180,
        'instructions': [
          {
            'point': [101.7113, 3.1491],
            'text': 'Arrive at destination',
            'distance_meters': 230,
          },
        ],
      });
      expect(route.points.first, demoOrigin);
      expect(route.points.last, const GeoPoint(3.1491, 101.7113));
      expect(route.instructions.single.point, route.points.last);
      expect(route.isDemo, isFalse);
    });

    test('rejects no-path and impossible routing responses', () {
      expect(
        () => RoutePlan.fromJson({
          'coordinates': [],
          'distance_meters': 100,
          'duration_seconds': 80,
        }),
        throwsFormatException,
      );
      expect(
        () => RoutePlan.fromJson({
          'coordinates': [
            [0, 0],
            [0, 0.001],
          ],
          'distance_meters': -1,
          'duration_seconds': 80,
        }),
        throwsFormatException,
      );
      expect(() => GeoPoint.fromJson([double.nan, 0]), throwsFormatException);
    });
  });

  group('Navigation progress', () {
    final route = RoutePlan(
      points: const [
        GeoPoint(0, 0),
        GeoPoint(0, 0.001),
        GeoPoint(0.001, 0.001),
      ],
      instructions: const [
        RouteInstruction(
          point: GeoPoint(0, 0.001),
          text: 'Turn left',
          distanceMeters: 111.195,
        ),
        RouteInstruction(
          point: GeoPoint(0.001, 0.001),
          text: 'Arrive',
          distanceMeters: 111.195,
        ),
      ],
      distanceMeters: 222.39,
      durationSeconds: 180,
    );

    test('measures remaining path through the bend, not a direct shortcut', () {
      final progress = nearestRouteProgress(const GeoPoint(0, 0.0005), route);
      expect(progress.remainingMeters, closeTo(166.793, 0.02));
      expect(progress.distanceFromRouteMeters, closeTo(0, 0.01));
      expect(progress.targetPoint, const GeoPoint(0, 0.001));
      expect(progress.instruction?.text, 'Turn left');
      expect(progress.hasArrived, isFalse);
    });

    test('flags deviation and does not silently turn it into a new path', () {
      final progress = nearestRouteProgress(
        const GeoPoint(-0.001, 0.0005),
        route,
      );
      expect(progress.isOffRoute, isTrue);
      expect(progress.distanceFromRouteMeters, closeTo(111.195, 0.02));
      expect(route.points, hasLength(3));
    });

    test('arrival requires being near the route endpoint', () {
      expect(
        nearestRouteProgress(const GeoPoint(0.001, 0.001), route).hasArrived,
        isTrue,
      );
      expect(
        nearestRouteProgress(const GeoPoint(0.0005, 0.001), route).hasArrived,
        isFalse,
      );
    });

    test('keeps the current ORS step active until its junction is reached', () {
      final orsRoute = RoutePlan(
        points: route.points,
        instructions: const [
          RouteInstruction(
            point: GeoPoint(0, 0),
            text: 'Head east',
            distanceMeters: 111.195,
          ),
          RouteInstruction(
            point: GeoPoint(0, 0.001),
            text: 'Turn left',
            distanceMeters: 111.195,
          ),
          RouteInstruction(
            point: GeoPoint(0.001, 0.001),
            text: 'Arrive',
            distanceMeters: 0,
          ),
        ],
        distanceMeters: 222.39,
        durationSeconds: 180,
      );
      expect(
        nearestRouteProgress(
          const GeoPoint(0, 0.0005),
          orsRoute,
        ).instruction?.text,
        'Head east',
      );
      expect(
        nearestRouteProgress(
          const GeoPoint(0.0005, 0.001),
          orsRoute,
        ).instruction?.text,
        'Turn left',
      );
      expect(
        nearestRouteProgress(orsRoute.points.last, orsRoute).instruction?.text,
        'Arrive',
      );
    });

    test('rejects invalid route deviation thresholds', () {
      expect(
        () => nearestRouteProgress(
          demoOrigin,
          route,
          offRouteThreshold: double.nan,
        ),
        throwsArgumentError,
      );
    });

    test(
      'demo routes are explicitly synthetic, including zero-distance trips',
      () async {
        const service = DemoRouteService();
        final path = await service.route(
          demoOrigin,
          demoRestaurants.first.location,
        );
        expect(path.isDemo, isTrue);
        expect(path.points.length, greaterThan(2));
        expect(path.distanceMeters, greaterThan(0));
        final zero = await service.route(demoOrigin, demoOrigin);
        expect(zero.distanceMeters, 0);
        expect(nearestRouteProgress(demoOrigin, zero).hasArrived, isTrue);
      },
    );
  });
}
