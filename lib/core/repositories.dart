import 'package:supabase_flutter/supabase_flutter.dart';

import 'geo.dart';
import 'models.dart';

const demoOrigin = GeoPoint(3.1478, 101.7100);

abstract class RestaurantRepository {
  Future<List<Restaurant>> nearby(GeoPoint center, double radiusMeters);
}

void _validateSearch(GeoPoint center, double radiusMeters) {
  if (!center.isValid ||
      !radiusMeters.isFinite ||
      radiusMeters < 100 ||
      radiusMeters > 5000) {
    throw ArgumentError(
      'Search needs valid coordinates and a radius of 100–5000 m.',
    );
  }
}

class DemoRestaurantRepository implements RestaurantRepository {
  const DemoRestaurantRepository();

  @override
  Future<List<Restaurant>> nearby(GeoPoint center, double radiusMeters) async {
    _validateSearch(center, radiusMeters);
    final result = demoRestaurants
        .where(
          (restaurant) =>
              distanceMeters(center, restaurant.location) <= radiusMeters,
        )
        .toList();
    result.sort(
      (a, b) => distanceMeters(
        center,
        a.location,
      ).compareTo(distanceMeters(center, b.location)),
    );
    return result;
  }
}

class SupabaseRestaurantRepository implements RestaurantRepository {
  const SupabaseRestaurantRepository(this.client);
  final SupabaseClient client;

  @override
  Future<List<Restaurant>> nearby(GeoPoint center, double radiusMeters) async {
    _validateSearch(center, radiusMeters);
    final data = await client.rpc(
      'nearby_restaurants',
      params: {
        'latitude': center.latitude,
        'longitude': center.longitude,
        'radius_meters': radiusMeters,
      },
    );
    if (data is! List) {
      throw const FormatException(
        'Restaurant search returned an invalid response.',
      );
    }
    return data.map((row) {
      if (row is! Map) {
        throw const FormatException('A restaurant record is invalid.');
      }
      return Restaurant.fromJson(Map<String, dynamic>.from(row));
    }).toList();
  }
}

abstract class RouteService {
  Future<RoutePlan> route(GeoPoint from, GeoPoint to);
}

class SupabaseRouteService implements RouteService {
  const SupabaseRouteService(this.client);
  final SupabaseClient client;

  @override
  Future<RoutePlan> route(GeoPoint from, GeoPoint to) async {
    if (!from.isValid || !to.isValid) {
      throw ArgumentError('Walking directions require valid coordinates.');
    }
    // The routing key stays in the Edge Function. Anonymous users receive a
    // real authenticated session, which the function verifies server-side.
    if (client.auth.currentSession == null) {
      await client.auth.signInAnonymously();
    }
    final response = await client.functions.invoke(
      'walking-route',
      body: {'from': from.toJson(), 'to': to.toJson()},
    );
    final data = response.data;
    if (response.status < 200 || response.status >= 300 || data is! Map) {
      throw StateError(
        'Walking directions are unavailable. Try again shortly.',
      );
    }
    return RoutePlan.fromJson(Map<String, dynamic>.from(data));
  }
}

class DemoRouteService implements RouteService {
  const DemoRouteService();

  @override
  Future<RoutePlan> route(GeoPoint from, GeoPoint to) async {
    if (!from.isValid || !to.isValid) {
      throw ArgumentError('The demo route needs valid coordinates.');
    }
    // Synthetic geometry for the interactive demo only. This is intentionally
    // never returned when live walking directions fail.
    final middleLatitude = (from.latitude + to.latitude) / 2;
    final candidates = [
      from,
      GeoPoint(middleLatitude, from.longitude),
      GeoPoint(middleLatitude, to.longitude),
      to,
    ];
    final points = <GeoPoint>[from];
    for (final point in candidates.skip(1)) {
      if (distanceMeters(points.last, point) > 0.1) points.add(point);
    }
    if (points.length == 1) points.add(to);
    var distance = 0.0;
    for (var i = 0; i < points.length - 1; i++) {
      distance += distanceMeters(points[i], points[i + 1]);
    }
    return RoutePlan(
      points: points,
      instructions: [
        for (var i = 1; i < points.length - 1; i++)
          RouteInstruction(
            point: points[i],
            text: 'Follow the bend in the demo route',
            distanceMeters: distanceMeters(points[i - 1], points[i]),
          ),
        RouteInstruction(
          point: to,
          text: 'Arrive at your demo destination',
          distanceMeters: distanceMeters(points[points.length - 2], to),
        ),
      ],
      distanceMeters: distance,
      durationSeconds: distance / 1.25,
      isDemo: true,
    );
  }
}

/// Fictitious restaurants for a deterministic, offline Kuala Lumpur demo.
const demoRestaurants = <Restaurant>[
  Restaurant(
    id: '00000000-0000-4000-8000-000000000001',
    name: 'Bamboo & Bowl',
    cuisine: 'Japanese',
    description:
        'Slow-simmered ramen, warm wooden counters, and a little calm in the city. A fictitious place created for the DineFinder demo.',
    address: '12 Jalan Demo, Kuala Lumpur · Sample address',
    location: GeoPoint(3.1491, 101.7113),
    rating: 4.8,
    priceLevel: 2,
    emoji: '🍜',
    accentHex: 0xffe6edda,
    tags: ['Ramen', 'Cozy', 'Vegetarian options'],
    openingHours: 'Demo hours · 11:00 AM–10:00 PM',
  ),
  Restaurant(
    id: '00000000-0000-4000-8000-000000000002',
    name: 'Ember Kitchen',
    cuisine: 'Malaysian',
    description:
        'Charcoal-grilled favorites and generous plates inspired by Malaysian home cooking. This restaurant is fictitious demo data.',
    address: '28 Lorong Demo, Kuala Lumpur · Sample address',
    location: GeoPoint(3.1457, 101.7120),
    rating: 4.7,
    priceLevel: 2,
    emoji: '🍲',
    accentHex: 0xfff4dfca,
    tags: ['Local favorites', 'Grilled', 'Casual'],
    openingHours: 'Demo hours · 11:30 AM–10:30 PM',
  ),
  Restaurant(
    id: '00000000-0000-4000-8000-000000000003',
    name: 'Casa Verde',
    cuisine: 'Italian',
    description:
        'Handmade pasta, crisp pizza, and a leafy terrace for long lunches. A fictitious restaurant in the DineFinder demo.',
    address: '7 Persiaran Demo, Kuala Lumpur · Sample address',
    location: GeoPoint(3.1511, 101.7082),
    rating: 4.6,
    priceLevel: 3,
    emoji: '🍕',
    accentHex: 0xfff4e8ca,
    tags: ['Pizza', 'Pasta', 'Outdoor seating'],
    openingHours: 'Demo hours · 12:00 PM–11:00 PM',
  ),
  Restaurant(
    id: '00000000-0000-4000-8000-000000000004',
    name: 'The Morning Press',
    cuisine: 'Café',
    description:
        'Freshly baked pastries, specialty coffee, and sunlit tables. This café is a fictitious demo destination.',
    address: '36 Jalan Contoh, Kuala Lumpur · Sample address',
    location: GeoPoint(3.1462, 101.7067),
    rating: 4.9,
    priceLevel: 1,
    emoji: '☕',
    accentHex: 0xffeadfd5,
    tags: ['Coffee', 'Brunch', 'Bakery'],
    openingHours: 'Demo hours · 8:00 AM–6:00 PM',
  ),
  Restaurant(
    id: '00000000-0000-4000-8000-000000000005',
    name: 'Saffron House',
    cuisine: 'Indian',
    description:
        'Fragrant curries, fluffy naan, and colorful sharing plates. A fictitious sample restaurant for exploring the app.',
    address: '19 Jalan Contoh, Kuala Lumpur · Sample address',
    location: GeoPoint(3.1438, 101.7094),
    rating: 4.7,
    priceLevel: 2,
    emoji: '🍛',
    accentHex: 0xfff4ddba,
    tags: ['Curry', 'Vegetarian options', 'Family friendly'],
    openingHours: 'Demo hours · 11:00 AM–10:00 PM',
  ),
  Restaurant(
    id: '00000000-0000-4000-8000-000000000006',
    name: 'Seoul Social',
    cuisine: 'Korean',
    description:
        'Sizzling bowls and lively shared tables with Korean comfort food. This is a fictitious DineFinder demo restaurant.',
    address: '51 Lorong Contoh, Kuala Lumpur · Sample address',
    location: GeoPoint(3.1528, 101.7135),
    rating: 4.5,
    priceLevel: 3,
    emoji: '🥢',
    accentHex: 0xffeadced,
    tags: ['Korean', 'Sharing plates', 'Dinner'],
    openingHours: 'Demo hours · 12:00 PM–11:00 PM',
  ),
];
