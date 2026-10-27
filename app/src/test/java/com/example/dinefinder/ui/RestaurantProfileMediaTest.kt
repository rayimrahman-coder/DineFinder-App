package com.example.dinefinder.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.Fetcher
import coil.request.Options
import com.example.dinefinder.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w393dp-h851dp")
class RestaurantProfileMediaTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val restaurant = Restaurant("media-cafe", "Campus Cafe", "Cafe", "", "Labuan", GeoPoint(5.28,115.17),4.5,1,"",0L)
    private val details = RestaurantProfileDetails("https://images.example.test/cafe.jpg", 12, mapOf(5 to 8, 4 to 4, 3 to 0, 2 to 0, 1 to 0),
        listOf(RestaurantReview("test-review", "Test diner", 5, "Fresh lunch and friendly service.")))
    @Volatile private var fetchedImages = 0
    private fun loader(fail: Boolean = false): ImageLoader = ImageLoader.Builder(compose.activity).components {
        add(object : Fetcher.Factory<Any> {
            override fun create(data: Any, options: Options, imageLoader: ImageLoader): Fetcher = object : Fetcher {
                override suspend fun fetch(): DrawableResult {
                    fetchedImages++
                    if (fail) error("Simulated photo failure")
                    return DrawableResult(ColorDrawable(Color.GREEN), false, DataSource.MEMORY)
                }
            }
        })
    }.build()
    @Test fun realSummaryReviewAndPhotoAppearWithoutCoordinatesOrHalal() {
        val testLoader = loader()
        compose.setContent { MaterialTheme { RestaurantProfileScreen(restaurant,72.0,false,{}, {}, details=details, imageLoader=testLoader) } }
        compose.waitUntil(5000) { fetchedImages > 0 }
        compose.onNodeWithContentDescription("Photo of Campus Cafe").assertIsDisplayed()
        compose.onNodeWithText("12 reviews").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("5 stars: 8 reviews").assertExists()
        compose.onNodeWithText("Fresh lunch and friendly service.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Test diner").assertIsDisplayed()
        compose.onNodeWithText("Halal",substring=true).assertDoesNotExist()
        compose.onNodeWithText("5.28",substring=true).assertDoesNotExist()
        compose.onNodeWithText("Start walking navigation").assertIsDisplayed()
    }
    @Test fun missingMediaHasHonestFallbackWithoutFabricatedWrittenReviews() {
        compose.setContent { MaterialTheme { RestaurantProfileScreen(restaurant,72.0,false,{}, {},
            details=RestaurantProfileDetails(reviewCount=12)) } }
        compose.onNodeWithText("Photo not available").assertIsDisplayed()
        compose.onNodeWithText("12 reviews").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Written reviews are not available for this restaurant.").performScrollTo().assertIsDisplayed()
    }
    @Test fun failedPhotoStillAllowsNavigation() {
        var navigated = false
        val failedLoader = loader(fail=true)
        compose.setContent { MaterialTheme { RestaurantProfileScreen(restaurant,72.0,false,{}, {navigated=true},
            details=details, imageLoader=failedLoader) } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Photo couldn't be loaded").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Retry photo").assertIsDisplayed()
        compose.onNodeWithText("Start walking navigation").performClick()
        compose.runOnIdle { assertTrue(navigated) }
    }
    @Test fun metadataFailureHasRetryAndDoesNotBlockRestaurantProfile() {
        var retried = false
        compose.setContent { MaterialTheme { RestaurantProfileScreen(restaurant,72.0,false,{}, {},
            detailsError="Photos and reviews couldn't be loaded. Please try again.", onRetryDetails={retried=true}) } }
        compose.onNodeWithText("Retry photos and reviews").performClick()
        compose.runOnIdle { assertTrue(retried) }
        compose.onNodeWithText("Campus Cafe").assertIsDisplayed()
        compose.onNodeWithText("Start walking navigation").assertIsEnabled()
    }
    @Test
    @Config(sdk=[28],qualifiers="w320dp-h480dp",fontScale=2f)
    fun mediaAndLongReviewsRemainScrollableOnSmallScreens() {
        val testLoader = loader()
        compose.setContent { MaterialTheme { RestaurantProfileScreen(restaurant,72.0,false,{}, {}, details=details, imageLoader=testLoader) } }
        compose.onNodeWithText("Fresh lunch and friendly service.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Start walking navigation").assertIsDisplayed()
    }
}
