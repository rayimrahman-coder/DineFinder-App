import 'dart:math' as math;

/// Geographic coordinates in WGS84 decimal degrees.
class GeoPoint {
  const GeoPoint(this.latitude, this.longitude)
    : assert(latitude >= -90 && latitude <= 90),
      assert(longitude >= -180 && longitude <= 180);

  final double latitude;
  final double longitude;

  bool get isValid =>
      latitude.isFinite &&
      longitude.isFinite &&
      latitude >= -90 &&
      latitude <= 90 &&
      longitude >= -180 &&
      longitude <= 180;

  factory GeoPoint.fromJson(Object? json) {
    double latitude;
    double longitude;
    if (json is List && json.length >= 2) {
      // GeoJSON always stores longitude first.
      longitude = requiredNumber(json[0], 'longitude');
      latitude = requiredNumber(json[1], 'latitude');
    } else if (json is Map) {
      latitude = requiredNumber(json['latitude'] ?? json['lat'], 'latitude');
      longitude = requiredNumber(
        json['longitude'] ?? json['lon'] ?? json['lng'],
        'longitude',
      );
    } else {
      throw const FormatException('A location must contain coordinates.');
    }
    if (latitude < -90 ||
        latitude > 90 ||
        longitude < -180 ||
        longitude > 180) {
      throw const FormatException('Coordinates are outside the valid range.');
    }
    return GeoPoint(latitude, longitude);
  }

  Map<String, double> toJson() => {
    'latitude': latitude,
    'longitude': longitude,
  };

  @override
  bool operator ==(Object other) =>
      other is GeoPoint &&
      other.latitude == latitude &&
      other.longitude == longitude;

  @override
  int get hashCode => Object.hash(latitude, longitude);

  @override
  String toString() => 'GeoPoint($latitude, $longitude)';
}

double requiredNumber(Object? value, String name) {
  final number = value is num ? value.toDouble() : double.tryParse('$value');
  if (number == null || !number.isFinite) {
    throw FormatException('$name must be a finite number.');
  }
  return number;
}

String _requiredString(Object? value, String name) {
  if (value is! String || value.trim().isEmpty) {
    throw FormatException('$name must be a nonempty string.');
  }
  return value;
}

class Restaurant {
  const Restaurant({
    required this.id,
    required this.name,
    required this.cuisine,
    required this.description,
    required this.address,
    required this.location,
    required this.rating,
    required this.priceLevel,
    required this.emoji,
    required this.accentHex,
    this.tags = const [],
    this.openingHours = 'Check opening hours with the restaurant',
  });

  final String id;
  final String name;
  final String cuisine;
  final String description;
  final String address;
  final GeoPoint location;
  final double rating;
  final int priceLevel;
  final String emoji;
  final int accentHex;
  final List<String> tags;
  final String openingHours;

  String get priceLabel =>
      List.filled(priceLevel.clamp(1, 4).toInt(), '\$').join();

  factory Restaurant.fromJson(Map<String, dynamic> json) {
    final rating = requiredNumber(json['rating'] ?? 0, 'rating');
    final price = requiredNumber(json['price_level'] ?? 2, 'price_level');
    if (rating < 0 || rating > 5 || price < 1 || price > 4) {
      throw const FormatException('Restaurant rating or price is invalid.');
    }
    final rawAccent = json['accent_hex'];
    final accent = rawAccent is num
        ? rawAccent.toInt()
        : int.tryParse('${rawAccent ?? ''}'.replaceFirst('#', ''), radix: 16);
    return Restaurant(
      id: _requiredString(json['id'], 'id'),
      name: _requiredString(json['name'], 'name'),
      cuisine: json['cuisine'] as String? ?? 'Restaurant',
      description: json['description'] as String? ?? '',
      address: json['address'] as String? ?? '',
      location: GeoPoint.fromJson(json),
      rating: rating,
      priceLevel: price.toInt(),
      emoji: json['emoji'] as String? ?? '🍽️',
      accentHex: accent == null ? 0xffdef1e7 : (accent | 0xff000000),
      tags: (json['tags'] as List? ?? const []).whereType<String>().toList(),
      openingHours:
          json['opening_hours'] as String? ??
          'Check opening hours with the restaurant',
    );
  }
}

class RouteInstruction {
  const RouteInstruction({
    required this.point,
    required this.text,
    required this.distanceMeters,
  });

  final GeoPoint point;
  final String text;
  final double distanceMeters;

  factory RouteInstruction.fromJson(Map<String, dynamic> json) {
    final distance = requiredNumber(
      json['distance_meters'] ?? json['distance'] ?? 0,
      'instruction distance',
    );
    if (distance < 0) {
      throw const FormatException('Instruction distance cannot be negative.');
    }
    return RouteInstruction(
      point: GeoPoint.fromJson(json['point']),
      text: _requiredString(json['text'], 'instruction text'),
      distanceMeters: distance,
    );
  }
}

class RoutePlan {
  RoutePlan({
    required List<GeoPoint> points,
    required List<RouteInstruction> instructions,
    required this.distanceMeters,
    required this.durationSeconds,
    this.isDemo = false,
  }) : points = List.unmodifiable(points),
       instructions = List.unmodifiable(instructions) {
    if (points.length < 2 || points.any((p) => !p.isValid)) {
      throw const FormatException(
        'A route requires at least two valid points.',
      );
    }
    if (instructions.any(
      (step) =>
          !step.point.isValid ||
          !step.distanceMeters.isFinite ||
          step.distanceMeters < 0,
    )) {
      throw const FormatException(
        'Route instructions must contain valid locations and distances.',
      );
    }
    if (!distanceMeters.isFinite ||
        !durationSeconds.isFinite ||
        math.min(distanceMeters, durationSeconds) < 0) {
      throw const FormatException(
        'Route metrics must be finite and nonnegative.',
      );
    }
  }

  final List<GeoPoint> points;
  final List<RouteInstruction> instructions;
  final double distanceMeters;
  final double durationSeconds;
  final bool isDemo;

  factory RoutePlan.fromJson(Map<String, dynamic> json) {
    final rawPoints = json['points'] ?? json['coordinates'];
    if (rawPoints is! List || rawPoints.length < 2) {
      throw const FormatException('The routing service returned no path.');
    }
    final rawInstructions = json['instructions'] ?? const [];
    if (rawInstructions is! List) {
      throw const FormatException('Route instructions are invalid.');
    }
    return RoutePlan(
      points: rawPoints.map(GeoPoint.fromJson).toList(),
      instructions: rawInstructions.map((value) {
        if (value is! Map) {
          throw const FormatException('A route instruction must be an object.');
        }
        return RouteInstruction.fromJson(Map<String, dynamic>.from(value));
      }).toList(),
      distanceMeters: requiredNumber(
        json['distance_meters'] ?? json['distance'],
        'route distance',
      ),
      durationSeconds: requiredNumber(
        json['duration_seconds'] ?? json['duration'],
        'route duration',
      ),
    );
  }
}
