package com.example.dinefinder.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.dinefinder.Restaurant
import kotlinx.coroutines.flow.Flow

@Dao
interface RestaurantDao {
    @Query("SELECT * FROM restaurants ORDER BY id")
    fun observeRestaurants(): Flow<List<Restaurant>>

    @Query("SELECT * FROM restaurants ORDER BY id")
    suspend fun getRestaurants(): List<Restaurant>

    @Query("SELECT * FROM restaurants WHERE id = :id")
    suspend fun getRestaurant(id: Int): Restaurant?

    @Upsert
    suspend fun upsert(restaurant: Restaurant)

    @Query("DELETE FROM restaurants WHERE id = :id")
    suspend fun delete(id: Int)
}
