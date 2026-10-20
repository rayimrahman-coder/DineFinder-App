import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import 'geo.dart';
import 'models.dart';
import 'repositories.dart';

/// A readable, non-secret-bearing routing failure for the app to present.
class WalkingRouteException implements Exception {
  const WalkingRouteException(this.message);
  final String message;

  @override
  String toString() => message;
}

/// Requests pedestrian geometry from FOSSGIS's key-free OpenStreetMap router.
///
/// The `routed-foot` server uses a walking graph. Using a driving graph with a
/// cosmetic `foot` URL profile would not supply pedestrian directions.
/// This community service needs an internet connection and may be rate limited.
/// No authorization header, account, app key, or synthetic fallback is used.
class OpenStreetMapRouteService implements RouteService {
  OpenStreetMapRouteService({
    http.Client? client,
    Uri? endpoint,
    this.timeout = const Duration(seconds: 20),
  }) : _client = client ?? http.Client(),
       _ownsClient = client == null,
       endpoint =
           endpoint ??
           Uri.parse(
             'https://routing.openstreetmap.de/routed-foot/route/v1/foot/',
           ) {
    if (this.endpoint.scheme != 'https' ||
        this.endpoint.host.isEmpty ||
        !this.endpoint.path.endsWith('/') ||
        this.endpoint.hasQuery ||
        this.endpoint.hasFragment ||
        this.endpoint.userInfo.isNotEmpty) {
      throw ArgumentError('The walking router needs an HTTPS base endpoint.');
    }
    if (timeout <= Duration.zero) {
      throw ArgumentError('The walking request timeout must be positive.');
    }
  }

  final http.Client _client;
  final bool _ownsClient;
  final Uri endpoint;
  final Duration timeout;
  bool _closed = false;

  @override
  Future<RoutePlan> route(GeoPoint from, GeoPoint to) async {
    if (_closed) throw StateError('The walking router has been closed.');
    if (!from.isValid || !to.isValid) {
      throw ArgumentError('Walking directions require valid coordinates.');
    }
    if (distanceMeters(from, to) > 10000) {
      throw const WalkingRouteException(
        'Choose a walking destination within 10 km.',
      );
    }
    final coordinates =
        '${from.longitude.toStringAsFixed(7)},'
        '${from.latitude.toStringAsFixed(7)};'
        '${to.longitude.toStringAsFixed(7)},'
        '${to.latitude.toStringAsFixed(7)}';
    final uri = endpoint.replace(
      path: '${endpoint.path}$coordinates',
      queryParameters: {
        'overview': 'full',
        'geometries': 'geojson',
        'steps': 'true',
        'alternatives': 'false',
        // Do not silently snap an indoor or inaccurate fix to a distant road.
        'radiuses': '100;100',
      },
    );
    http.Response response;
    try {
      response = await _client
          .get(uri, headers: const {'Accept': 'application/json'})
          .timeout(timeout);
    } on TimeoutException {
      throw const WalkingRouteException(
        'Walking directions took too long. Try again shortly.',
      );
    } on http.ClientException {
      throw const WalkingRouteException(
        'Unable to connect to walking directions. Check your connection.',
      );
    }
    if (response.statusCode == 429) {
      throw const WalkingRouteException(
        'The walking service is busy. Wait before trying again.',
      );
    }
    if (response.statusCode != 200) {
      throw const WalkingRouteException(
        'Walking directions are temporarily unavailable.',
      );
    }
    if (response.bodyBytes.length > 2 * 1024 * 1024) {
      throw const FormatException('The walking route response is too large.');
    }
    final Object? decoded = jsonDecode(response.body);
    if (decoded is! Map) {
      throw const FormatException('The walking route response is invalid.');
    }
    return _parseRoute(Map<String, dynamic>.from(decoded), from, to);
  }

  /// Release resources created by this adapter; injected clients stay owned by
  /// their caller and are never closed here.
  void close() {
    if (_closed) return;
    _closed = true;
    if (_ownsClient) _client.close();
  }
}

RoutePlan _parseRoute(Map<String, dynamic> json, GeoPoint from, GeoPoint to) {
  if (json['code'] == 'NoRoute' || json['code'] == 'NoSegment') {
    throw const WalkingRouteException(
      'No walking route was found for these locations.',
    );
  }
  if (json['code'] != 'Ok') {
    throw const FormatException(
      'The walking router returned an invalid result.',
    );
  }
  final routes = json['routes'];
  if (routes is! List || routes.isEmpty || routes.first is! Map) {
    throw const FormatException('The walking router returned no route.');
  }
  final route = Map<String, dynamic>.from(routes.first as Map);
  final distance = requiredNumber(route['distance'], 'route distance');
  final duration = requiredNumber(route['duration'], 'route duration');
  if (distance < 0 ||
      duration < 0 ||
      distance > 10000 ||
      (distance > 0 && duration == 0)) {
    throw const FormatException(
      'Walking route distance or duration is invalid.',
    );
  }
  final geometry = route['geometry'];
  if (geometry is! Map ||
      geometry['type'] != 'LineString' ||
      geometry['coordinates'] is! List) {
    throw const FormatException('The walking route has no valid path.');
  }
  final points = (geometry['coordinates'] as List)
      .map(GeoPoint.fromJson)
      .toList();
  if (points.length < 2 || points.length > 50000) {
    throw const FormatException('The walking route path is invalid.');
  }
  if (distanceMeters(from, points.first) > 150 ||
      distanceMeters(to, points.last) > 150) {
    throw const WalkingRouteException(
      'A nearby walking path could not be found for these locations.',
    );
  }
  final legs = route['legs'];
  if (legs is! List || legs.isEmpty) {
    throw const FormatException('The walking route contains no directions.');
  }
  final instructions = <RouteInstruction>[];
  for (final leg in legs) {
    if (leg is! Map || leg['steps'] is! List) {
      throw const FormatException('Walking directions are invalid.');
    }
    for (final step in leg['steps'] as List) {
      if (step is! Map || step['maneuver'] is! Map) {
        throw const FormatException('A walking direction is invalid.');
      }
      final maneuver = step['maneuver'] as Map;
      final stepDistance = requiredNumber(
        step['distance'],
        'direction distance',
      );
      if (stepDistance < 0 || stepDistance > 10000) {
        throw const FormatException(
          'A walking direction has an invalid distance.',
        );
      }
      instructions.add(
        RouteInstruction(
          point: GeoPoint.fromJson(maneuver['location']),
          text: _instructionText(maneuver, step),
          distanceMeters: stepDistance,
        ),
      );
    }
  }
  if (instructions.isEmpty) {
    throw const FormatException('The walking route contains no directions.');
  }
  return RoutePlan(
    points: points,
    instructions: instructions,
    distanceMeters: distance,
    durationSeconds: duration,
  );
}

String _instructionText(Map maneuver, Map step) {
  final type = maneuver['type'];
  if (type is! String || type.trim().isEmpty) {
    throw const FormatException('The walking maneuver type is invalid.');
  }
  final rawModifier = maneuver['modifier'];
  final modifier = rawModifier is String
      ? rawModifier.replaceAll('_', ' ')
      : '';
  final rawName = step['name'];
  final name = rawName is String ? rawName.trim() : '';
  final road = name.isEmpty
      ? ''
      : ' onto ${name.length > 160 ? name.substring(0, 160) : name}';
  switch (type) {
    case 'depart':
      final bearing = maneuver['bearing_after'];
      var direction = '';
      if (bearing is num &&
          bearing.isFinite &&
          bearing >= 0 &&
          bearing <= 360) {
        const compass = [
          'north',
          'northeast',
          'east',
          'southeast',
          'south',
          'southwest',
          'west',
          'northwest',
        ];
        direction = ' ${compass[((bearing + 22.5) ~/ 45) % 8]}';
      }
      return 'Start walking$direction${name.isEmpty ? '' : ' on $name'}';
    case 'arrive':
      return 'Arrive at your destination${modifier == 'left' || modifier == 'right' ? ' on the $modifier' : ''}';
    case 'turn':
    case 'end of road':
      return modifier.isEmpty ? 'Turn$road' : 'Turn $modifier$road';
    case 'fork':
      return modifier.isEmpty
          ? 'Continue at the fork$road'
          : 'Keep $modifier at the fork$road';
    case 'roundabout':
    case 'rotary':
      final exit = maneuver['exit'];
      return exit is num && exit.isFinite && exit > 0
          ? 'At the roundabout, take exit ${exit.toInt()}$road'
          : 'Continue around the roundabout$road';
    case 'exit roundabout':
    case 'exit rotary':
      return 'Exit the roundabout$road';
    case 'merge':
      return 'Join the path$road';
    case 'new name':
    case 'continue':
      return modifier == 'uturn'
          ? 'Turn back$road'
          : 'Continue${modifier.isEmpty || modifier == 'straight' ? '' : ' $modifier'}$road';
    default:
      return 'Follow the walking path$road';
  }
}
