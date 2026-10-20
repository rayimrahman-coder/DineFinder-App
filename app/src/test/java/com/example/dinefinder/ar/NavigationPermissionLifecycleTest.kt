package com.example.dinefinder.ar

import android.Manifest
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import com.example.dinefinder.R
import com.example.dinefinder.camera.CameraArrowView
import com.example.dinefinder.camera.CameraPreviewFrame
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NavigationPermissionLifecycleTest {
    @Test
    fun deniedPermissionsRequestCameraAndPreciseLocationTogetherWithoutStartingNavigation() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(
            Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val controller = Robolectric.buildActivity(ARNavigationActivity::class.java, destination()).create().start().resume()
        val activity = controller.get()
        assertArrayEquals(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION), Shadows.shadowOf(activity).lastRequestedPermission.requestedPermissions)
        assertFalse(field(activity, "isLocationUpdatesRequested") as Boolean)
        assertEquals(View.INVISIBLE, (field(activity, "cameraArrow") as View).visibility)
        assertEquals(activity.getString(R.string.navigation_camera_permission_needed),
            activity.findViewById<TextView>(R.id.directionText).text.toString())
        controller.pause().stop().destroy()
    }

    @Test
    fun approximateLocationDoesNotStartCameraGuidanceOrGpsUpdates() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA, Manifest.permission.ACCESS_COARSE_LOCATION)
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val controller = Robolectric.buildActivity(ARNavigationActivity::class.java, destination()).create().start().resume()
        assertFalse(field(controller.get(), "isLocationUpdatesRequested") as Boolean)
        assertEquals(View.INVISIBLE, (field(controller.get(), "cameraArrow") as View).visibility)
        assertEquals(controller.get().getString(R.string.navigation_permission_needed),
            controller.get().findViewById<TextView>(R.id.directionText).text.toString())
        controller.pause().stop().destroy()
    }

    @Test
    fun pauseClearsArrowAndLateCameraCallbackCannotRestoreIt() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(
            Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val controller = Robolectric.buildActivity(ARNavigationActivity::class.java, destination()).create().start().resume()
        val activity = controller.get()
        val arrow = field(activity, "cameraArrow") as CameraArrowView
        arrow.setManeuver(90f, "Move the camera right to see the walking path")
        arrow.visibility = View.VISIBLE
        controller.pause()
        assertEquals(View.INVISIBLE, arrow.visibility)
        assertNull(arrow.contentDescription)
        activity.javaClass.getDeclaredMethod("onCameraFrame", CameraPreviewFrame::class.java)
            .apply { isAccessible = true }.invoke(activity,
                CameraPreviewFrame(DoubleArray(16), 1L, SystemClock.elapsedRealtimeNanos()))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertEquals(View.INVISIBLE, arrow.visibility)
        assertFalse(field(activity, "isLocationUpdatesRequested") as Boolean)
        controller.stop().destroy()
    }

    @Test
    fun expiredGpsRemovesPriorUpcomingTurnTextWithoutStartingHardware() {
        val app = Shadows.shadowOf(RuntimeEnvironment.getApplication())
        app.denyPermissions(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
        val controller = Robolectric.buildActivity(ARNavigationActivity::class.java, destination()).create().start().resume()
        val activity = controller.get()
        // Grant after resume without invoking the permission callback: hardware stays stopped.
        app.grantPermissions(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
        Shadows.shadowOf(activity.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager).setLocationEnabled(true)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(20))
        val start = RouteCoordinate(5.28, 115.17)
        val corner = RouteCoordinate(5.2805, 115.17)
        val end = RouteCoordinate(5.2805, 115.1705)
        setField(activity, "pathPoints", listOf(start, corner, end))
        setField(activity, "routeSteps", listOf(
            WalkingRouteStep(listOf(start, corner), "Head north", "DEPART", 55),
            WalkingRouteStep(listOf(corner, end), "Turn right", "TURN_RIGHT", 55)))
        (field(activity, "routeRequestGate") as RouteRequestGate).succeeded()
        setField(activity, "currentGuidance", WalkingStepGuidance(0, 1, corner, "Head north", "DEPART", 55.0, true, false))
        setField(activity, "latestLocation", Location("gps").apply {
            latitude = start.latitude
            longitude = start.longitude
            accuracy = 5f
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        })
        render(activity)
        assertEquals(View.VISIBLE, activity.findViewById<TextView>(R.id.upcomingTurnText).visibility)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(16))
        render(activity)
        assertEquals(View.GONE, activity.findViewById<TextView>(R.id.upcomingTurnText).visibility)
        assertEquals("", activity.findViewById<TextView>(R.id.upcomingTurnText).text.toString())
        assertFalse(field(activity, "isLocationUpdatesRequested") as Boolean)
        controller.pause().stop().destroy()
    }

    private fun field(activity: ARNavigationActivity, name: String) = activity.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(activity)
    private fun setField(activity: ARNavigationActivity, name: String, value: Any) {
        activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    }
    private fun render(activity: ARNavigationActivity) {
        activity.javaClass.getDeclaredMethod("renderNavigationState").apply { isAccessible = true }.invoke(activity)
    }
    private fun destination() = Intent().putExtra("restaurant_name", "Cafe UMSKAL")
        .putExtra("restaurant_lat", 5.280285448386224).putExtra("restaurant_lng", 115.17640486697412)
}
