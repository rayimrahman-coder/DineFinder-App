package com.example.dinefinder.data

import android.content.Context
import com.example.dinefinder.Restaurant
import kotlinx.coroutines.flow.Flow

class RestaurantRepository(private val dao: RestaurantDao) {
    fun observeRestaurants(): Flow<List<Restaurant>> = dao.observeRestaurants()

    suspend fun getRestaurants(): List<Restaurant> = dao.getRestaurants()

    suspend fun getRestaurant(id: Int): Restaurant? = dao.getRestaurant(id)

    suspend fun save(restaurant: Restaurant) = dao.upsert(restaurant)

    suspend fun delete(id: Int) = dao.delete(id)

    companion object {
        @Volatile
        private var instance: RestaurantRepository? = null

        fun getInstance(context: Context): RestaurantRepository =
            instance ?: synchronized(this) {
                instance ?: RestaurantRepository(
                    RestaurantDatabase.getInstance(context).restaurantDao()
                ).also { instance = it }
            }
    }
}
