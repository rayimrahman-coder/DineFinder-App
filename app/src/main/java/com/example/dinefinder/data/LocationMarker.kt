package com.example.dinefinder.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

@Entity(tableName = "locations")
data class LocationMarker(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: String,  // "outdoor" or "indoor"

    // Outdoor fields
    val latitude: Double? = null,
    val longitude: Double? = null,

    // Indoor fields
    val floor: Int? = null,
    val room: String? = null,
    val buildingId: String? = null,
    val x: Float? = null,
    val y: Float? = null,

    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface LocationMarkerDao {
    @Insert
    suspend fun insertMarker(marker: LocationMarker)

    @Query("SELECT * FROM locations WHERE type = :type")
    suspend fun getMarkersByType(type: String): List<LocationMarker>

    @Update
    suspend fun updateMarker(marker: LocationMarker)
}