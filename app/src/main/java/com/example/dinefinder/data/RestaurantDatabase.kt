package com.example.dinefinder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.dinefinder.Restaurant

@Database(entities = [Restaurant::class], version = 3, exportSchema = true)
abstract class RestaurantDatabase : RoomDatabase() {
    abstract fun restaurantDao(): RestaurantDao

    companion object {
        @Volatile
        private var instance: RestaurantDatabase? = null

        // Clear saved information once when upgrading. Later records remain persistent.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM restaurants")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                RestaurantInitialData.insertMissing(db)
            }
        }

        private val InitialDataCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                RestaurantInitialData.insertMissing(db)
            }
        }

        fun getInstance(context: Context): RestaurantDatabase =
            instance ?: synchronized(this) {
                instance ?: create(context)
                    .also { instance = it }
            }

        fun create(context: Context, name: String = "restaurants.db"): RestaurantDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                RestaurantDatabase::class.java,
                name
            )
                .setJournalMode(JournalMode.TRUNCATE)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(InitialDataCallback)
                .build()
    }
}
