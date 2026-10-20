package com.example.dinefinder

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "restaurants")
data class Restaurant(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val cuisine: String,
    val halal: Boolean,
    val rating: Float,
    val hours: String,
    val priceRange: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val distanceLabel: String = "Nearby"
)
