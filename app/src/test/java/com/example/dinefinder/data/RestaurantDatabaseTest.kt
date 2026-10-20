package com.example.dinefinder.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.dinefinder.Restaurant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RestaurantDatabaseTest {
    private lateinit var context: Context
    private lateinit var databaseName: String
    private lateinit var database: RestaurantDatabase

    @Before
    fun createDatabase() {
        context = RuntimeEnvironment.getApplication()
        databaseName = "restaurant-test-${UUID.randomUUID()}.db"
        context.getDatabasePath(databaseName).parentFile?.mkdirs()
        database = RestaurantDatabase.create(context, databaseName)
    }

    @After
    fun deleteDatabase() {
        if (::database.isInitialized) database.close()
        context.deleteDatabase(databaseName)
        context.deleteDatabase("$databaseName-wal")
        context.deleteDatabase("$databaseName-shm")
    }

    @Test
    fun creationStoresExactlyTheSuppliedRestaurantsWithoutDuplicatesOnReopening() = runBlocking {
        val initial = database.restaurantDao().getRestaurants()
        assertEquals(expectedInitialRestaurants, initial.map { it.copy(id = 0) })
        assertTrue(initial.all { it.id > 0 })
        assertEquals(2, initial.map { it.id }.distinct().size)

        repeat(2) {
            reopenDatabase()
            assertEquals(initial, database.restaurantDao().getRestaurants())
        }
    }

    @Test
    fun newRestaurantGetsIdAndCanBeUpdatedQueriedAndDeleted() = runBlocking {
        val dao = database.restaurantDao()
        val initial = dao.getRestaurants()
        val newRestaurant = testRestaurant()
        dao.upsert(newRestaurant)

        val inserted = dao.getRestaurants().single { it.name == newRestaurant.name }
        assertTrue(inserted.id > initial.maxOf { it.id })
        assertEquals(newRestaurant.copy(id = inserted.id), dao.getRestaurant(inserted.id))

        val edited = inserted.copy(
            name = "Updated Test Restaurant",
            cuisine = "Alternate Cuisine",
            halal = false,
            rating = 3.5f,
            hours = "10:00 - 18:00",
            priceRange = "20 - 30",
            address = "Test Address B",
            latitude = 1.0,
            longitude = 1.0,
            distanceLabel = "Unknown distance"
        )
        dao.upsert(edited)

        assertEquals(edited, dao.getRestaurant(inserted.id))
        assertEquals(initial + edited, dao.getRestaurants())

        dao.delete(inserted.id)
        assertNull(dao.getRestaurant(inserted.id))
        assertEquals(initial, dao.getRestaurants())
    }

    @Test
    fun editsNewRestaurantsAndDeletionsSurviveReopening() = runBlocking {
        val dao = database.restaurantDao()
        val original = dao.getRestaurants()
        val edited = original[0].copy(name = "Edited Record A", rating = 3.0f)
        dao.upsert(edited)
        dao.delete(original[1].id)
        dao.upsert(testRestaurant(name = "Record C"))
        val inserted = dao.getRestaurants().single { it.name == "Record C" }

        reopenDatabase()

        assertEquals(listOf(edited, inserted), database.restaurantDao().getRestaurants())
        assertNull(database.restaurantDao().getRestaurant(original[1].id))

        database.restaurantDao().getRestaurants().forEach { database.restaurantDao().delete(it.id) }
        reopenDatabase()
        assertTrue(database.restaurantDao().getRestaurants().isEmpty())
    }

    @Test
    fun observingRestaurantsEmitsInsertionsEditsAndDeletions() = runBlocking {
        val dao = database.restaurantDao()
        val initial = dao.getRestaurants()
        val emissions = Channel<List<Restaurant>>(Channel.UNLIMITED)
        val observer = launch(Dispatchers.Default) {
            dao.observeRestaurants().distinctUntilChanged().collect { emissions.send(it) }
        }
        try {
            assertEquals(initial, withTimeout(10_000) { emissions.receive() })

            dao.upsert(testRestaurant())
            val afterInsert = withTimeout(10_000) { emissions.receive() }
            val inserted = afterInsert.single { it.name == "Test Restaurant" }
            assertEquals(testRestaurant().copy(id = inserted.id), inserted)
            assertEquals(initial + inserted, afterInsert)

            val edited = inserted.copy(name = "Observed Edit")
            dao.upsert(edited)
            assertEquals(initial + edited, withTimeout(10_000) { emissions.receive() })

            dao.delete(inserted.id)
            assertEquals(initial, withTimeout(10_000) { emissions.receive() })
        } finally {
            observer.cancelAndJoin()
            emissions.close()
        }
    }

    @Test
    fun directVersionOneUpgradeClearsLegacyRowsAndAddsTheRequestedRestaurantsOnce() = runBlocking {
        createLegacyDatabase(1, listOf(
            testRestaurant(id = 1, name = "Edited Legacy Record"),
            testRestaurant(id = 2, name = "Existing Legacy Record"),
            testRestaurant(id = 9, name = "New Legacy Record")
        ))

        val migrated = database.restaurantDao().getRestaurants()
        assertEquals(expectedInitialRestaurants, migrated.map { it.copy(id = 0) })
        assertEquals(3, database.openHelper.readableDatabase.version)
        assertNull(database.restaurantDao().getRestaurant(1))
        assertNull(database.restaurantDao().getRestaurant(2))
        assertNull(database.restaurantDao().getRestaurant(9))

        reopenDatabase()
        assertEquals(migrated, database.restaurantDao().getRestaurants())

        val dao = database.restaurantDao()
        dao.upsert(testRestaurant(name = "Record Added After Upgrade"))
        val addedAfterUpgrade = dao.getRestaurants().single { it.name == "Record Added After Upgrade" }

        repeat(2) {
            reopenDatabase()
            assertEquals(migrated + addedAfterUpgrade, database.restaurantDao().getRestaurants())
        }
    }

    @Test
    fun versionTwoUpgradeAddsRequestedRestaurantsAndPreservesOtherRecords() = runBlocking {
        val existing = listOf(
            testRestaurant(id = 1, name = "Edited Existing Record").copy(rating = 3.5f),
            testRestaurant(id = 9, name = "Another Existing Record")
        )
        createLegacyDatabase(2, existing)

        val migrated = database.restaurantDao().getRestaurants()
        assertEquals(4, migrated.size)
        assertEquals(existing, migrated.filter { it.id == 1 || it.id == 9 })
        assertEquals(expectedInitialRestaurants, migrated.filter { it.id != 1 && it.id != 9 }
            .map { it.copy(id = 0) })
        assertEquals(3, database.openHelper.readableDatabase.version)

        reopenDatabase()
        assertEquals(migrated, database.restaurantDao().getRestaurants())
    }

    @Test
    fun versionTwoUpgradeDoesNotDuplicateAnExistingPlaceOrOverwriteItsEdits() = runBlocking {
        val existingPlace = expectedInitialRestaurants[0].copy(
            id = 7,
            cuisine = "Edited Cuisine",
            halal = false,
            rating = 3.5f,
            hours = "10:00 - 18:00",
            address = "Edited Test Address"
        )
        val otherRecord = testRestaurant(id = 12, name = "Other Existing Record")
        createLegacyDatabase(2, listOf(existingPlace, otherRecord))

        val migrated = database.restaurantDao().getRestaurants()
        assertEquals(3, migrated.size)
        assertEquals(existingPlace, migrated.single { it.name == existingPlace.name })
        assertEquals(otherRecord, database.restaurantDao().getRestaurant(otherRecord.id))
        assertEquals(expectedInitialRestaurants[1], migrated.single {
            it.name == expectedInitialRestaurants[1].name
        }.copy(id = 0))

        repeat(2) {
            reopenDatabase()
            assertEquals(migrated, database.restaurantDao().getRestaurants())
        }
    }

    private fun reopenDatabase() {
        database.close()
        database = RestaurantDatabase.create(context, databaseName)
    }

    private fun createLegacyDatabase(version: Int, records: List<Restaurant>) {
        if (::database.isInitialized) database.close()
        context.deleteDatabase(databaseName)
        context.deleteDatabase("$databaseName-wal")
        context.deleteDatabase("$databaseName-shm")
        context.getDatabasePath(databaseName).parentFile?.mkdirs()
        val helper = object : SQLiteOpenHelper(context, databaseName, null, version) {
            override fun onConfigure(db: SQLiteDatabase) {
                db.disableWriteAheadLogging()
            }
            override fun onCreate(db: SQLiteDatabase) {
                // Versions one and two share this exported schema and Room identity.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS restaurants (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL, cuisine TEXT NOT NULL, halal INTEGER NOT NULL,
                        rating REAL NOT NULL, hours TEXT NOT NULL, priceRange TEXT NOT NULL,
                        address TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL,
                        distanceLabel TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                db.execSQL(
                    "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, ?)",
                    arrayOf("f6745285caf71a73f2fd20d2604ad12e")
                )
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
                error("The legacy fixture must remain at its requested version")
            }
        }
        try {
            helper.setWriteAheadLoggingEnabled(false)
            val legacy = helper.writableDatabase
            records.forEach { legacy.insertOrThrow("restaurants", null, values(it)) }
            assertEquals(version, legacy.version)
            legacy.rawQuery("SELECT COUNT(*) FROM restaurants", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(records.size, cursor.getInt(0))
            }
        } finally {
            helper.close()
        }
        database = RestaurantDatabase.create(context, databaseName)
    }

    private fun values(restaurant: Restaurant) = ContentValues().apply {
        put("id", restaurant.id)
        put("name", restaurant.name)
        put("cuisine", restaurant.cuisine)
        put("halal", if (restaurant.halal) 1 else 0)
        put("rating", restaurant.rating)
        put("hours", restaurant.hours)
        put("priceRange", restaurant.priceRange)
        put("address", restaurant.address)
        put("latitude", restaurant.latitude)
        put("longitude", restaurant.longitude)
        put("distanceLabel", restaurant.distanceLabel)
    }

    private fun testRestaurant(id: Int = 0, name: String = "Test Restaurant") = Restaurant(
        id = id,
        name = name,
        cuisine = "Test Cuisine",
        halal = true,
        rating = 4.0f,
        hours = "09:00 - 17:00",
        priceRange = "10 - 20",
        address = "Test Address A",
        latitude = 0.0,
        longitude = 0.0,
        distanceLabel = "Unknown distance"
    )

    // Independent fixtures verify the supplied fields and that unknown details stay blank.
    private val expectedInitialRestaurants = listOf(
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
}
