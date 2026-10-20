import 'dart:math' as math;

import 'models.dart';

const _earthRadiusMeters = 6371008.8;
const _radiansPerDegree = math.pi / 180;

void _validatePoint(GeoPoint point) {
  if (!point.isValid) {
    throw ArgumentError.value(point, 'point', 'Invalid WGS84 coordinates');
  }
}

/// Great-circle distance, including across the antimeridian.
double distanceMeters(GeoPoint from, GeoPoint to) {
  _validatePoint(from);
  _validatePoint(to);
  final deltaLat = (to.latitude - from.latitude) * _radiansPerDegree;
  final deltaLon = (to.longitude - from.longitude) * _radiansPerDegree;
  final a =
      math.pow(math.sin(deltaLat / 2), 2) +
      math.cos(from.latitude * _radiansPerDegree) *
          math.cos(to.latitude * _radiansPerDegree) *
          math.pow(math.sin(deltaLon / 2), 2);
  return 2 * _earthRadiusMeters * math.asin(math.sqrt(a.clamp(0.0, 1.0)));
}

double normalizeAngle(double degrees) {
  if (!degrees.isFinite) throw ArgumentError('Heading must be finite.');
  return ((degrees % 360) + 360) % 360;
}

/// Signed shortest rotation from device heading toward a target bearing.
double headingDelta(double bearing, double heading) =>
    (normalizeAngle(bearing) - normalizeAngle(heading) + 540) % 360 - 180;

double bearingDegrees(GeoPoint from, GeoPoint to) {
  _validatePoint(from);
  _validatePoint(to);
  final fromLat = from.latitude * _radiansPerDegree;
  final toLat = to.latitude * _radiansPerDegree;
  final deltaLon = (to.longitude - from.longitude) * _radiansPerDegree;
  return normalizeAngle(
    math.atan2(
          math.sin(deltaLon) * math.cos(toLat),
          math.cos(fromLat) * math.sin(toLat) -
              math.sin(fromLat) * math.cos(toLat) * math.cos(deltaLon),
        ) /
        _radiansPerDegree,
  );
}

class MarkerProjection {
  const MarkerProjection({
    required this.horizontal,
    required this.isVisible,
    required this.directionDelta,
  });

  /// -1 is the left edge, +1 is the right edge of the camera viewport.
  final double horizontal;
  final bool isVisible;
  final double directionDelta;
}

/// Approximate compass overlay projection, not a world-anchored AR pose.
MarkerProjection projectMarker(
  double bearing,
  double heading, {
  double fieldOfView = 70,
}) {
  if (!fieldOfView.isFinite || fieldOfView <= 0 || fieldOfView >= 180) {
    throw ArgumentError('Camera field of view must be between 0 and 180.');
  }
  final delta = headingDelta(bearing, heading);
  return MarkerProjection(
    horizontal: (delta / (fieldOfView / 2)).clamp(-1.0, 1.0).toDouble(),
    isVisible: delta.abs() <= fieldOfView / 2,
    directionDelta: delta,
  );
}

class RouteProgress {
  const RouteProgress({
    required this.distanceFromRouteMeters,
    required this.remainingMeters,
    required this.segmentIndex,
    required this.targetPoint,
    required this.instruction,
    required this.isOffRoute,
    required this.hasArrived,
  });

  final double distanceFromRouteMeters;
  final double remainingMeters;
  final int segmentIndex;
  final GeoPoint targetPoint;
  final RouteInstruction? instruction;
  final bool isOffRoute;
  final bool hasArrived;
}

class _NearestSegment {
  const _NearestSegment(this.index, this.fraction, this.distance);
  final int index;
  final double fraction;
  final double distance;
}

_NearestSegment _nearest(GeoPoint position, List<GeoPoint> points) {
  final cosLatitude = math.cos(position.latitude * _radiansPerDegree);
  var best = const _NearestSegment(0, 0, double.infinity);
  for (var i = 0; i < points.length - 1; i++) {
    // A local tangent-plane projection is sufficient for a short walking route.
    final ax =
        headingDelta(points[i].longitude, position.longitude) *
        _radiansPerDegree *
        _earthRadiusMeters *
        cosLatitude;
    final ay =
        (points[i].latitude - position.latitude) *
        _radiansPerDegree *
        _earthRadiusMeters;
    final bx =
        headingDelta(points[i + 1].longitude, position.longitude) *
        _radiansPerDegree *
        _earthRadiusMeters *
        cosLatitude;
    final by =
        (points[i + 1].latitude - position.latitude) *
        _radiansPerDegree *
        _earthRadiusMeters;
    final dx = bx - ax;
    final dy = by - ay;
    final lengthSquared = dx * dx + dy * dy;
    final fraction = lengthSquared == 0
        ? 0.0
        : (-(ax * dx + ay * dy) / lengthSquared).clamp(0.0, 1.0).toDouble();
    final distance = math.sqrt(
      math.pow(ax + fraction * dx, 2) + math.pow(ay + fraction * dy, 2),
    );
    if (distance < best.distance) {
      best = _NearestSegment(i, fraction, distance);
    }
  }
  return best;
}

double _alongDistance(_NearestSegment nearest, List<double> segmentLengths) {
  var result = 0.0;
  for (var i = 0; i < nearest.index; i++) {
    result += segmentLengths[i];
  }
  return result + segmentLengths[nearest.index] * nearest.fraction;
}

RouteProgress nearestRouteProgress(
  GeoPoint position,
  RoutePlan route, {
  double offRouteThreshold = 35,
  double arrivalThreshold = 25,
}) {
  _validatePoint(position);
  if (!offRouteThreshold.isFinite ||
      !arrivalThreshold.isFinite ||
      offRouteThreshold <= 0 ||
      arrivalThreshold <= 0) {
    throw ArgumentError('Navigation thresholds must be positive.');
  }
  final segmentLengths = <double>[
    for (var i = 0; i < route.points.length - 1; i++)
      distanceMeters(route.points[i], route.points[i + 1]),
  ];
  final nearest = _nearest(position, route.points);
  final travelled = _alongDistance(nearest, segmentLengths);
  final length = segmentLengths.fold(0.0, (sum, value) => sum + value);
  final remaining = math.max(0.0, length - travelled).toDouble();
  final arrived =
      distanceMeters(position, route.points.last) <= arrivalThreshold &&
      remaining <= arrivalThreshold;
  // ORS associates each instruction with the START of its walking segment.
  // Showing the next instruction ahead would tell someone to turn before
  // reaching the junction. Keep the current segment's instruction active.
  RouteInstruction? instruction = route.instructions.isEmpty
      ? null
      : route.instructions.first;
  for (final candidate in route.instructions) {
    final along = _alongDistance(
      _nearest(candidate.point, route.points),
      segmentLengths,
    );
    if (along > travelled + 3) break;
    instruction = candidate;
  }
  if (arrived && route.instructions.isNotEmpty) {
    instruction = route.instructions.last;
  }
  var targetIndex = nearest.index + 1;
  if (targetIndex < route.points.length - 1 &&
      distanceMeters(position, route.points[targetIndex]) < 8) {
    targetIndex++;
  }
  return RouteProgress(
    distanceFromRouteMeters: nearest.distance,
    remainingMeters: arrived ? 0 : remaining,
    segmentIndex: nearest.index,
    targetPoint: route.points[targetIndex],
    instruction: instruction,
    isOffRoute: nearest.distance > offRouteThreshold,
    hasArrived: arrived,
  );
}
