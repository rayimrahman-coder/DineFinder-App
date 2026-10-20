package com.example.dinefinder.data

import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.dinefinder.Restaurant

object RestaurantInitialData {
    private val restaurants = listOf(
        Restaurant(
            name = "Cafe UMSKAL",
            cuisine = "Food court",
            halal = true,
            rating = 0f,
            hours = "",
            priceRange = "",
            address = "",
            latitude = 5.280285448386224,
            longitude = 115.17640486697412,
            distanceLabel = ""
        ),
        Restaurant(
            name = "Anjung Selera Sungai Pagar",
            cuisine = "Food court",
            halal = true,
            rating = 0f,
            hours = "",
            priceRange = "",
            address = "",
            latitude = 5.28381416215476,
            longitude = 115.17329350236913,
            distanceLabel = ""
        )
    )

    fun insertMissing(db: SupportSQLiteDatabase) {
        restaurants.forEach { restaurant ->
            db.execSQL(
                """
                INSERT INTO restaurants (
                    name, cuisine, halal, rating, hours, priceRange,
                    address, latitude, longitude, distanceLabel
                )
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM restaurants
                    WHERE name = ? AND latitude = ? AND longitude = ?
                )
                """.trimIndent(),
                arrayOf<Any>(
                    restaurant.name,
                    restaurant.cuisine,
                    if (restaurant.halal) 1 else 0,
                    restaurant.rating.toDouble(),
                    restaurant.hours,
                    restaurant.priceRange,
                    restaurant.address,
                    restaurant.latitude,
                    restaurant.longitude,
                    restaurant.distanceLabel,
                    restaurant.name,
                    restaurant.latitude,
                    restaurant.longitude
                )
            )
        }
    }
}
