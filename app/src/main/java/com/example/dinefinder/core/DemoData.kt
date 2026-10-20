package com.example.dinefinder.core

/** Fictitious places and paths for an explicitly labeled, offline demonstration. */
object DemoData {
    val origin = GeoPoint(3.1478, 101.7100)

    val restaurants: List<Restaurant> = listOf(
        Restaurant(
            id = "00000000-0000-4000-8000-000000000001",
            name = "Bamboo & Bowl",
            cuisine = "Japanese",
            description = "Slow-simmered ramen, warm wooden counters, and a little calm in the city. A fictitious place created for the DineFinder demo.",
            address = "12 Jalan Demo, Kuala Lumpur · Sample address",
            location = GeoPoint(3.1491, 101.7113),
            rating = 4.8,
            priceLevel = 2,
            emoji = "🍜",
            accentHex = 0xffe6eddaL,
            tags = listOf("Ramen", "Cozy", "Vegetarian options"),
            openingHours = "Demo hours · 11:00 AM–10:00 PM",
        ),
        Restaurant(
            id = "00000000-0000-4000-8000-000000000002",
            name = "Ember Kitchen",
            cuisine = "Malaysian",
            description = "Charcoal-grilled favorites and generous plates inspired by Malaysian home cooking. This restaurant is fictitious demo data.",
            address = "28 Lorong Demo, Kuala Lumpur · Sample address",
            location = GeoPoint(3.1457, 101.7120),
            rating = 4.7,
            priceLevel = 2,
            emoji = "🍲",
            accentHex = 0xfff4dfcaL,
            tags = listOf("Local favorites", "Grilled", "Casual"),
            openingHours = "Demo hours · 11:30 AM–10:30 PM",
        ),
        Restaurant(
            id = "00000000-0000-4000-8000-000000000003",
            name = "Casa Verde",
            cuisine = "Italian",
            description = "Handmade pasta, crisp pizza, and a leafy terrace for long lunches. A fictitious restaurant in the DineFinder demo.",
            address = "7 Persiaran Demo, Kuala Lumpur · Sample address",
            location = GeoPoint(3.1511, 101.7082),
            rating = 4.6,
            priceLevel = 3,
            emoji = "🍕",
            accentHex = 0xfff4e8caL,
            tags = listOf("Pizza", "Pasta", "Outdoor seating"),
            openingHours = "Demo hours · 12:00 PM–11:00 PM",
        ),
        Restaurant(
            id = "00000000-0000-4000-8000-000000000004",
            name = "The Morning Press",
            cuisine = "Café",
            description = "Freshly baked pastries, specialty coffee, and sunlit tables. This café is a fictitious demo destination.",
            address = "36 Jalan Contoh, Kuala Lumpur · Sample address",
            location = GeoPoint(3.1462, 101.7067),
            rating = 4.9,
            priceLevel = 1,
            emoji = "☕",
            accentHex = 0xffeadfd5L,
            tags = listOf("Coffee", "Brunch", "Bakery"),
            openingHours = "Demo hours · 8:00 AM–6:00 PM",
        ),
        Restaurant(
            id = "00000000-0000-4000-8000-000000000005",
            name = "Saffron House",
            cuisine = "Indian",
            description = "Fragrant curries, fluffy naan, and colorful sharing plates. A fictitious sample restaurant for exploring the app.",
            address = "19 Jalan Contoh, Kuala Lumpur · Sample address",
            location = GeoPoint(3.1438, 101.7094),
            rating = 4.7,
            priceLevel = 2,
            emoji = "🍛",
            accentHex = 0xfff4ddbaL,
            tags = listOf("Curry", "Vegetarian options", "Family friendly"),
            openingHours = "Demo hours · 11:00 AM–10:00 PM",
        ),
        Restaurant(
            id = "00000000-0000-4000-8000-000000000006",
            name = "Seoul Social",
            cuisine = "Korean",
            description = "Sizzling bowls and lively shared tables with Korean comfort food. This is a fictitious DineFinder demo restaurant.",
            address = "51 Lorong Contoh, Kuala Lumpur · Sample address",
            location = GeoPoint(3.1528, 101.7135),
            rating = 4.5,
            priceLevel = 3,
            emoji = "🥢",
            accentHex = 0xffeadcedL,
            tags = listOf("Korean", "Sharing plates", "Dinner"),
            openingHours = "Demo hours · 12:00 PM–11:00 PM",
        ),
    )

    fun nearby(center: GeoPoint, radiusMeters: Double = 1000.0): List<Restaurant> {
        require(radiusMeters.isFinite() && radiusMeters in 100.0..5000.0) {
            "Search radius must be between 100 and 5000 meters."
        }
        return restaurants.filter { distanceMeters(center, it.location) <= radiusMeters }
            .sortedBy { distanceMeters(center, it.location) }
    }

    /** Synthetic bends only; this function is never a fallback for live routing. */
    fun route(from: GeoPoint, to: GeoPoint): RoutePlan {
        val middleLatitude = (from.latitude + to.latitude) / 2.0
        val candidates = listOf(
            from,
            GeoPoint(middleLatitude, from.longitude),
            GeoPoint(middleLatitude, to.longitude),
            to,
        )
        val points = mutableListOf(from)
        candidates.drop(1).forEach { point ->
            if (distanceMeters(points.last(), point) > 0.1) points.add(point)
        }
        if (points.size == 1) points.add(to) else if (points.last() != to) {
            if (distanceMeters(points.last(), to) <= 0.1) points[points.lastIndex] = to
            else points.add(to)
        }
        val length = points.zipWithNext(::distanceMeters).sum()
        val instructions = points.indices.drop(1).map { index ->
            RouteInstruction(
                point = points[index],
                text = if (index == points.lastIndex) "Arrive at your demo destination"
                    else "Follow the bend in the demo route",
                distanceMeters = distanceMeters(points[index - 1], points[index]),
            )
        }
        return RoutePlan(points, instructions, length, length / 1.25, isDemo = true)
    }

    fun pointOnRoute(route: RoutePlan, fraction: Double): GeoPoint {
        require(fraction.isFinite()) { "Demo progress must be finite." }
        if (fraction <= 0.0) return route.points.first()
        if (fraction >= 1.0) return route.points.last()
        val lengths = route.points.zipWithNext(::distanceMeters)
        var remaining = lengths.sum() * fraction
        lengths.forEachIndexed { index, length ->
            if (remaining <= length && length > 0.0) {
                val ratio = remaining / length
                val from = route.points[index]
                val to = route.points[index + 1]
                val longitude = from.longitude + headingDelta(to.longitude, from.longitude) * ratio
                return GeoPoint(
                    from.latitude + (to.latitude - from.latitude) * ratio,
                    ((longitude + 540.0) % 360.0) - 180.0,
                )
            }
            remaining -= length
        }
        return route.points.last()
    }
}
