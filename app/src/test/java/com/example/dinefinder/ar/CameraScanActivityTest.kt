package com.example.dinefinder.ar

import android.Manifest
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.dinefinder.R
import com.example.dinefinder.Restaurant
import com.example.dinefinder.data.CameraRestaurantMatch
import com.example.dinefinder.data.CameraScanPose
import com.example.dinefinder.data.DiscoveryLocation
import com.example.dinefinder.data.RestaurantCameraScan
import com.example.dinefinder.places.PlaceSummary
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.lang.reflect.InvocationTargetException
import java.time.Duration
import kotlin.math.cos
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CameraScanActivityTest {
    @Test
    fun requestsCameraAndBothLocationPermissionsTogetherWithoutShowingMarkers() {
        val controller = Robolectric.buildActivity(ARDiscoveryActivity::class.java).create()
        val activity = controller.get()
        assertArrayEquals(arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ), Shadows.shadowOf(activity).lastRequestedPermission.requestedPermissions)
        assertEquals(0, activity.findViewById<FrameLayout>(R.id.discoveryMarkers).childCount)
        assertEquals(activity.getString(R.string.restaurant_scan_permissions),
            activity.findViewById<TextView>(R.id.discoveryHint).text.toString())
        controller.destroy()
    }

    @Test
    fun approximateLocationDoesNotEnableScanMarkers() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
            Manifest.permission.CAMERA, Manifest.permission.ACCESS_COARSE_LOCATION
        )
        // Stay CREATED: there is no camera session in this permission test.
        val controller = Robolectric.buildActivity(ARDiscoveryActivity::class.java).create()
        val activity = controller.get()
        assertEquals(0, activity.findViewById<FrameLayout>(R.id.discoveryMarkers).childCount)
        assertEquals(activity.getString(R.string.restaurant_scan_location_required),
            activity.findViewById<TextView>(R.id.discoveryHint).text.toString())
        controller.destroy()
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun pausingClearsVisibleMarkersAndLateCallbacksCannotRestoreThem() {
        val controller = Robolectric.buildActivity(ARDiscoveryActivity::class.java).create().start().resume()
        val activity = controller.get()
        val container = activity.findViewById<FrameLayout>(R.id.discoveryMarkers)
        val marker = LinearLayout(activity)
        val markerViews = activity.javaClass.getDeclaredField("markerViews").apply { isAccessible = true }
            .get(activity) as MutableMap<Int, LinearLayout>
        markerViews[1] = marker
        container.addView(marker)
        assertEquals(1, container.childCount)
        controller.pause()
        assertEquals(0, container.childCount)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertEquals(0, container.childCount)
        controller.stop().destroy()
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun removedApiKeyClearsGoogleContentBeforeTheScannerResumes() {
        val controller = Robolectric.buildActivity(ARDiscoveryActivity::class.java).create()
        val activity = controller.get()
        val keyField = activity.javaClass.getDeclaredField("configuredKey").apply { isAccessible = true }
        val googleField = activity.javaClass.getDeclaredField("googlePlaces").apply { isAccessible = true }
        keyField.set(activity, "old-test-configuration")
        googleField.set(activity, listOf(PlaceSummary("example_place", "Example cafe",
            0.001, 0.0, "", "Cafe")))
        // The real device-local store is empty, so onResume observes key removal.
        controller.start().resume()
        assertEquals("", keyField.get(activity))
        assertTrue((googleField.get(activity) as List<PlaceSummary>).isEmpty())
        assertEquals(0, activity.findViewById<FrameLayout>(R.id.discoveryMarkers).childCount)
        controller.pause().stop().destroy()
    }

    @Test
    fun missingKeySetupHintOpensSettingsEvenBeforeGpsOrCameraAreReady() {
        val controller = Robolectric.buildActivity(ARDiscoveryActivity::class.java).create()
        val activity = controller.get()
        activity.findViewById<TextView>(R.id.discoverySetupHint).performClick()
        val intent = Shadows.shadowOf(activity).nextStartedActivity
        assertTrue(intent.getBooleanExtra("open_settings", false))
        assertTrue(intent.getBooleanExtra("return_to_navigation", false))
        controller.destroy()
    }

    @Test
    fun firstVisibleRestaurantCardsRenderCafeAndFoodCourtIconsInsideTheNewBadges() {
        // Exercise real card creation without starting camera, sensor or location hardware.
        val controller = Robolectric.buildActivity(ARDiscoveryActivity::class.java).create()
        val activity = controller.get()
        val container = activity.findViewById<FrameLayout>(R.id.discoveryMarkers)
        container.layout(0, 0, 1_000, 1_000)
        val cafe = markerRestaurant(1, "Campus Cafe", "Cafe")
        val foodCourt = markerRestaurant(2, "Campus Food Court", "Food court")

        renderVisibleMarkers(activity, listOf(
            CameraRestaurantMatch(cafe, 123.6, 0.25, 0.5),
            CameraRestaurantMatch(foodCourt, 27.1, 0.75, 0.5)
        ))

        assertEquals(2, container.childCount)
        val cafeCard = container.getChildAt(0) as LinearLayout
        val foodCourtCard = container.getChildAt(1) as LinearLayout
        assertTrue(cafeCard.getChildAt(0) is FrameLayout)
        assertTrue(foodCourtCard.getChildAt(0) is FrameLayout)
        assertCardContent(activity, cafeCard, cafe, 124, R.drawable.ic_cafe_marker)
        assertCardContent(activity, foodCourtCard, foodCourt, 27, R.drawable.ic_restaurant_marker)
        assertTrue(cafeCard.isClickable)
        assertTrue(cafeCard.isFocusable)
        controller.destroy()
    }

    @Test
    fun visibleRestaurantUpdatesReuseTheCardAndRefreshItsIconLabelsAndAccessibilityText() {
        val controller = Robolectric.buildActivity(ARDiscoveryActivity::class.java).create()
        val activity = controller.get()
        val container = activity.findViewById<FrameLayout>(R.id.discoveryMarkers)
        container.layout(0, 0, 1_000, 1_000)
        val original = markerRestaurant(1, "Campus Cafe", "Cafe")
        renderVisibleMarkers(activity, listOf(CameraRestaurantMatch(original, 125.0, 0.4, 0.5)))
        val card = container.getChildAt(0) as LinearLayout
        val icon = descendants(card).filterIsInstance<ImageView>().single()

        val updated = original.copy(name = "Campus Food Court", cuisine = "Food court")
        renderVisibleMarkers(activity, listOf(CameraRestaurantMatch(updated, 81.6, 0.6, 0.5)))

        assertEquals(1, container.childCount)
        assertSame(card, container.getChildAt(0))
        assertSame(icon, descendants(card).filterIsInstance<ImageView>().single())
        assertCardContent(activity, card, updated, 82, R.drawable.ic_restaurant_marker)
        controller.destroy()
    }

    @Test
    fun restaurantsLeavingTheCameraViewRemoveTheirCardsAndCanAppearAgain() {
        val controller = Robolectric.buildActivity(ARDiscoveryActivity::class.java).create()
        val activity = controller.get()
        val container = activity.findViewById<FrameLayout>(R.id.discoveryMarkers)
        container.layout(0, 0, 1_000, 1_000)
        val cafe = CameraRestaurantMatch(markerRestaurant(1, "Campus Cafe", "Cafe"), 100.0, 0.25, 0.5)
        val foodCourt = CameraRestaurantMatch(markerRestaurant(2, "Food Court", "Food court"), 200.0, 0.75, 0.5)
        renderVisibleMarkers(activity, listOf(cafe, foodCourt))
        val firstCafeCard = container.getChildAt(0)
        val foodCourtCard = container.getChildAt(1)

        renderVisibleMarkers(activity, listOf(foodCourt))
        assertEquals(1, container.childCount)
        assertSame(foodCourtCard, container.getChildAt(0))
        renderVisibleMarkers(activity, emptyList())
        assertEquals(0, container.childCount)
        renderVisibleMarkers(activity, listOf(cafe))
        assertEquals(1, container.childCount)
        assertNotSame(firstCafeCard, container.getChildAt(0))
        assertCardContent(activity, container.getChildAt(0) as LinearLayout,
            cafe.restaurant, 100, R.drawable.ic_cafe_marker)
        controller.destroy()
    }

    @Test
    fun cardsInTheSameDirectionStaySeparatedAfterLayoutAndHeadingUpdates() {
        val controller = Robolectric.buildActivity(ARDiscoveryActivity::class.java).create()
        val activity = controller.get()
        val container = activity.findViewById<FrameLayout>(R.id.discoveryMarkers)
        container.layout(0, 0, 1_000, 1_000)
        val matches = (1..4).map { id -> CameraRestaurantMatch(
            markerRestaurant(id, "Restaurant with a longer name $id", "Cafe"),
            id * 50.0, 0.5, 0.5
        ) }

        renderVisibleMarkers(activity, matches)
        layoutMarkerContainer(container, 1_000, 1_000)
        val cards = (0 until container.childCount).map { container.getChildAt(it) }
        assertEquals(4, cards.count { it.visibility == View.VISIBLE })
        assertSeparatedCards(cards, 1_000, 1_000)
        val rows = cards.map { it.top }

        // Minor bearing updates must not reset the cards to the same row.
        renderVisibleMarkers(activity, matches.map { it.copy(screenX = 0.51) })
        layoutMarkerContainer(container, 1_000, 1_000)
        assertSeparatedCards(cards, 1_000, 1_000)
        assertEquals(rows, cards.map { it.top })
        controller.destroy()
    }

    @Test
    fun cardsAtBothCameraEdgesRemainInsideTheViewAndDoNotOverlap() {
        val controller = Robolectric.buildActivity(ARDiscoveryActivity::class.java).create()
        val activity = controller.get()
        val container = activity.findViewById<FrameLayout>(R.id.discoveryMarkers)
        container.layout(0, 0, 400, 1_000)
        val matches = listOf(0.0, 0.01, 0.99, 1.0).mapIndexed { index, x ->
            CameraRestaurantMatch(markerRestaurant(index + 1, "Restaurant $index", "Cafe"),
                20.0 * (index + 1), x, 0.5)
        }

        renderVisibleMarkers(activity, matches)
        layoutMarkerContainer(container, 400, 1_000)
        val cards = (0 until container.childCount).map { container.getChildAt(it) }
        assertEquals(4, cards.count { it.visibility == View.VISIBLE })
        assertSeparatedCards(cards, 400, 1_000)
        controller.destroy()
    }

    @Test
    fun crowdedCardsKeepTheNearestRestaurantsClearOfControlsAndAdaptWhenSpaceShrinks() {
        val controller = Robolectric.buildActivity(ARDiscoveryActivity::class.java).create()
        val activity = controller.get()
        val container = activity.findViewById<FrameLayout>(R.id.discoveryMarkers)
        container.layout(0, 0, 400, 1_000)
        val back = activity.findViewById<View>(R.id.discoveryBack)
        back.layout(16, 16, 64, 64)
        val controls = activity.findViewById<View>(R.id.discoveryControls)
        controls.layout(0, 600, 400, 1_000)
        val matches = (1..12).map { id -> CameraRestaurantMatch(
            markerRestaurant(id, "Restaurant $id", "Cafe"), id * 20.0, 0.5, 0.5
        ) }
        renderVisibleMarkers(activity, matches)
        layoutMarkerContainer(container, 400, 1_000)
        val cards = (0 until container.childCount).map { container.getChildAt(it) }
        val initialCount = cards.count { it.visibility == View.VISIBLE }
        assertTrue(initialCount in 1 until matches.size)
        assertTrue(cards.take(initialCount).all { it.visibility == View.VISIBLE })
        assertTrue(cards.drop(initialCount).all { it.visibility == View.INVISIBLE })
        assertSeparatedCards(cards, 400, 1_000)
        assertTrue(cards.filter { it.visibility == View.VISIBLE }.all {
            it.top > back.bottom && it.bottom < controls.top
        })
        val overflow = activity.findViewById<TextView>(R.id.discoveryOverflowHint)
        assertEquals(View.VISIBLE, overflow.visibility)
        assertEquals(activity.getString(R.string.restaurant_scan_card_overflow, initialCount, matches.size),
            overflow.text.toString())

        // The controls can grow when setup, search status, or provider credits appear.
        controls.layout(0, 350, 400, 1_000)
        layoutMarkerContainer(container, 400, 1_000)
        assertTrue(cards.count { it.visibility == View.VISIBLE } < initialCount)
        assertSeparatedCards(cards, 400, 1_000)
        assertTrue(cards.filter { it.visibility == View.VISIBLE }.all {
            it.top > back.bottom && it.bottom < controls.top
        })

        // Keep the overflow summary stable even if controls leave no room for a card.
        controls.layout(0, back.bottom + 4, 400, 1_000)
        layoutMarkerContainer(container, 400, 1_000)
        assertTrue(cards.all { it.visibility == View.INVISIBLE })
        assertEquals(View.VISIBLE, overflow.visibility)

        controls.layout(0, 600, 400, 1_000)
        layoutMarkerContainer(container, 400, 1_000)
        assertEquals(initialCount, cards.count { it.visibility == View.VISIBLE })
        assertSeparatedCards(cards, 400, 1_000)
        controller.destroy()
    }

    @Test
    fun markerCentersRemainOnTheSamePixelRowAcrossPitchAndLabelHeightChanges() {
        val controller = Robolectric.buildActivity(ARDiscoveryActivity::class.java).create()
        val activity = controller.get()
        val container = activity.findViewById<FrameLayout>(R.id.discoveryMarkers)
        container.layout(0, 0, 1_000, 1_000)
        val restaurant = Restaurant(1, "Example venue", "", false, 0f, "", "", "",
            0.001, 0.0005773502691896258)
        val projection = doubleArrayOf(
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0,
            0.0, 0.0, -1.002, -1.0, 0.0, 0.0, -0.2002, 0.0
        )
        val positionMarkers = activity.javaClass.getDeclaredMethod("positionMarkers").apply { isAccessible = true }
        for (pitch in listOf(-60.0, 0.0, 60.0)) {
            val radians = Math.toRadians(pitch)
            val rotation = doubleArrayOf(1.0, 0.0, 0.0,
                0.0, -sin(radians), -cos(radians), 0.0, cos(radians), -sin(radians))
            val match = RestaurantCameraScan.visibleRestaurants(listOf(restaurant),
                DiscoveryLocation(0.0, 0.0), CameraScanPose(rotation, projection)).single()
            renderVisibleMarkers(activity, listOf(match))
            val marker = container.getChildAt(0)
            for (verticalPadding in listOf(10, 30)) {
                marker.setPadding(marker.paddingLeft, verticalPadding, marker.paddingRight, verticalPadding)
                positionMarkers.invoke(activity)
                layoutMarkerContainer(container, 1_000, 1_000)
                assertEquals(500, marker.top + marker.height / 2)
            }
        }
        controller.destroy()
    }

    private fun markerRestaurant(id: Int, name: String, cuisine: String) = Restaurant(
        id, name, cuisine, true, 4.5f, "", "", "", 0.001, 0.0
    )

    private fun layoutMarkerContainer(container: FrameLayout, width: Int, height: Int) {
        container.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        container.layout(0, 0, width, height)
    }

    private fun assertSeparatedCards(cards: List<View>, width: Int, height: Int) {
        val visible = cards.filter { it.visibility == View.VISIBLE }
        for ((index, card) in visible.withIndex()) {
            assertTrue("Card must have measured content", card.width > 0 && card.height > 0)
            assertTrue("Card must fit horizontally", card.left >= 0 && card.right <= width)
            assertTrue("Card must fit vertically", card.top >= 0 && card.bottom <= height)
            for (other in visible.drop(index + 1)) {
                assertTrue("Restaurant cards must not overlap",
                    card.right < other.left || other.right < card.left ||
                        card.bottom < other.top || other.bottom < card.top)
            }
        }
    }

    private fun renderVisibleMarkers(activity: ARDiscoveryActivity, matches: List<CameraRestaurantMatch>) {
        activity.javaClass.getDeclaredField("visibleRestaurants").apply { isAccessible = true }
            .set(activity, matches)
        try {
            activity.javaClass.getDeclaredMethod("renderMarkers").apply { isAccessible = true }
                .invoke(activity)
        } catch (error: InvocationTargetException) {
            // Keep rendering failures visible instead of hiding them behind reflection.
            throw error.targetException
        }
    }

    private fun assertCardContent(activity: ARDiscoveryActivity, card: LinearLayout,
        restaurant: Restaurant, distanceMeters: Int, iconResource: Int) {
        val icon = descendants(card).filterIsInstance<ImageView>().single()
        assertEquals(iconResource, Shadows.shadowOf(icon.drawable).createdFromResId)
        val labels = descendants(card).filterIsInstance<TextView>().map { it.text.toString() }.toList()
        assertEquals(listOf(restaurant.name,
            activity.getString(R.string.restaurant_distance_meters, distanceMeters)), labels)
        assertEquals(activity.getString(R.string.restaurant_scan_marker, restaurant.name, distanceMeters),
            card.contentDescription.toString())
    }

    private fun descendants(parent: ViewGroup): Sequence<View> = sequence {
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            yield(child)
            if (child is ViewGroup) yieldAll(descendants(child))
        }
    }
}
