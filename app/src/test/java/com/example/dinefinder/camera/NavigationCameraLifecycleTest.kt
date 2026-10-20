package com.example.dinefinder.camera

import android.Manifest
import android.os.Looper
import android.os.SystemClock
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class NavigationCameraLifecycleTest {
    private lateinit var activity: ActivityController<ComponentActivity>
    private lateinit var camera: NavigationCameraController
    private lateinit var container: FrameLayout
    private val frames = mutableListOf<NavigationCameraFrame>()
    private val errors = mutableListOf<String>()

    @Before
    fun createCameraWithoutOpeningHardware() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.CAMERA)
        activity = Robolectric.buildActivity(ComponentActivity::class.java).create().start().resume()
        container = FrameLayout(activity.get())
        camera = NavigationCameraController(activity.get(), container, frames::add, errors::add)
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
    }

    @After
    fun closeCamera() {
        camera.close()
        if (activity.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) activity.pause()
        activity.stop().destroy()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun busyMainThreadReceivesOnlyLatestPose() {
        val generation = delivery().start()
        // Drive the capture ingress directly so lifecycle tests need no native AR runtime.
        repeat(120) { post(generation, frame(it + 1L)) }
        assertTrue(frames.isEmpty())
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, frames.size)
        assertEquals(120L, frames.single().frame.captureTimestampNanos)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun lifecyclePauseDiscardsBothQueuedAndLateCameraFrames() {
        val generation = delivery().start()
        post(generation, frame(1))
        activity.pause()
        post(generation, frame(2))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(frames.isEmpty())
        assertFalse(delivery().active)
        assertEquals(0, container.childCount)
    }

    @Test
    fun replacedBackendRejectsOldGenerationAndAcceptsNewCaptureClock() {
        val previous = delivery().start()
        post(previous, frame(100))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        camera.stop()
        val current = delivery().start()
        post(previous, frame(101))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, frames.size)
        post(current, frame(1))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(100L, 1L), frames.map { it.frame.captureTimestampNanos })
    }

    @Test
    fun closedCameraCannotRestartOrDeliverQueuedCallbacks() {
        val generation = delivery().start()
        post(generation, frame(1))
        camera.close()
        camera.start()
        post(generation, frame(2))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(frames.isEmpty())
        assertFalse(delivery().active)
        assertEquals(0, container.childCount)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun permissionRevocationStopsDeliveryAndReportsOneError() {
        val generation = delivery().start()
        post(generation, frame(1))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.CAMERA)
        post(generation, frame(2))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        post(generation, frame(3))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, frames.size)
        assertEquals(1, errors.size)
        assertFalse(delivery().active)
    }

    @Test
    fun fallbackInvalidatesPoseImmediatelyWithoutRefreshingCameraImage() {
        val generation = delivery().start()
        val tracked = frame(1).copy(cameraToWorld = doubleArrayOf(
            1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0), position = doubleArrayOf(1.0, 2.0, 3.0))
        post(generation, tracked)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        ShadowSystemClock.advanceBy(Duration.ofMillis(400))
        invalidatePose(generation)
        assertEquals(2, frames.size)
        val invalidated = frames.last()
        assertFalse(invalidated.tracking)
        assertNull(invalidated.cameraToWorld)
        assertNull(invalidated.position)
        assertEquals(NavigationCameraSource.ARCORE, invalidated.source)
        assertEquals(tracked.frame.captureTimestampNanos, invalidated.frame.captureTimestampNanos)
        assertEquals(tracked.frame.observedAtNanos, invalidated.frame.observedAtNanos)
        invalidatePose(generation)
        assertEquals(2, frames.size)
    }

    @Test
    fun normalStopDoesNotSendTrackingInvalidationOrPermitLateOne() {
        val generation = delivery().start()
        post(generation, frame(1))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        camera.stop()
        invalidatePose(generation)
        assertEquals(1, frames.size)
        assertFalse(delivery().active)
    }

    private fun frame(timestamp: Long) = NavigationCameraFrame(
        CameraPreviewFrame(DoubleArray(0), timestamp, SystemClock.elapsedRealtimeNanos()),
        tracking = true, source = NavigationCameraSource.ARCORE)

    private fun delivery(): NavigationFrameDelivery = NavigationCameraController::class.java
        .getDeclaredField("delivery").apply { isAccessible = true }.get(camera) as NavigationFrameDelivery

    private fun post(generation: Long, frame: NavigationCameraFrame) {
        NavigationCameraController::class.java.getDeclaredMethod("postFrame",
            Long::class.javaPrimitiveType, NavigationCameraFrame::class.java)
            .apply { isAccessible = true }.invoke(camera, generation, frame)
    }

    private fun invalidatePose(generation: Long) {
        NavigationCameraController::class.java.getDeclaredMethod("invalidateTrackedPose",
            Long::class.javaPrimitiveType).apply { isAccessible = true }.invoke(camera, generation)
    }
}
