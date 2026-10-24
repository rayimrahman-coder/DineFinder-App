package com.example.dinefinder.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.dinefinder.core.GeoPoint
import com.example.dinefinder.core.Restaurant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w393dp-h851dp")
class RestaurantProfileScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val restaurant = Restaurant("profile-test", "Cafe UMSKAL", "Food court", "Local meals on campus", "UMS Labuan International Campus",
        GeoPoint(5.280285, 115.176405), 4.6, 1, "", 0L, tags = listOf("HALAL"), isHalal = true)
    private fun show(busy: Boolean = false, rating: Double = restaurant.rating, navigate: () -> Unit = {}, close: () -> Unit = {}) {
        compose.activity.getSharedPreferences("dinefinder_saved_restaurants", Context.MODE_PRIVATE).edit().clear().commit()
        compose.setContent { MaterialTheme { RestaurantProfileScreen(restaurant.copy(rating = rating), 72.0, busy, close, navigate) } }
    }
    @Test fun profileShowsRealDetailsWithoutCoordinatesOrHalalLabels() {
        show()
        compose.onNodeWithText(restaurant.name).assertIsDisplayed()
        compose.onNodeWithText("72 m away").assertIsDisplayed()
        compose.onNodeWithText("Cuisine").assertIsDisplayed()
        compose.onNodeWithText("Food court").assertIsDisplayed()
        compose.onNodeWithText("Rating").assertIsDisplayed()
        compose.onNodeWithText(String.format(java.util.Locale.getDefault(), "%.1f / 5", 4.6)).assertIsDisplayed()
        compose.onNodeWithText("Halal", substring = true, ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithText("5.280285", substring = true).assertDoesNotExist()
        compose.onNodeWithText("115.176405", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Local meals on campus").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Location", substring = false).performScrollTo().assertIsDisplayed()
    }
    @Test fun navigationCloseAndSavingUseWorkingActions() {
        var navigated = false
        var closed = false
        show(navigate = { navigated = true }, close = { closed = true })
        compose.onNodeWithContentDescription("Save restaurant").performClick()
        compose.onNodeWithContentDescription("Remove from saved restaurants").assertIsDisplayed()
        compose.runOnIdle { assertTrue(compose.activity.getSharedPreferences("dinefinder_saved_restaurants", Context.MODE_PRIVATE).getBoolean(restaurant.id, false)) }
        compose.onNodeWithText("Start walking navigation").performClick()
        compose.runOnIdle { assertTrue(navigated) }
        compose.onNodeWithContentDescription("Back to restaurants").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }
    @Test fun busyRoutePreventsDuplicateNavigationRequests() {
        show(busy = true, rating = 0.0)
        compose.onNodeWithText("Not rated").assertIsDisplayed()
        compose.onNodeWithText("Finding walking route…").assertIsDisplayed().assertIsNotEnabled()
    }
    @Test
    @Config(sdk = [28], qualifiers = "w320dp-h480dp", fontScale = 2f)
    fun smallScreenKeepsNavigationAvailableAndDetailsScrollable() {
        show()
        compose.onNodeWithText("Start walking navigation").assertIsDisplayed()
        compose.onNodeWithText("Opening hours").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to restaurants").assertIsDisplayed()
    }
}
