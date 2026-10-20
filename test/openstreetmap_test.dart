import 'dart:async';
import 'dart:convert';

import 'package:dinefinder/core/dinefinder_core.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

const _from = GeoPoint(3.1478, 101.7100);
const _to = GeoPoint(3.1491, 101.7113);

Map<String, Object?> _response() => {
  'code': 'Ok',
  'routes': [
    {
      'distance': 289.0,
      'duration': 240.0,
      'geometry': {
        'type': 'LineString',
        'coordinates': [
          [101.7100, 3.1478],
          [101.7100, 3.1491],
          [101.7113, 3.1491],
        ],
      },
      'legs': [
        {
          'steps': [
            {
              'name': 'Jalan Pertama',
              'distance': 145.0,
              'maneuver': {
                'type': 'depart',
                'bearing_after': 0,
                'location': [101.7100, 3.1478],
              },
            },
            {
              'name': 'Jalan Kedua',
              'distance': 144.0,
              'maneuver': {
                'type': 'turn',
                'modifier': 'right',
                'location': [101.7100, 3.1491],
              },
            },
            {
              'name': '',
              'distance': 0,
              'maneuver': {
                'type': 'arrive',
                'modifier': 'left',
                'location': [101.7113, 3.1491],
              },
            },
          ],
        },
      ],
    },
  ],
};

void main() {
  test(
    'requests the pedestrian graph with longitude first and no key headers',
    () async {
      final client = MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.scheme, 'https');
        expect(request.url.host, 'routing.openstreetmap.de');
        expect(
          request.url.path,
          '/routed-foot/route/v1/foot/101.7100000,3.1478000;101.7113000,3.1491000',
        );
        expect(request.url.queryParameters, {
          'overview': 'full',
          'geometries': 'geojson',
          'steps': 'true',
          'alternatives': 'false',
          'radiuses': '100;100',
        });
        expect(
          request.headers.keys.map((key) => key.toLowerCase()),
          isNot(contains(anyOf('authorization', 'apikey', 'x-api-key'))),
        );
        expect(
          request.url.queryParameters.keys,
          isNot(contains(anyOf('key', 'api_key', 'token'))),
        );
        return http.Response(jsonEncode(_response()), 200);
      });
      final service = OpenStreetMapRouteService(client: client);
      addTearDown(service.close);
      final route = await service.route(_from, _to);
      expect(route.isDemo, isFalse);
      expect(route.points.first, _from);
      expect(route.points.last, _to);
      expect(route.distanceMeters, 289);
      expect(route.durationSeconds, 240);
      expect(route.instructions.map((step) => step.text), [
        'Start walking north on Jalan Pertama',
        'Turn right onto Jalan Kedua',
        'Arrive at your destination on the left',
      ]);
      expect(route.instructions[1].point, const GeoPoint(3.1491, 101.7100));
    },
  );

  test('does not replace a no-route response with demo geometry', () async {
    final service = OpenStreetMapRouteService(
      client: MockClient(
        (_) async => http.Response(jsonEncode({'code': 'NoRoute'}), 200),
      ),
    );
    addTearDown(service.close);
    await expectLater(
      service.route(_from, _to),
      throwsA(isA<WalkingRouteException>()),
    );
  });

  test(
    'rate limits and connection failures produce bounded readable errors',
    () async {
      final limited = OpenStreetMapRouteService(
        client: MockClient(
          (_) async => http.Response('Do not expose this upstream body', 429),
        ),
      );
      addTearDown(limited.close);
      await expectLater(
        limited.route(_from, _to),
        throwsA(
          isA<WalkingRouteException>().having(
            (error) => error.message,
            'message',
            contains('Wait'),
          ),
        ),
      );
      final disconnected = OpenStreetMapRouteService(
        client: MockClient(
          (_) async => throw http.ClientException('An upstream detail'),
        ),
      );
      addTearDown(disconnected.close);
      await expectLater(
        disconnected.route(_from, _to),
        throwsA(
          isA<WalkingRouteException>().having(
            (error) => error.message,
            'message',
            isNot(contains('upstream detail')),
          ),
        ),
      );
    },
  );

  test(
    'times out a stalled walking request without returning a fabricated route',
    () async {
      final response = Completer<http.Response>();
      final service = OpenStreetMapRouteService(
        client: MockClient((_) => response.future),
        timeout: const Duration(milliseconds: 5),
      );
      addTearDown(service.close);
      await expectLater(
        service.route(_from, _to),
        throwsA(
          isA<WalkingRouteException>().having(
            (error) => error.message,
            'message',
            contains('too long'),
          ),
        ),
      );
      response.complete(http.Response(jsonEncode(_response()), 200));
    },
  );

  test('rejects a distant trip before issuing a request', () async {
    var called = false;
    final service = OpenStreetMapRouteService(
      client: MockClient((_) async {
        called = true;
        return http.Response(jsonEncode(_response()), 200);
      }),
    );
    addTearDown(service.close);
    await expectLater(
      service.route(_from, const GeoPoint(4, 102)),
      throwsA(isA<WalkingRouteException>()),
    );
    expect(called, isFalse);
  });

  test(
    'rejects invalid metrics, missing steps, bad geometry, and distant snapped endpoints',
    () async {
      final fixtures = <Map<String, Object?>>[];
      final long = _response();
      ((long['routes'] as List).first as Map)['distance'] = 10001;
      fixtures.add(long);
      final missingSteps = _response();
      ((missingSteps['routes'] as List).first as Map)['legs'] = [];
      fixtures.add(missingSteps);
      final missingGeometry = _response();
      ((missingGeometry['routes'] as List).first as Map).remove('geometry');
      fixtures.add(missingGeometry);
      final remote = _response();
      (((remote['routes'] as List).first as Map)['geometry']
          as Map)['coordinates'] = [
        [0, 0],
        [0, .001],
      ];
      fixtures.add(remote);
      for (final fixture in fixtures) {
        final service = OpenStreetMapRouteService(
          client: MockClient(
            (_) async => http.Response(jsonEncode(fixture), 200),
          ),
        );
        await expectLater(
          service.route(_from, _to),
          throwsA(anyOf(isA<FormatException>(), isA<WalkingRouteException>())),
        );
        service.close();
      }
    },
  );

  test(
    'rejects malformed JSON instead of treating it as an empty successful route',
    () async {
      final service = OpenStreetMapRouteService(
        client: MockClient(
          (_) async => http.Response('<html>error</html>', 200),
        ),
      );
      addTearDown(service.close);
      await expectLater(service.route(_from, _to), throwsFormatException);
    },
  );

  test('closing the adapter preserves injected client ownership', () async {
    final client = _TrackingClient();
    final service = OpenStreetMapRouteService(client: client);
    service.close();
    service.close();
    expect(client.closedByCaller, isFalse);
    await expectLater(service.route(_from, _to), throwsStateError);
    client.close();
    expect(client.closedByCaller, isTrue);
  });
}

class _TrackingClient extends MockClient {
  _TrackingClient()
    : super((_) async => http.Response(jsonEncode(_response()), 200));
  bool closedByCaller = false;

  @override
  void close() {
    closedByCaller = true;
    super.close();
  }
}
