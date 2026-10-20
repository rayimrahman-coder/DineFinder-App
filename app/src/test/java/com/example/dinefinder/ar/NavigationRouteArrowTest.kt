package com.example.dinefinder.ar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import com.example.dinefinder.R
import com.example.dinefinder.camera.CameraArrowView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NavigationRouteArrowTest {
    @Test
    fun screenshotShowsStraightNowAndRightIn63MetersWithoutCompassData() {
        val controller = readyActivity()
        val activity = controller.get()
        render(activity)
        assertEquals("Continue straight for 63 m", primary(activity))
        assertEquals("Then in 63 m: Turn right", upcoming(activity))
        assertAction(activity, 0f)
        assertFalse(primary(activity).contains("Head west"))
        assertFalse(caption(activity).contains("preview", ignoreCase = true))
        // The arrow is a current walking action, not the route's compass tangent.
        val guidance = field(activity, "currentGuidance") as WalkingStepGuidance
        set(activity, "currentGuidance", guidance.copy(bearingDegrees = 0f))
        render(activity)
        assertAction(activity, 0f)
        controller.pause().stop().destroy()
    }

    @Test
    fun arrowChangesAtJunctionThenReturnsForwardForOutgoingPathAndNextTurn() {
        val controller = readyActivity()
        val activity = controller.get()
        val steps = steps(activity)
        val corner = steps[1].points.first()
        // Prompt at the nearby junction instead of requiring a fix exactly on it.
        move(activity, RouteCoordinate(corner.latitude, corner.longitude + metersLongitude(1.0)))
        assertEquals(0, field(activity, "currentStepIndex"))
        assertEquals("At the junction: Turn right", primary(activity))
        assertAction(activity, 90f)
        move(activity, corner)
        assertEquals("Now: Turn right", primary(activity))
        assertAction(activity, 90f)
        assertTrue(upcoming(activity).contains("Turn left"))
        move(activity, RouteCoordinate(corner.latitude + metersLatitude(10.0), corner.longitude))
        assertTrue(primary(activity).startsWith("Continue straight"))
        assertAction(activity, 0f)
        assertTrue(upcoming(activity).contains("Turn left"))
        val nextCorner = steps[2].points.first()
        move(activity, nextCorner)
        assertEquals("Now: Turn left", primary(activity))
        assertAction(activity, -90f)
        move(activity, RouteCoordinate(nextCorner.latitude, nextCorner.longitude - metersLongitude(12.0)))
        assertTrue(primary(activity).startsWith("Continue straight"))
        assertAction(activity, 0f)
        move(activity, steps.last().points.last())
        assertEquals(activity.getString(R.string.navigation_route_end), primary(activity))
        assertHidden(activity)
        controller.pause().stop().destroy()
    }

    @Test
    fun sparseCornerFixesKeepTextAndModelTurningUntilOutgoingProgressConfirmsCompletion() {
        val controller = readyActivity()
        val activity = controller.get()
        val corner = steps(activity)[1].points.first()
        move(activity, RouteCoordinate(corner.latitude, corner.longitude + metersLongitude(12.0)))
        assertTrue(primary(activity).startsWith("Continue straight"))
        assertAction(activity, 0f)
        move(activity, RouteCoordinate(corner.latitude, corner.longitude + metersLongitude(4.0)))
        assertEquals("At the junction: Turn right", primary(activity))
        assertAction(activity, 90f)
        // GPS skips the exact junction, then drifts slightly behind its incoming endpoint.
        move(activity, RouteCoordinate(corner.latitude + metersLatitude(4.0), corner.longitude + metersLongitude(.5)))
        assertEquals("Now: Turn right", primary(activity))
        repeat(20) {
            invoke(activity, "updateArrow") // Camera refreshes cannot consume a turn.
            assertAction(activity, 90f)
        }
        move(activity, RouteCoordinate(corner.latitude + metersLatitude(12.0), corner.longitude + metersLongitude(.5)))
        assertTrue(primary(activity).startsWith("Continue straight"))
        assertAction(activity, 0f)
        controller.pause().stop().destroy()
    }

    @Test
    fun biasedGpsCanCompleteRightTurnAndActivateTheFollowingLeftTurn() {
        val controller = readyActivity()
        val activity = controller.get()
        val corner = steps(activity)[1].points.first()
        move(activity, RouteCoordinate(corner.latitude, corner.longitude + metersLongitude(4.0)))
        assertAction(activity, 90f)
        move(activity, RouteCoordinate(corner.latitude + metersLatitude(5.0), corner.longitude + metersLongitude(4.0)))
        assertEquals(1, field(activity, "currentStepIndex"))
        assertEquals("Now: Turn right", primary(activity))
        assertAction(activity, 90f)
        move(activity, RouteCoordinate(corner.latitude + metersLatitude(12.0), corner.longitude + metersLongitude(4.0)))
        assertTrue(primary(activity).startsWith("Continue straight"))
        assertAction(activity, 0f)
        val nextCorner = steps(activity)[2].points.first()
        move(activity, RouteCoordinate(nextCorner.latitude - metersLatitude(4.0), nextCorner.longitude))
        assertEquals("At the junction: Turn left", primary(activity))
        assertAction(activity, -90f)
        move(activity, nextCorner)
        assertEquals("Now: Turn left", primary(activity))
        assertAction(activity, -90f)
        controller.pause().stop().destroy()
    }

    @Test
    fun sparseFixBeyondTurnGoesForwardAndBackwardJitterCannotReplayIt() {
        val controller = readyActivity()
        val activity = controller.get()
        val corner = steps(activity)[1].points.first()
        // There was no fix at the corner. Do not issue a late right turn.
        move(activity, RouteCoordinate(corner.latitude + metersLatitude(15.0), corner.longitude))
        assertEquals(1, field(activity, "currentStepIndex"))
        assertTrue(primary(activity).startsWith("Continue straight"))
        assertAction(activity, 0f)
        move(activity, RouteCoordinate(corner.latitude + metersLatitude(.5), corner.longitude))
        assertFalse(primary(activity).contains("Now: Turn right"))
        assertAction(activity, 0f)
        controller.pause().stop().destroy()
    }

    @Test
    fun unknownUpcomingManeuverDoesNotReplaceCurrentStraightActionOrInventJunctionArrow() {
        val controller = readyActivity()
        val activity = controller.get()
        val modified = steps(activity).toMutableList()
        modified[1] = modified[1].copy(maneuver = "UNKNOWN")
        set(activity, "routeSteps", modified)
        render(activity)
        assertEquals("Continue straight for 63 m", primary(activity))
        assertEquals("Then in 63 m: Turn right", upcoming(activity))
        assertAction(activity, 0f)
        move(activity, modified[1].points.first())
        assertEquals("Now: Turn right", primary(activity))
        assertHidden(activity)
        controller.pause().stop().destroy()
    }

    @Test
    fun routeMissingLoadingOrFailedCannotDisplayCurrentActionWithFreshCameraAndGps() {
        val controller = readyActivity()
        val activity = controller.get()
        val arrow = field(activity, "cameraArrow") as CameraArrowView
        for (state in listOf(RouteRequestGate.State.WAITING, RouteRequestGate.State.LOADING, RouteRequestGate.State.FAILED)) {
            val gate = RouteRequestGate()
            when (state) {
                RouteRequestGate.State.LOADING -> gate.tryStart(0L)
                RouteRequestGate.State.FAILED -> gate.failed(0L)
                else -> Unit
            }
            set(activity, "routeRequestGate", gate)
            arrow.visibility = View.VISIBLE
            arrow.setManeuver(90f, "Now: Turn right")
            render(activity)
            assertHidden(activity)
            assertFalse(primary(activity).contains("Continue straight"))
        }
        (field(activity, "routeRequestGate") as RouteRequestGate).succeeded()
        render(activity)
        assertAction(activity, 0f)
        set(activity, "routeApiKey", "")
        invoke(activity, "updateArrow")
        assertHidden(activity)
        set(activity, "routeApiKey", "test-key")
        set(activity, "lastRouteFailure", WalkingRouteFailure.NETWORK)
        invoke(activity, "updateArrow")
        assertHidden(activity)
        set(activity, "lastRouteFailure", null)
        set(activity, "currentGuidance", null)
        render(activity)
        assertHidden(activity)
        controller.pause().stop().destroy()
    }

    @Test
    fun wholeRouteWithoutProviderStepsShowsPathTextAndNoInventedStraightOrTurnArrow() {
        val controller = readyActivity()
        val activity = controller.get()
        set(activity, "routeSteps", emptyList<WalkingRouteStep>())
        render(activity)
        assertEquals(activity.getString(R.string.navigation_continue_path), primary(activity))
        assertHidden(activity)
        controller.pause().stop().destroy()
    }

    @Test
    fun routeResetClearsConsumedTurnsForNewRoute() {
        val controller = readyActivity()
        val activity = controller.get()
        val steps = steps(activity)
        val points = field(activity, "pathPoints")
        val corner = steps[1].points.first()
        move(activity, RouteCoordinate(corner.latitude + metersLatitude(10.0), corner.longitude))
        assertAction(activity, 0f)
        invoke(activity, "resetRoute")
        set(activity, "routeSteps", steps)
        set(activity, "pathPoints", points)
        (field(activity, "routeRequestGate") as RouteRequestGate).succeeded()
        move(activity, corner)
        assertEquals("Now: Turn right", primary(activity))
        assertAction(activity, 90f)
        controller.pause().stop().destroy()
    }

    @Test
    fun expiredGpsAndCameraHideCurrentActionAndPermissionRevocationCannotRestoreIt() {
        val controller = readyActivity()
        val activity = controller.get()
        render(activity)
        assertAction(activity, 0f)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        render(activity)
        assertHidden(activity)
        set(activity, "lastCameraObservedAtNanos", SystemClock.elapsedRealtimeNanos())
        render(activity)
        assertAction(activity, 0f)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(16))
        render(activity)
        assertHidden(activity)
        assertEquals(activity.getString(R.string.navigation_waiting_location), primary(activity))
        assertEquals(View.GONE, activity.findViewById<TextView>(R.id.upcomingTurnText).visibility)
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        render(activity)
        assertHidden(activity)
        controller.pause().stop().destroy()
    }

    @Test
    fun missingRouteNeverCompletesNavigationFromOnlyFinalCoordinateProximity() {
        val controller = Robolectric.buildActivity(ARNavigationActivity::class.java, destination()).create()
        val activity = controller.get()
        updateGuidance(activity, RouteCoordinate(5.280285448386224, 115.17640486697412))
        assertEquals(false, field(activity, "hasArrived"))
        controller.destroy()
    }

    private fun readyActivity(): ActivityController<ARNavigationActivity> {
        val app = Shadows.shadowOf(RuntimeEnvironment.getApplication())
        app.denyPermissions(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
        val controller = Robolectric.buildActivity(ARNavigationActivity::class.java, destination()).create().start().resume()
        val activity = controller.get()
        // Grant after resume, so these tests do not start real camera or GPS work.
        app.grantPermissions(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
        Shadows.shadowOf(activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager).setLocationEnabled(true)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(20))
        val start = RouteCoordinate(5.28, 115.17)
        val corner = RouteCoordinate(start.latitude, start.longitude - metersLongitude(63.0))
        val secondCorner = RouteCoordinate(corner.latitude + metersLatitude(50.0), corner.longitude)
        val end = RouteCoordinate(secondCorner.latitude, secondCorner.longitude - metersLongitude(32.0))
        setFix(activity, start)
        set(activity, "lastCameraCaptureTimestamp", 1L)
        set(activity, "lastCameraObservedAtNanos", SystemClock.elapsedRealtimeNanos())
        set(activity, "routeApiKey", "test-key")
        set(activity, "pathPoints", listOf(start, corner, secondCorner, end))
        set(activity, "routeSteps", listOf(
            WalkingRouteStep(listOf(start, corner), "Head west", "DEPART", 63),
            WalkingRouteStep(listOf(corner, secondCorner), "Turn right", "TURN_RIGHT", 50),
            WalkingRouteStep(listOf(secondCorner, end), "Turn left", "TURN_LEFT", 32)))
        set(activity, "currentGuidance", WalkingStepGuidance(0, 1, corner, "Head west", "DEPART", 63.0, true, false, 270f))
        (field(activity, "routeRequestGate") as RouteRequestGate).succeeded()
        return controller
    }

    @Suppress("UNCHECKED_CAST")
    private fun steps(activity: ARNavigationActivity) = field(activity, "routeSteps") as List<WalkingRouteStep>
    private fun primary(activity: ARNavigationActivity) = activity.findViewById<TextView>(R.id.directionText).text.toString()
    private fun upcoming(activity: ARNavigationActivity) = activity.findViewById<TextView>(R.id.upcomingTurnText).text.toString()
    private fun caption(activity: ARNavigationActivity) = activity.findViewById<TextView>(R.id.maneuverPreviewCaption).text.toString()
    private fun assertAction(activity: ARNavigationActivity, angle: Float) {
        val arrow = field(activity, "cameraArrow") as CameraArrowView
        assertEquals(View.VISIBLE, arrow.visibility)
        assertEquals(angle, arrow.javaClass.getDeclaredField("direction").apply { isAccessible = true }.get(arrow))
        assertEquals(primary(activity), arrow.contentDescription)
        assertTrue(caption(activity).contains(primary(activity)))
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.maneuverPreviewContainer).visibility)
    }
    private fun assertHidden(activity: ARNavigationActivity) {
        val arrow = field(activity, "cameraArrow") as CameraArrowView
        assertEquals(View.INVISIBLE, arrow.visibility)
        assertNull(arrow.contentDescription)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.maneuverPreviewContainer).visibility)
    }
    private fun metersLatitude(meters: Double) = Math.toDegrees(meters / 6_371_000.0)
    private fun metersLongitude(meters: Double) = Math.toDegrees(meters / (6_371_000.0 * kotlin.math.cos(Math.toRadians(5.28))))
    private fun field(activity: ARNavigationActivity, name: String) = activity.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(activity)
    private fun set(activity: ARNavigationActivity, name: String, value: Any?) {
        activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    }
    private fun invoke(activity: ARNavigationActivity, method: String) {
        activity.javaClass.getDeclaredMethod(method).apply { isAccessible = true }.invoke(activity)
    }
    private fun render(activity: ARNavigationActivity) {
        invoke(activity, "renderNavigationState")
    }
    private fun setFix(activity: ARNavigationActivity, point: RouteCoordinate) {
        set(activity, "latestLocation", Location("gps").apply {
            latitude = point.latitude
            longitude = point.longitude
            accuracy = 5f
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        })
    }
    private fun updateGuidance(activity: ARNavigationActivity, point: RouteCoordinate) {
        setFix(activity, point)
        activity.javaClass.getDeclaredMethod("updateGuidance", RouteCoordinate::class.java).apply { isAccessible = true }.invoke(activity, point)
    }
    private fun move(activity: ARNavigationActivity, point: RouteCoordinate) {
        updateGuidance(activity, point)
        set(activity, "lastCameraObservedAtNanos", SystemClock.elapsedRealtimeNanos())
        render(activity)
    }
    private fun destination() = Intent().putExtra("restaurant_name", "Cafe UMSKAL")
        .putExtra("restaurant_lat", 5.280285448386224).putExtra("restaurant_lng", 115.17640486697412)
}
